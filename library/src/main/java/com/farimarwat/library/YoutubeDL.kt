package com.farimarwat.library

import android.content.Context
import android.os.Build
import com.farimarwat.aria2c.Aria2c
import com.farimarwat.common.SharedPrefsHelper
import com.farimarwat.common.SharedPrefsHelper.update
import com.farimarwat.common.utils.ZipUtils
import com.farimarwat.common.utils.ZipUtils.unzip
import com.farimarwat.commons.UpdateChannel
import com.farimarwat.commons.UpdateStatus
import com.farimarwat.commons.VideoInfo
import com.farimarwat.commons.YoutubeDLRequest
import com.farimarwat.commons.YoutubeDLResponse
import com.farimarwat.ffmpeg.FFmpeg
import com.fasterxml.jackson.databind.ObjectMapper
import com.farimarwat.ffmpeg.FfmpegStreamExtractor
import com.yausername.youtubedl_android.getChildProcessId
import com.yausername.youtubedl_android.getProcessId
import com.yausername.youtubedl_android.killProcess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.UUID
import kotlin.collections.set

object YoutubeDL {
    @Volatile
    private var initialized = false
    private var pythonPath: File? = null
    private var ffmpegPath: File? = null
    // The precompiled yt-dlp (see prepareYtdlp), or the plain zipapp when compiling failed.
    @Volatile
    private var ytdlpPath: File? = null
    private var ytdlpDir: File? = null
    private var pythonLibSize: String = ""
    private var binDir: File? = null
    private var ENV_LD_LIBRARY_PATH: String? = null
    private var ENV_SSL_CERT_FILE: String? = null
    private var ENV_PYTHONHOME: String? = null
    private var TMPDIR: String = ""
    private val downloadIdProcessMap = Collections.synchronizedMap(HashMap<String, Process>())

