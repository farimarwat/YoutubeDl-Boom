package com.farimarwat.library

import android.app.Activity
import android.app.Activity.BIND_AUTO_CREATE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.farimarwat.aria2c.Aria2c
import com.farimarwat.common.SharedPrefsHelper
import com.farimarwat.common.SharedPrefsHelper.update
import com.farimarwat.common.utils.ZipUtils.unzip
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

import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.UUID
import kotlin.collections.set

object YoutubeDL {
    private var initialized = false
    private var pythonPath: File? = null
    private var ffmpegPath: File? = null
    private var ytdlpPath: File? = null
    private var binDir: File? = null
    private var ENV_LD_LIBRARY_PATH: String? = null
    private var ENV_SSL_CERT_FILE: String? = null
    private var ENV_PYTHONHOME: String? = null
    private var TMPDIR: String = ""
    private val downloadIdProcessMap = Collections.synchronizedMap(HashMap<String, Process>())
    private var serviceConnection:ServiceConnection? = null
    var isBound = false
    private var isInitializing = false

    /**
     * Initializes the YoutubeDL library asynchronously.
     *
     * This function checks if the required library files are ready. If they are,
     * it initializes YoutubeDL, FFmpeg, and Aria2c if enabled. If the files are not ready,
     * it attempts to download them before proceeding with initialization.
     *
     * @param appContext The application context used for initialization.
     * @param fileManager The file manager responsible for handling YoutubeDL library files. Defaults to [YoutubeDlFileManager].
     * @param onSuccess Callback invoked on successful initialization with the initialized [YoutubeDL] instance.
     * @param onError Callback invoked if an error occurs during initialization.
     * @return A [Job] representing the initialization coroutine.
     */
    fun init(
        appContext: Context,
        withFFmpeg:Boolean = false,
        withAria2c:Boolean = false,
        onSuccess: suspend (YoutubeDL) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        val exception = CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job + exception)
        return scope.launch {
            performInit(appContext)
            if (withFFmpeg) {
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
        ytdlpPath = File(ytdlpDir, ytdlpBin)
        ENV_LD_LIBRARY_PATH = pythonDir.absolutePath + "/usr/lib" + ":" +
                ffmpegDir.absolutePath + "/usr/lib" + ":" +
                aria2cDir.absolutePath + "/usr/lib"
        ENV_SSL_CERT_FILE = pythonDir.absolutePath + "/usr/etc/tls/cert.pem"
        ENV_PYTHONHOME = pythonDir.absolutePath + "/usr"
        TMPDIR = appContext.cacheDir.absolutePath
        initPython(appContext, pythonDir)
        init_ytdlp(appContext,ytdlpDir)
        initialized = true
    }

    @Throws(YoutubeDLException::class)
    internal fun init_ytdlp(appContext: Context,ytdlpDir: File) {
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        val ytdlpBinary = File(ytdlpDir, ytdlpBin)
        if (!ytdlpBinary.exists()) {
            try {
                val inputStream = appContext.resources.openRawResource(R.raw.ytdlp)
                FileUtils.copyInputStreamToFile(inputStream, ytdlpBinary)
            } catch (e: Exception) {
                FileUtils.deleteQuietly(ytdlpDir)
                throw YoutubeDLException("failed to initialize", e)
            }
        }
    }

    @Throws(YoutubeDLException::class)
    internal fun initPython(appContext: Context, pythonDir: File) {
        val pythonLib = File(appContext.applicationInfo.nativeLibraryDir, pythonLibName)
        val pythonSize = pythonLib.length().toString()
        if (!pythonDir.exists() || shouldUpdatePython(appContext, pythonSize)) {
            FileUtils.deleteQuietly(pythonDir)
            pythonDir.mkdirs()
            try {
                unzip(pythonLib, pythonDir)
            } catch (e: Exception) {
                FileUtils.deleteQuietly(pythonDir)
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
     * Retrieves video information from the given URL.
     *
     * @param url The URL of the video.
     * @param onSuccess Callback function invoked with the retrieved [VideoInfo] on success.
     * @param onError Callback function invoked with an error [Throwable] if retrieval fails.
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
                val processBuilder = ProcessBuilder(command).apply {
                    environment().apply {
                        this["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
                        this["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
                        this["PATH"] = System.getenv("PATH") + ":" + binDir!!.absolutePath
                        this["PYTHONHOME"] = ENV_PYTHONHOME
                        this["HOME"] = ENV_PYTHONHOME
                        this["TMPDIR"] = TMPDIR
                    }
                }

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
                val videoInfo = out.let { jsonOutput ->
                    try {
                        objectMapper.readValue(jsonOutput, VideoInfo::class.java)
                            ?: throw YoutubeDLException("Failed to parse video information: JSON output is null")
                    } catch (e: IOException) {
                        throw YoutubeDLException("Unable to parse video information", e)
                    }
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
     * Downloads a video using the given request.
     *
     * @param request The [YoutubeDLRequest] containing download parameters.
     * @param pId Optional process ID to track the download.
     * @param progressCallBack Callback function for reporting progress with percentage, elapsed time, and speed.
     * @param onStartProcess Callback function invoked when the process starts with the process ID.
     * @param onEndProcess Callback function invoked when the process ends with the [YoutubeDLResponse].
     * @param onError Callback function invoked if an error occurs during the download.
     * @return A [Job] representing the coroutine handling the download process.
     * @throws YoutubeDLException If an error occurs during execution.
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
                val processBuilder = ProcessBuilder(command).apply {
                    environment().apply {
                        this["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
                        this["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
                        this["PATH"] = System.getenv("PATH") + ":" + binDir!!.absolutePath
                        this["PYTHONHOME"] = ENV_PYTHONHOME
                        this["HOME"] = ENV_PYTHONHOME
                        this["TMPDIR"] = TMPDIR
                    }
                }


                val process: Process = processBuilder.start()
                withContext(Dispatchers.Main) {
                    onStartProcess(processId)
                }
                downloadIdProcessMap[processId] = process
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
     * Terminates a running process by its ID.
     *
     * @param id The unique identifier of the process to be terminated.
     * @return `true` if the process was successfully destroyed, `false` if the process was not found or could not be terminated.
     */
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


    @Throws(YoutubeDLException::class)
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

    enum class UpdateStatus {
        DONE, ALREADY_UP_TO_DATE
    }

    open class UpdateChannel(val apiUrl: String) {
        object STABLE : UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
        object NIGHTLY :
            UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest")

        object MASTER :
            UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp-master-builds/releases/latest")

        companion object {
            @JvmField
            val _STABLE: STABLE = STABLE

            @JvmField
            val _NIGHTLY: NIGHTLY = NIGHTLY

            @JvmField
            val _MASTER: MASTER = MASTER
        }
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
    val objectMapper = ObjectMapper()

    fun getInstance() = this


}