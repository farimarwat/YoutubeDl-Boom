# YoutubeDL-Boom

An Android library that runs [yt-dlp](https://github.com/yt-dlp/yt-dlp) on the device, with Python,
ffmpeg and aria2c bundled. Get video info and download from the sites yt-dlp supports, with
progress, cancel and yt-dlp self-update, from Kotlin coroutines.

Built on the work of **JunkFood**'s [youtubedl-android](https://github.com/yausername/youtubedl-android)
(see [Credits](#credits)).

- Android 7.0+ (minSdk 24), **arm64-v8a and armeabi-v7a** only (no x86/x86_64).
- Kotlin API; not designed for Java callers.

## Full or lite?

| | `youtubedl-boom` | `youtubedl-boom-lite` |
|---|---|---|
| ffmpeg in the APK (per ABI) | ~35.6 MB | ~6.6 MB |
| ffmpeg on the device | ~150 MB | ~14 MB |
| Merge video + audio, remux, fixups | ✅ | ✅ |
| HLS / https via ffmpeg (`--downloader ffmpeg`, `--download-sections`) | ✅ | ✅ |
| Audio extraction `-x` (mp3, m4a/aac, opus, vorbis, flac, alac, wav) | ✅ | ✅ |
| Embed thumbnails and subtitles | ✅ | ✅ |
| Re-encode video (`--recode-video`) | ✅ x264, x265, vp9, av1… | mp4 only, through ffmpeg's built-in mpeg4 encoder (lower quality) |

Same API, same package names. Most apps only download, merge and extract audio: use **lite**.

## Installation

```kotlin
dependencies {
    implementation("io.github.farimarwat:youtubedl-boom:1.0.24")        // or youtubedl-boom-lite:1.0.24
    implementation("io.github.farimarwat:youtubedl-boom-commons:1.3")
}
```

The bundled programs are run from the app's native library folder, so they must be extracted at
install time:

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // The Python/aria2c bundles are zip files named .so: nothing to strip.
            keepDebugSymbols += "**/*.zip.so"
        }
    }
}
```

(Older setups use `android:extractNativeLibs="true"` on `<application>` in the manifest; same effect.)

**ProGuard / R8:** since 1.0.24 / helper 1.7 / commons 1.3 the rules ship inside the libraries.
Nothing to add. (Older versions needed `-keep class com.farimarwat.** { *; }` and
`-keep class org.apache.commons.compress.archivers.zip.** { *; }`.)

### Library in a Dynamic Feature Module

To keep the ~50–100 MB out of the base APK, put the library in a DFM and call it from the base
module through the helper:

```kotlin
// dynamic feature module
implementation("io.github.farimarwat:youtubedl-boom:1.0.24")         // or -lite
implementation("io.github.farimarwat:youtubedl-boom-commons:1.3")

// app (base) module
implementation("io.github.farimarwat:youtubedl-boom-helper:1.7")
implementation("io.github.farimarwat:youtubedl-boom-commons:1.3")
```

`RYoutubeDL` has the same calls as `YoutubeDL`, see [Using the helper](#using-the-helper-dfm).

## Usage

### Initialize

Call once, e.g. in your `Application`. The first start unpacks Python and prepares yt-dlp, which
takes a few seconds; later starts are fast.

```kotlin
YoutubeDL.init(
    appContext = applicationContext,
    withFfmpeg = true,   // needed to merge video + audio, extract audio, etc. Recommended.
    withAria2c = false,
    onSuccess = { youtubeDL -> /* ready, on the main thread */ },
    onError = { error -> /* failed */ }
)
```

### Get video info

```kotlin
YoutubeDL.getInfo(
    url = "https://example.com/some/video",
    onSuccess = { info -> println("${info.title} · ${info.duration}s · ${info.formats?.size} formats") },
    onError = { error -> println(error.message) }
)

// With yt-dlp options:
YoutubeDL.getInfo(
    request = YoutubeDLRequest(url).addOption("--no-playlist"),
    onSuccess = { info -> },
    onError = { error -> }
)
```

`onSuccess` runs on the main thread, `onError` on a background thread.

### Download

yt-dlp and ffmpeg write files themselves, by path. On Android 10+ write into a folder your app
owns (e.g. `getExternalFilesDir(...)`) and move the result to shared storage (MediaStore)
afterwards; the [sample app](app/src/main/java/com/example/youtubedl_boom/Storage.kt) shows how.

```kotlin
val request = YoutubeDLRequest(url)
    .addOption("-o", File(outputDir, "%(title).80B.%(ext)s").absolutePath)
    .addOption("--merge-output-format", "mp4/mkv")
// audio only:  .addOption("-x").addOption("--audio-format", "mp3")

