# YoutubeDl-Boom

Public Android library (Maven Central, io.github.farimarwat, ~17 GitHub stars) wrapping yt-dlp
with bundled Python, ffmpeg and aria2c. Modules: `library` (youtubedl-boom), `commons`
(shared types), `helper` (RYoutubeDL: calls the library by reflection when it lives in a
Dynamic Feature Module), `app` (sample).

## Rules

- It is a library, not one app's code. Zed Browser (~/StudioProjects/RedBrowser) is one user;
  never shape the library around Zed only.
- Existing users must not break: keep the public API (names, signatures, behaviour). Additions
  are fine; removals and changes need a new major version and are avoided.
- Keep it simple. No new modules or abstractions unless they remove a real problem.
- Work and testing stay in this repo: verify with the sample app (`:app`), never edit Zed or
  frame the work around it. The user tests on the phone and says when to commit and publish.
  Discuss each step briefly before building; wait for "go".
- Commit messages: plain, no Co-Authored-By or "generated with" lines.

## Current work (agreed 2026-11-06): round 1, fixes only, no API change

1. helper 1.6 -> 1.7
   - Crash in Zed (Crashlytics, non-fatal): `AssertionError: Built-in class kotlin.Any is not
     found` at `RYoutubeDL$init` (`memberFunctions`, RYoutubeDL.kt:48). kotlin-reflect needs
     the `kotlin/*.kotlin_builtins` resources, which are missing/unreachable in DFM + R8
     apps, so the engine never starts. Fix: plain Java reflection
     (`Class.forName(...).getField("INSTANCE")`, `getMethod` with full parameter lists, since
     Kotlin default params don't exist for Java; the suspend `updateYoutubeDL` takes a
     trailing Continuation). Same public API. Drop kotlin-reflect.
   - Add `getInfo(request: YoutubeDLRequest, ...)` (library already has it) so callers can pass
     options. Zed needs it to scan with `--use-extractors default,-youtube.*`.
   - Trim deps to what it uses: commons, coroutines (timber only if kept).
2. commons 1.2 -> 1.3: keep only jackson-annotations (used by VideoInfo/VideoFormat/...).
   Remove appcompat, material, jackson-databind, commons-io, commons-compress, kotlin-reflect.
3. library 1.0.23 -> 1.0.24: remove appcompat and material (unused). `init`/`performInit`
   thread-safe (not synchronized today). StreamGobbler reads char by char unbuffered: read in
   blocks. Nothing else.

Added to round 1 after the 2026-11-06 analysis (all internal, no API change):
- Cancelling a download Job must kill python/ffmpeg and clear downloadIdProcessMap (only
  InterruptedException is handled today; coroutine cancel throws CancellationException).
- FfmpegStreamExtractor: loops forever if ffmpeg never starts (download never ends), spawns
  `sh -c ps` every second, leaks streams.
- Ship consumer-rules.pro (all empty today): keep com.farimarwat.** so R8 apps work.
- Timeout on the GitHub API call in YoutubeDLUpdater (no timeout today).
- objectMapper lazy (same public getter). Replace commons-io with Kotlin stdlib.

Round 2 (done, untested on phone by user): yt-dlp is precompiled once (at init and after each
update) into yt-dlp-<key>.pyz, a zip of sourceless .pyc built by the bundled python
(YoutubeDL.prepareYtdlp); falls back to the plain zipapp. Bundled yt-dlp 2024.09.27 -> 2026.10.26;
an installed copy older than BUNDLED_YTDLP_VERSION is replaced at init. Update res/raw/ytdlp and
BUNDLED_YTDLP_VERSION together. Not done: trimming the Python stdlib (~2 MB unpacked).

Lite ffmpeg (done): module `library-lite`, artifact youtubedl-boom-lite (same version as the
library). No sources of its own: compiles ../library's java/res, takes its jniLibs minus the full
ffmpeg, adds library-lite/src/main/ffmpegLibs/<abi>/libffmpeg.so: one static executable from
scripts/ffmpeg-lite/build.sh (ffmpeg 7.1.3 + lame, opus, vorbis, mbedtls; no external video
encoders; ~14 MB unpacked, ~6.5 MB in the APK per ABI vs 78 / 35.6 MB). No ffprobe: yt-dlp looks
for `ffprobe` next to --ffmpeg-location and never found libffprobe.so, so it always used ffmpeg.
Verified on a Pixel 6a (arm64 + armv7): https/HLS, --downloader ffmpeg, --download-sections,
-x mp3/m4a/opus/vorbis/flac/wav, merge, thumbnails, mov_text subs, setts fixup, recode->mpeg4.
FFmpeg.init deletes a full build's unpacked packages/ffmpeg when no zip is present (switch to
lite). Sample app on lite: `./gradlew :app:installDebug -Plite`.

Why removing deps is safe: they are `implementation` deps, so consumers only get them at
runtime and could never compile against them.

Steps: change here -> build and run the sample app (`:app`) on the phone -> user tests scan,
download, mp3, cancel -> user publishes. All four modules use the vanniktech plugin (0.33.0);
credentials and signing are in ~/.gradle/gradle.properties (never in the repo). Release order,
because of POM dependencies: commons -> helper -> library -> library-lite:
`./gradlew :commons:publishAndReleaseToMavenCentral :helper:publishAndReleaseToMavenCentral
:library:publishAndReleaseToMavenCentral :library-lite:publishAndReleaseToMavenCentral`
(`publishToMavenCentral` instead = upload only, release by hand on central.sonatype.com).
Check first with `publishToMavenLocal` (signs too).

## Deferred (decided, not now)

- aria2c stays (others may use withAria2c = true).
- Making getInfo cancellable (returns Unit today), GitHub API rate limit (60/h per IP) in
  YoutubeDLUpdater: later, additive only.
- yt-dlp wants a JS runtime (deno) for full YouTube support; not bundled (size).