    /**
     * Initializes the YoutubeDL library asynchronously.
     *
     * This function performs the initialization of the YoutubeDL library, optionally including
     * FFmpeg and Aria2c. It executes in a background coroutine and invokes the appropriate callback
     * on success or failure.
     *
     * - If `withFfmpeg` is `true`, FFmpeg is initialized.
     * - If `withAria2c` is `true`, Aria2c is initialized.
     * - If an error occurs during initialization, the `onError` callback is triggered.
     * - Upon successful initialization, the `onSuccess` callback is called on the main thread.
     *
     * @param appContext The application context required for initialization.
     * @param withFfmpeg Boolean flag indicating whether to initialize FFmpeg. Defaults to `false`.
     * @param withAria2c Boolean flag indicating whether to initialize Aria2c. Defaults to `false`.
     * @param onSuccess Callback invoked on successful initialization, providing the initialized [YoutubeDL] instance.
     * @param onError Callback invoked if an error occurs during initialization, receiving the encountered [Throwable].
     * @return A [Job] representing the coroutine handling the initialization process.
     */
    fun init(
        appContext: Context,
        withFfmpeg:Boolean = false,
        withAria2c:Boolean = false,
        onSuccess:suspend (YoutubeDL) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        val exception = CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job + exception)
        return scope.launch {
            performInit(appContext)
            if (withFfmpeg) {
                FFmpeg.init(appContext)
            }
            if (withAria2c) {
                Aria2c.init(appContext)
            }
            withContext(Dispatchers.Main) {
                onSuccess(this@YoutubeDL)
            }
        }
    }


    // Synchronized: two init() calls at once used to unzip python into the same folder together.
    @Synchronized
    private fun performInit(appContext: Context) {
        if (initialized) return
        val baseDir = File(appContext.noBackupFilesDir, baseName)
        if (!baseDir.exists()) baseDir.mkdir()
        val packagesDir = File(baseDir, packagesRoot)
        binDir = File(appContext.applicationInfo.nativeLibraryDir)
        pythonPath = File(binDir, pythonBinName)
        ffmpegPath = File(binDir, ffmpegBinName)
        val pythonDir = File(packagesDir, pythonDirName)
        val ffmpegDir = File(packagesDir, ffmpegDirName)
        val aria2cDir = File(packagesDir, aria2cDirName)
        val ytdlpDir = File(baseDir, ytdlpDirName)
        this.ytdlpDir = ytdlpDir
        ytdlpPath = File(ytdlpDir, ytdlpBin)
        ENV_LD_LIBRARY_PATH = pythonDir.absolutePath + "/usr/lib" + ":" +
                ffmpegDir.absolutePath + "/usr/lib" + ":" +
                aria2cDir.absolutePath + "/usr/lib"
        ENV_SSL_CERT_FILE = pythonDir.absolutePath + "/usr/etc/tls/cert.pem"
        ENV_PYTHONHOME = pythonDir.absolutePath + "/usr"
        TMPDIR = appContext.cacheDir.absolutePath
        initPython(appContext, pythonDir)
        init_ytdlp(appContext,ytdlpDir)
        prepareYtdlp()
        initialized = true
    }

    @Throws(YoutubeDLException::class)
    internal fun init_ytdlp(appContext: Context,ytdlpDir: File) {
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        val ytdlpBinary = File(ytdlpDir, ytdlpBin)
        // Also replace a copy older than the bundled one (yt-dlp tags are dates, so they sort as
        // text). No recorded version means an old library's bundled copy, never updated.
        val installed = YoutubeDLUpdater.version(appContext)
        if (!ytdlpBinary.exists() || installed == null || installed < BUNDLED_YTDLP_VERSION) {
            try {
                appContext.resources.openRawResource(R.raw.ytdlp).use { input ->
                    ytdlpBinary.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
            } catch (e: Exception) {
                ZipUtils.deleteQuietly(ytdlpDir)
                throw YoutubeDLException("failed to initialize", e)
            }
            YoutubeDLUpdater.saveVersion(appContext, BUNDLED_YTDLP_VERSION, "yt-dlp $BUNDLED_YTDLP_VERSION")
        }
    }

    /**
     * Python never caches bytecode for code run from a zip, so the yt-dlp zipapp was compiled from
     * source on every getInfo/download. Compile it once into a zip of .pyc files (with the bundled
     * python, so the bytecode always matches it) and run that. Falls back to the plain zipapp.
     * Called at init and after every update.
     */
    internal fun prepareYtdlp() {
        val dir = ytdlpDir ?: return
        val source = File(dir, ytdlpBin)
        val key = "${source.length()}-${source.lastModified()}-$pythonLibSize".hashCode().toUInt()
        val compiled = File(dir, "$ytdlpBin-$key.pyz")
        dir.listFiles()?.forEach {
            if (it.name.startsWith("$ytdlpBin-") && it.name.endsWith(".pyz") && it != compiled) it.delete()
        }
        if (!compiled.exists() && source.exists()) {
            val tmp = File(dir, compiled.name + ".tmp")
            try {
                val process = pythonProcess(listOf(pythonPath!!.absolutePath, "-c", COMPILE_YTDLP, source.absolutePath, tmp.absolutePath))
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.waitFor() == 0 && tmp.renameTo(compiled)) {
                    Timber.i("yt-dlp precompiled: ${compiled.name}")
                } else {
                    Timber.i("yt-dlp precompile failed: $output")
                }
            } catch (e: Exception) {
                Timber.i(e)
            } finally {
                tmp.delete()
            }
        }
        ytdlpPath = if (compiled.exists()) compiled else source
    }

    /** A ProcessBuilder for the bundled python with the environment it needs. */
    private fun pythonProcess(command: List<String?>): ProcessBuilder =
        ProcessBuilder(command).apply {
            environment().apply {
                this["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
                this["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
                this["PATH"] = System.getenv("PATH") + ":" + binDir!!.absolutePath
                this["PYTHONHOME"] = ENV_PYTHONHOME
                this["HOME"] = ENV_PYTHONHOME
                this["TMPDIR"] = TMPDIR
            }
        }

    @Throws(YoutubeDLException::class)
    internal fun initPython(appContext: Context, pythonDir: File) {
        val pythonLib = File(appContext.applicationInfo.nativeLibraryDir, pythonLibName)
        val pythonSize = pythonLib.length().toString()
        pythonLibSize = pythonSize
        if (!pythonDir.exists() || shouldUpdatePython(appContext, pythonSize)) {
            ZipUtils.deleteQuietly(pythonDir)
            pythonDir.mkdirs()
            try {
                unzip(pythonLib, pythonDir)
            } catch (e: Exception) {
                ZipUtils.deleteQuietly(pythonDir)
                throw YoutubeDLException("failed to initialize", e)
            }
            updatePython(appContext, pythonSize)
        }
    }

    private fun shouldUpdatePython(appContext: Context, version: String): Boolean {
        return version != SharedPrefsHelper[appContext, pythonLibVersion]
    }

    private fun updatePython(appContext: Context, version: String) {
        update(appContext, pythonLibVersion, version)
    }

    private fun assertInit() {
        check(initialized) { "instance not initialized" }
    }

    private fun ignoreErrors(request: YoutubeDLRequest, out: String): Boolean {
        return request.hasOption("--dump-json") && !out.isEmpty() && request.hasOption("--ignore-errors")
    }

    /**
     * Fetches video information from the provided URL using YoutubeDL.
     *
     * This function creates a [YoutubeDLRequest] for the given URL and retrieves metadata such as
     * video title, duration, and available formats. It executes asynchronously and invokes the
     * appropriate callback upon completion.
     *
     * - If the request succeeds, the `onSuccess` callback is triggered with a [VideoInfo] instance.
     * - If an error occurs, the `onError` callback is triggered with the encountered [Throwable].
     *
     * @param url The URL of the video to retrieve information for.
     * @param onSuccess Callback invoked upon successful retrieval of video information, providing a [VideoInfo] instance.
     * @param onError Callback invoked if an error occurs during the process, receiving the encountered [Throwable].
     */
    fun getInfo(
        url: String,
        onSuccess: (VideoInfo) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val request = YoutubeDLRequest(url)
        getInfo(
            request = request,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Retrieves video information from a given [YoutubeDLRequest].
     *
     * This function executes the YoutubeDL command-line tool in a background coroutine to fetch
     * metadata about a video, such as its title, duration, and available formats. The result is
     * parsed into a [VideoInfo] object.
     *
     * - The function runs the process using Python and Youtube-DLP.
     * - It captures both standard output and error streams.
     * - If successful, the parsed [VideoInfo] is returned via the `onSuccess` callback.
     * - If an error occurs at any stage, the `onError` callback is triggered with the encountered [Throwable].
     *
     * @param request The [YoutubeDLRequest] containing the video URL and options.
     * @param onSuccess Callback invoked with the retrieved [VideoInfo] upon successful execution.
     * @param onError Callback invoked if an error occurs during the process, providing the encountered [Throwable].
     */
    fun getInfo(
        request: YoutubeDLRequest,
        onSuccess: (VideoInfo) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        var streamProcessExtractor: StreamProcessExtractor?
        var streamGobbler: StreamGobbler?
        val exception = CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
        CoroutineScope(Dispatchers.IO + exception).launch {
            request.addOption("--dump-json")
            try {
                assertInit()
                val outBuffer = StringBuffer() // stdout
                val errBuffer = StringBuffer() // stderr
                val args = request.buildCommand()
                val command: MutableList<String?> = ArrayList()
                command.addAll(listOf(pythonPath!!.absolutePath, ytdlpPath!!.absolutePath))
                command.addAll(args)
                val processBuilder = pythonProcess(command)

                val process: Process = processBuilder.start()
                streamProcessExtractor = StreamProcessExtractor()
                val stdOutProcessor =
                    streamProcessExtractor?.readStream(outBuffer, process.inputStream, null)

                streamGobbler = StreamGobbler()
                val stdErrProcessor = streamGobbler?.readStream(errBuffer, process.errorStream)

                try {
                    stdOutProcessor?.join()
                    stdErrProcessor?.join()
                    process.waitFor()
                } catch (e: InterruptedException) {
                    process.destroy()
                    throw e
                }

                val out = outBuffer.toString()
                val err = errBuffer.toString()

                if (out.isBlank()) {
                    throw YoutubeDLException(
                        err.ifBlank { "yt-dlp returned no output for the given URL" }
                    )
                }

                val videoInfo = try {
                    objectMapper.readValue(out, VideoInfo::class.java)
                        ?: throw YoutubeDLException("Failed to parse video information: JSON output is null")
                } catch (e: IOException) {
                    throw YoutubeDLException("Unable to parse video information: $err", e)
                }
                withContext(Dispatchers.Main) {
                    onSuccess(videoInfo)
                }
            } catch (e: Exception) {
                throw e
            } finally {
                streamGobbler = null
                streamProcessExtractor = null
            }
        }
    }

    class CanceledException : Exception()

    /**
     * Downloads media using youtube-dl with the provided request.
     *
     * @param request The YoutubeDLRequest containing the URL and options for the download.
     * @param pId Optional process ID to track the download; if null, a new UUID is generated.
     * @param progressCallBack Optional callback function that provides progress updates:
     *        - [Float]: Download progress percentage.
     *        - [Long]: Downloaded bytes.
     *        - [String]: Status message.
     * @param onStartProcess Callback invoked when the download process starts, receiving the process ID.
     * @param onEndProcess Callback invoked when the download completes successfully, returning a [YoutubeDLResponse].
     * @param onError Callback invoked if an error occurs, receiving the thrown [Throwable].
     * @return A [Job] representing the coroutine handling the download process.
     *
     * The function ensures initialization and prevents duplicate process IDs.
     * It modifies the request options to optimize downloads and uses a [ProcessBuilder] to execute youtube-dl.
     * The download process output is captured, and errors are handled gracefully.
     */

    fun download(
        request: YoutubeDLRequest,
        pId: String? = null,
        progressCallBack: ((Float, Long, String) -> Unit)? = null,
        onStartProcess: (String) -> Unit = {},
        onEndProcess: (YoutubeDLResponse) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        var ffmpegStreamExtractor: FfmpegStreamExtractor?
        var streamProcessExtractor: StreamProcessExtractor?
        var streamGobbler: StreamGobbler?
        val exception = CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
        return CoroutineScope(Dispatchers.IO + exception).launch {
            try {
                val processId = if (pId.isNullOrEmpty()) UUID.randomUUID().toString() else pId
                assertInit()
                if (downloadIdProcessMap.containsKey(processId)) {
                    throw YoutubeDLException("Process ID already exists")
                }

                if (!request.hasOption("--cache-dir") || request.getOption("--cache-dir") == null) {
                    request.addOption("--no-cache-dir")
                }
                if (request.buildCommand().contains("libaria2c.so")) {
                    request
                        .addOption("--external-downloader-args", "aria2c:--summary-interval=1")
                        .addOption(
                            "--external-downloader-args",
                            "aria2c:--ca-certificate=$ENV_SSL_CERT_FILE"
                        )
                }

                request.addOption("--ffmpeg-location", ffmpegPath!!.absolutePath)
                val outBuffer = StringBuffer() // stdout
                val errBuffer = StringBuffer() // stderr
                val startTime = System.currentTimeMillis()
                val args = request.buildCommand()
                val command: MutableList<String?> = ArrayList()
                command.addAll(listOf(pythonPath!!.absolutePath, ytdlpPath!!.absolutePath))
                command.addAll(args)
                val processBuilder = pythonProcess(command)


                val process: Process = processBuilder.start()
                downloadIdProcessMap[processId] = process
                try {
                    withContext(Dispatchers.Main) {
                        onStartProcess(processId)
                    }
                    streamProcessExtractor = StreamProcessExtractor()
                    streamGobbler = StreamGobbler()
                    val stdOutProcessor = streamProcessExtractor?.readStream(
                        outBuffer,
                        process.inputStream,
                        progressCallBack
                    )
                    val stdErrProcessor = streamGobbler?.readStream(errBuffer, process.errorStream)
                    val stdOutFfmpeg = if (request.hasOption("--downloader")) {
                        val downloader = request.getOption("--downloader").toString()
                        if (downloader == "ffmpeg") {
                            ffmpegStreamExtractor = FfmpegStreamExtractor()
                            ffmpegStreamExtractor?.readStream(process, progressCallBack)
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    val exitCode: Int = try {
                        stdOutProcessor?.join()
                        stdErrProcessor?.join()
                        stdOutFfmpeg?.join()
                        process.waitFor()
                    } catch (e: InterruptedException) {
                        process.destroy()
                        downloadIdProcessMap.remove(processId)
                        throw e
                    }

                    val out = outBuffer.toString()
                    val err = errBuffer.toString()

                    if (exitCode > 0) {
                        if (!downloadIdProcessMap.containsKey(processId)) {
                            val canceledException = CanceledException()
                            throw canceledException
                        }
                        if (!ignoreErrors(request, out)) {
                            downloadIdProcessMap.remove(processId)
                            val youtubeDLException = YoutubeDLException(err)
                            throw youtubeDLException
                        }
                    }
                    downloadIdProcessMap.remove(processId)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val response = YoutubeDLResponse(command, exitCode, elapsedTime, out, err)
                    withContext(Dispatchers.Main) {
                        onEndProcess(response)
                    }
                } finally {
                    // Cancelling the returned Job (or a throwing callback) used to leave python and
                    // ffmpeg downloading in the background and the id stuck in downloadIdProcessMap.
                    if (process.isRunning()) killProcessTree(process)
                    // Only our own entry: onEndProcess may already have started a download with the same id.
                    downloadIdProcessMap.remove(processId, process)
                }
            } catch (e: Exception) {
                throw e
            } finally {
                ffmpegStreamExtractor = null
                streamGobbler = null
                streamProcessExtractor = null
            }
        }
    }

    /**
     * Destroys the download process associated with the given process ID.
     *
     * @param id The unique identifier of the process to be terminated.
     * @return `true` if the process was successfully destroyed, `false` if no matching process was found.
     *
     * This function looks up the process in [downloadIdProcessMap], terminates it,
     * and removes it from the map. If running on Android O (API 26+) or higher,
     * it checks whether the process is still alive before destroying it.
     */
    private fun Process.isRunning(): Boolean = try {
        exitValue()
        false
    } catch (e: IllegalThreadStateException) {
        true
    }

    /** Kills ffmpeg/aria2c started by python (if any), then python itself. */
    private fun killProcessTree(process: Process) {
        process.getProcessId().getChildProcessId().killProcess()
        process.destroy()
    }

    fun destroyProcessById(id: String): Boolean {
        if (downloadIdProcessMap.containsKey(id)) {
            val pythonProcess = downloadIdProcessMap[id]
            pythonProcess?.let { pythonProcess.getProcessId().getChildProcessId().killProcess() }
            var alive = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                alive = pythonProcess!!.isAlive
            }
            if (alive) {
                pythonProcess!!.destroy()
                downloadIdProcessMap.remove(id)
                return true
            }
        }
        return false
    }

    /**
     * Returns the count of downloads that are currently in progress.
     *
     * This function checks the size of the `downloadIdProcessMap`, which keeps track
     * of active downloads, and returns the total number of ongoing downloads.
     *
     * @return The number of downloads currently in progress.
     */
    fun getInProgressDownloadsCount(): Int {
        return downloadIdProcessMap.size
    }


    suspend fun updateYoutubeDL(
        appContext: Context,
        updateChannel: UpdateChannel = UpdateChannel.STABLE,
        onSuccess: (UpdateStatus) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            assertInit()
            try {
                val status = YoutubeDLUpdater.update(appContext, updateChannel)
                onSuccess(status)
            } catch (e: IOException) {
                onError(e)
            }
        }
    }

    fun version(appContext: Context?): String? {
        return YoutubeDLUpdater.version(appContext)
    }

    fun versionName(appContext: Context?): String? {
        return YoutubeDLUpdater.versionName(appContext)
    }


    const val baseName = "youtubeDl-android"
    private const val packagesRoot = "packages"
    private const val pythonBinName = "libpython.so"
    private const val pythonLibName = "libpython.zip.so"
    private const val pythonDirName = "python"
    private const val ffmpegDirName = "ffmpeg"
    private const val ffmpegBinName = "libffmpeg.so"
    private const val aria2cDirName = "aria2c"
    const val ytdlpDirName = "yt-dlp"
    const val ytdlpBin = "yt-dlp"
    private const val pythonLibVersion = "pythonLibVersion"
    // Version of res/raw/ytdlp. Update together with that file.
    private const val BUNDLED_YTDLP_VERSION = "2026.10.26"

    // argv: <yt-dlp zipapp> <output>. Writes every .py as a sourceless .pyc (zipimport loads those
    // without checking a source), everything else as is.
    private val COMPILE_YTDLP = """
import sys, zipfile, importlib.util, marshal
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        data = zin.read(info)
        if info.filename.endswith('.py'):
            code = compile(data, info.filename, 'exec', dont_inherit=True)
            zout.writestr(info.filename + 'c', importlib.util.MAGIC_NUMBER + bytes(12) + marshal.dumps(code))
        else:
            zout.writestr(info, data)
""".trimIndent()
    // Lazy: building an ObjectMapper is slow and used to run on whichever thread first touched YoutubeDL.
    val objectMapper: ObjectMapper by lazy { ObjectMapper() }

    fun getInstance() = this


}