val job = YoutubeDL.download(
    request = request,
    pId = null,  // optional id; generated if null
    progressCallBack = { percent, etaSeconds, line ->
        // percent and etaSeconds are -1 until known; line is yt-dlp's raw output line
    },
    onStartProcess = { processId -> /* keep it to cancel */ },
    onEndProcess = { response -> /* response.exitCode, response.out, response.elapsedTime */ },
    onError = { error -> /* YoutubeDLException, or YoutubeDL.CanceledException after a cancel */ }
)
```

`progressCallBack` is called on a background thread, often several times a second.

### Cancel

```kotlin
YoutubeDL.destroyProcessById(processId)   // stops yt-dlp and its ffmpeg/aria2c child
job.cancel()                              // since 1.0.24 this stops the processes too
```

### Update yt-dlp

Sites change often; update yt-dlp from time to time (e.g. once a day).

```kotlin
scope.launch {
    YoutubeDL.updateYoutubeDL(
        appContext = applicationContext,
        updateChannel = UpdateChannel.STABLE,   // or NIGHTLY, MASTER
        onSuccess = { status -> /* UpdateStatus.DONE or ALREADY_UP_TO_DATE */ },
        onError = { error -> }
    )
}
val version = YoutubeDL.version(applicationContext)        // e.g. "2026.10.26"
val name = YoutubeDL.versionName(applicationContext)
```

### Using the helper (DFM)

Same calls on `RYoutubeDL`; results that are library types arrive as `Any`.

```kotlin
RYoutubeDL.init(appContext, withFfmpeg = true, onSuccess = { }, onError = { })
RYoutubeDL.getInfo(url, onSuccess = { info -> }, onError = { })
RYoutubeDL.getInfo(YoutubeDLRequest(url).addOption("--no-playlist"), onSuccess = { }, onError = { })
RYoutubeDL.download(request, progressCallBack = { p, eta, line -> }, onStartProcess = { id -> },
    onEndProcess = { response -> }, onError = { })
RYoutubeDL.destroyProcessById(processId)
RYoutubeDL.updateYoutubeDL(appContext, UpdateChannel.STABLE, onSuccess = { }, onError = { })
val version = RYoutubeDL.version(appContext)   // suspend
```

Make sure the feature module is installed before calling, and check that `init` succeeded.

## Notes

- **YouTube:** current yt-dlp needs a JavaScript runtime (such as Deno) for full YouTube support.
  It is not bundled (size), so YouTube may offer fewer formats or fail. Other sites are not affected.
- **Sample app:** `app/` shows init, scan, video/mp3 download, progress, cancel, saving to
  Downloads and permissions. `./gradlew :app:installDebug` (full) or `-Plite`.
- **Lite ffmpeg** is built by [`scripts/ffmpeg-lite/build.sh`](scripts/ffmpeg-lite/build.sh)
  (ffmpeg 7.1.3 with lame, opus, vorbis, mbedtls; sources pinned by checksum).

## What's new

**youtubedl-boom 1.0.24 · youtubedl-boom-lite 1.0.24 (new) · helper 1.7 · commons 1.3**

- New `youtubedl-boom-lite`: same library with a small ffmpeg (see the table above).
- helper: no longer uses kotlin-reflect; fixes `Built-in class kotlin.Any is not found` in
  R8 / Dynamic Feature apps. New `RYoutubeDL.getInfo(request)` to pass yt-dlp options.
- Bundled yt-dlp updated to 2026.10.26, and an older copy on the device is replaced at start.
- yt-dlp is precompiled once, so `getInfo`/`download` start noticeably faster.
- Cancelling a download (`job.cancel()` or `destroyProcessById`) reliably stops yt-dlp and ffmpeg;
  `--downloader ffmpeg` no longer hangs when ffmpeg never starts.
- `init` is thread-safe; update check has network timeouts; less CPU while reading output.
- ProGuard/R8 rules ship with the libraries.
- Fewer dependencies: appcompat, material, kotlin-reflect, commons-io and more removed.

**1.0.23** · minor bug fixes.

## Credits

Hats off to **JunkFood** for [youtubedl-android](https://github.com/yausername/youtubedl-android).
YoutubeDL-Boom is built on that foundation. If this project helps you, please support
[his work](https://github.com/yausername).

What YoutubeDL-Boom changes:

- **Cancelling really stops ffmpeg:** the ffmpeg child process is killed with yt-dlp, so it no
  longer keeps downloading in the background.
- **Progress for ffmpeg downloads:** output of the ffmpeg downloader is passed to the progress
  callback.
- **Coroutines instead of threads.**
- **One module, one dependency** instead of several.
- **Helper API** for Dynamic Feature Modules.
- **Lite variant** with a small ffmpeg.
