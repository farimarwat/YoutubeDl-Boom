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

## Status (2026-11-06)

Released (uploaded to Maven Central, published by the user): youtubedl-boom 1.0.24,
youtubedl-boom-lite 1.0.24 (new), youtubedl-boom-helper 1.7, youtubedl-boom-commons 1.3.
Commit 989a9a6 on main, pushed. No git tag yet.

### Done in this release

Crashes / hangs
- helper: plain Java reflection instead of kotlin-reflect (`Class.forName(...).getField("INSTANCE")`,
  `getMethod` with full parameter lists; suspend `updateYoutubeDL` gets a trailing Continuation via
  suspendCoroutineUninterceptedOrReturn). Fixes `Built-in class kotlin.Any is not found` (R8/DFM).
  Every call checked by a reflection test. New `RYoutubeDL.getInfo(request)`.
- `performInit` @Synchronized, `initialized` @Volatile (parallel init corrupted python).
- Download Job cancel kills yt-dlp + ffmpeg child and clears downloadIdProcessMap (finally block;
  conditional remove(id, process)). Before: processes kept running.
- FfmpegStreamExtractor stops when python exits (looped forever if ffmpeg never started, so
  download() never returned); closes streams; delay instead of sleep.
- getChildProcessId reaps its `sh` (zombie + fd leak each call); killProcess ignores -1.
- consumer-rules.pro shipped in library/helper/commons (were empty).
- YoutubeDLUpdater: connect/read timeouts on the GitHub API and the download.

Performance / battery / memory
- yt-dlp precompiled once (init + after update) into yt-dlp-<key>.pyz (zip of sourceless .pyc,
  built by the bundled python, YoutubeDL.prepareYtdlp; falls back to the zipapp). Mac: 0.78 s ->
  0.47 s per call. Bundled yt-dlp 2024.09.27 -> 2026.10.26; older installed copy replaced at init
  (BUNDLED_YTDLP_VERSION; update together with res/raw/ytdlp).
- StreamGobbler reads 8 KB blocks (was char by char); progress regex once per line, skipped
  without a callback; ProcessUtils regex compiled once.
- objectMapper lazy (same public getter).

Size
- youtubedl-boom-lite: module `library-lite`, no sources of its own (compiles ../library's
  java/res; jniLibs = library's minus full ffmpeg + library-lite/src/main/ffmpegLibs/<abi>/
  libffmpeg.so, one static executable from scripts/ffmpeg-lite/build.sh: ffmpeg 7.1.3 + lame,
  opus, vorbis, mbedtls, no external video encoders). ffmpeg ~14 MB on device / ~6.6 MB in APK
  per ABI vs ~150 / 35.6 MB. No ffprobe: yt-dlp looks for `ffprobe` next to --ffmpeg-location and
  never found libffprobe.so. Verified on a Pixel 6a (arm64 + armv7): https/HLS, --downloader
  ffmpeg, --download-sections, -x mp3/m4a/opus/vorbis/flac/wav, merge, thumbnails, mov_text subs,
  setts fixup, recode->mpeg4. FFmpeg.init deletes a full build's packages/ffmpeg when no zip.
- Removed deps: appcompat, material, core-ktx, commons-io (library); kotlin-reflect,
  jackson-databind, commons-io/compress, timber, appcompat, material (helper, commons).
  Safe: they were `implementation` deps.

Other
- Credentials moved to ~/.gradle/gradle.properties (old token was public in commit 8623edf;
  user revoked it). All four modules publish with vanniktech 0.33.0.
- Sample app: new UI (engine state, full/lite, scan card, video/mp3, progress, cancel, saved +
  Open), ViewModel, all calls via RYoutubeDL, downloads to app folder then MediaStore (10+) or
  file copy with WRITE_EXTERNAL_STORAGE maxSdk 28. `./gradlew :app:installDebug [-Plite]`.
- README rewritten (full vs lite, install, usage, notes, what's new, credits).

### Open (not done; most change behaviour, so decide first)

1. Memory: whole yt-dlp stdout kept in a StringBuffer and returned in YoutubeDLResponse.out
   (MBs for long downloads). Cap it, ideally opt-in.
2. Battery: progress callback fires several times a second; `--progress-delta 0.5` default
   would cut it (changes update rate for callers).
3. With --downloader ffmpeg, still polls `ps` once a second until ffmpeg starts.
4. Python stdlib trim (~2 MB unpacked, <1 MB in APK): low value.
5. getInfo not cancellable (returns Unit): additive API later.
6. getInfo/download mutate the caller's YoutubeDLRequest (options added twice on reuse);
   onError runs on a background thread, onSuccess on Main. Documented, not changed.
7. Not measured on device: memory/battery (Android Studio Profiler, 1.0.23 vs 1.0.24).

## Release steps

Change here -> build and run the sample app (`:app`) on the phone -> user tests scan, download,
mp3, cancel -> user publishes. All four modules use vanniktech; credentials and signing are in
~/.gradle/gradle.properties (never in the repo). Order (POM dependencies): commons -> helper ->
library -> library-lite. Bump versions in each module's mavenPublishing { coordinates } and in
README. Check with `publishToMavenLocal` (signs too), then
`./gradlew :commons:publishToMavenCentral :helper:publishToMavenCentral
:library:publishToMavenCentral :library-lite:publishToMavenCentral` (upload only; the user
presses Publish on central.sonatype.com), or `publishAndReleaseToMavenCentral` to release directly.
Gradle must run on JDK <= 23 (local .gradle/config.properties points at jbr-21).

## Deferred (decided, not now)

- aria2c stays (others may use withAria2c = true).
- GitHub API rate limit (60/h per IP) in YoutubeDLUpdater: later, additive only.
- yt-dlp wants a JS runtime (deno) for full YouTube support; not bundled (size).
