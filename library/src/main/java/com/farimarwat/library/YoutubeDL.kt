package com.farimarwat.library

import android.content.Context
import android.os.Build
import com.farimarwat.common.SharedPrefsHelper
import com.farimarwat.common.SharedPrefsHelper.update
import com.farimarwat.downloadmanager.YoutubeDlFileManager
import com.farimarwat.common.utils.ZipUtils.unzip
import com.fasterxml.jackson.databind.ObjectMapper
import com.yausername.youtubedl_android.ProcessUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Collections
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
    private val idProcessMap = Collections.synchronizedMap(HashMap<String, Process>())

     fun init(appContext: Context,
              fileManager:YoutubeDlFileManager = YoutubeDlFileManager,
              onSuccess:suspend (YoutubeDL)->Unit={},
              onError:(Throwable)->Unit={}):Job {
         val exception = CoroutineExceptionHandler { _, throwable ->
             onError(throwable)
         }
         val job = Job()
         val scope = CoroutineScope(Dispatchers.IO+job+exception)
       return scope.launch {
           if(fileManager.isReady(appContext)){
               performInit(appContext)
               withContext(Dispatchers.Main){
                   onSuccess(this@YoutubeDL)
               }
           } else {
               fileManager.downloadLibFiles{ success, error ->
                   if(success){
                       performInit(appContext)
                       withContext(Dispatchers.Main){
                           onSuccess(this@YoutubeDL)
                       }
                   } else {
                       withContext(Dispatchers.Main){
                           onSuccess(this@YoutubeDL)
                       }
                   }
               }
           }
       }
    }

    private fun performInit(appContext: Context){
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
        init_ytdlp(appContext, ytdlpDir)
        initialized = true
    }
    @Throws(YoutubeDLException::class)
    internal fun init_ytdlp(appContext: Context, ytdlpDir: File) {
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        val ytdlpBinary = File(ytdlpDir, ytdlpBin)
        if (!ytdlpBinary.exists()) {
            try {
                val inputStream = File(YoutubeDlFileManager.DOWNLOAD_DIR, ytdlpBin).inputStream()
                FileUtils.copyInputStreamToFile(inputStream, ytdlpBinary)
            } catch (e: Exception) {
                FileUtils.deleteQuietly(ytdlpDir)
                throw YoutubeDLException("failed to initialize", e)
            }
        }
    }

    @Throws(YoutubeDLException::class)
    internal fun initPython(appContext: Context, pythonDir: File) {
        val pythonLib = File(YoutubeDlFileManager.DOWNLOAD_DIR, pythonLibName)
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

    suspend fun getInfo(
        url: String,
        onSuccess: (VideoInfo) -> Unit = {},
        onError: (Throwable) -> Unit = {}){
        val request = YoutubeDLRequest(url)
         getInfo(
            request = request,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    suspend fun getInfo(
        request: YoutubeDLRequest,
        onSuccess: (VideoInfo) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        try {
            request.addOption("--dump-json")
            val response = withContext(Dispatchers.IO) {
                execute(request, null, null)
            }
            val videoInfo = response.out.let { jsonOutput ->
                try {
                    objectMapper.readValue(jsonOutput, VideoInfo::class.java)
                        ?: throw YoutubeDLException("Failed to parse video information: JSON output is null")
                } catch (e: IOException) {
                    throw YoutubeDLException("Unable to parse video information", e)
                }
            }
            onSuccess(videoInfo)
        } catch (e: Throwable) {
            Timber.e(e, "Failed to fetch video information")
            onError(e)
        }
    }

    private fun ignoreErrors(request: YoutubeDLRequest, out: String): Boolean {
        return request.hasOption("--dump-json") && !out.isEmpty() && request.hasOption("--ignore-errors")
    }

    fun destroyProcessById(id: String): Boolean {
        if (idProcessMap.containsKey(id)) {
            val p = idProcessMap[id]
            p?.let{ProcessUtils.killChildProcess(p)}
            var alive = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                alive = p!!.isAlive
            }
            if (alive) {
                p!!.destroy()
                idProcessMap.remove(id)
                return true
            }
        }
        return false
    }

    class CanceledException : Exception()

    @Throws(YoutubeDLException::class, InterruptedException::class, CanceledException::class)
    suspend fun execute(
        request: YoutubeDLRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse {
        return withContext(Dispatchers.IO){
            assertInit()
            if (processId != null && idProcessMap.containsKey(processId)) throw YoutubeDLException("Process ID already exists")
            // disable caching unless explicitly requested
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

            /* Set ffmpeg location, See https://github.com/xibr/ytdlp-lazy/issues/1 */
            request.addOption("--ffmpeg-location", ffmpegPath!!.absolutePath)
            val youtubeDLResponse: YoutubeDLResponse
            val process: Process
            val exitCode: Int
            val outBuffer = StringBuffer() //stdout
            val errBuffer = StringBuffer() //stderr
            val startTime = System.currentTimeMillis()
            val args = request.buildCommand()
            val command: MutableList<String?> = ArrayList()
            command.addAll(listOf(pythonPath!!.absolutePath, ytdlpPath!!.absolutePath))
            command.addAll(args)
            Timber.i("Mycommand: ${command}")
            val processBuilder = ProcessBuilder(command)
            processBuilder.environment().apply {
                this["LD_LIBRARY_PATH"] = ENV_LD_LIBRARY_PATH
                this["SSL_CERT_FILE"] = ENV_SSL_CERT_FILE
                this["PATH"] = System.getenv("PATH") + ":" + binDir!!.absolutePath
                this["PYTHONHOME"] = ENV_PYTHONHOME
                this["HOME"] = ENV_PYTHONHOME
                this["TMPDIR"] = TMPDIR
            }

            process = try {
                processBuilder.start()
            } catch (e: IOException) {
                throw YoutubeDLException(e)
            }
            if (processId != null) {
                idProcessMap[processId] = process
            }
            val outStream = process.inputStream
            val errStream = process.errorStream
            val stdOutProcessor = StreamProcessExtractor(outBuffer, outStream, callback)
            val stdErrProcessor = StreamGobbler(errBuffer, errStream)
            exitCode = try {
                stdOutProcessor.join()
                stdErrProcessor.join()
                process.waitFor()
            } catch (e: InterruptedException) {
                process.destroy()
                if (processId != null) idProcessMap.remove(processId)
                throw e
            }
            val out = outBuffer.toString()
            val err = errBuffer.toString()
            if (exitCode > 0) {
                if (processId != null && !idProcessMap.containsKey(processId))
                    throw CanceledException()
                if (!ignoreErrors(request, out)) {
                    idProcessMap.remove(processId)
                    throw YoutubeDLException(err)
                }
            }
            idProcessMap.remove(processId)

            val elapsedTime = System.currentTimeMillis() - startTime
            youtubeDLResponse = YoutubeDLResponse(command, exitCode, elapsedTime, out, err)
            youtubeDLResponse
        }
    }

    @Throws(YoutubeDLException::class)
    suspend fun updateYoutubeDL(
        appContext: Context,
        updateChannel: UpdateChannel = UpdateChannel.STABLE,
        onSuccess: (UpdateStatus) -> Unit={},
        onError: (Throwable) -> Unit={}
    ) {
        withContext(Dispatchers.IO){
            if(!YoutubeDlFileManager.isReady(appContext)) onError(YoutubeDLException("Upddate Error: Kindly initialize YoutubeDl first"))
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


    const val baseName = "youtubedl-android"
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