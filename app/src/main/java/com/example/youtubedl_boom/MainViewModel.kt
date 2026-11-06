package com.example.youtubedl_boom

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.farimarwat.commons.VideoInfo
import com.farimarwat.commons.YoutubeDLRequest
import com.farimarwat.helper.RYoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

enum class Mode { VIDEO, AUDIO_MP3 }

sealed interface Engine {
    data object Preparing : Engine
    data class Ready(val ytdlpVersion: String?) : Engine
    data class Failed(val message: String) : Engine
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val percent: Float, val etaSeconds: Long, val line: String) : DownloadState
    data object Saving : DownloadState
    data class Done(val fileName: String, val uri: Uri?) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Canceled : DownloadState
}

data class UiState(
    val engine: Engine = Engine.Preparing,
    val url: String = "",
    val scanning: Boolean = false,
    val scanError: String? = null,
    val info: VideoInfo? = null,
    val mode: Mode = Mode.VIDEO,
    val download: DownloadState = DownloadState.Idle,
)

/**
 * Drives the library only through RYoutubeDL (the helper), so the sample also tests the
 * reflection path used from Dynamic Feature Modules. Callbacks arrive on IO or Main threads;
 * StateFlow.update is safe from both.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var processId: String? = null
    private var cancelRequested = false

    init {
        RYoutubeDL.init(
            appContext = app,
            withFfmpeg = true,
            withAria2c = false,
            onSuccess = {
                val version = RYoutubeDL.versionName(app)
                _state.update { it.copy(engine = Engine.Ready(version)) }
            },
            onError = { e ->
                Timber.e(e)
                _state.update { it.copy(engine = Engine.Failed(e.message ?: e.toString())) }
            }
        )
    }

    fun setUrl(url: String) = _state.update { it.copy(url = url.trim(), scanError = null) }

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode) }

    fun scan() {
        val url = _state.value.url
        if (url.isEmpty() || _state.value.scanning) return
        _state.update { it.copy(scanning = true, scanError = null, info = null) }
        RYoutubeDL.getInfo(
            url = url,
            onSuccess = { info -> _state.update { it.copy(scanning = false, info = info) } },
            onError = { e ->
                Timber.e(e)
                _state.update { it.copy(scanning = false, scanError = shortError(e)) }
            }
        )
    }

    /** Call only when Storage.hasPermission is true. */
    fun download() {
        val s = _state.value
        if (s.url.isEmpty() || s.download is DownloadState.Running || s.download is DownloadState.Saving) return
        val context = getApplication<Application>()
        val workDir = Storage.newWorkDir(context)
        val request = YoutubeDLRequest(s.url)
            .addOption("-o", File(workDir, "%(title).80B.%(ext)s").absolutePath)
            .addOption("--no-mtime")
        when (s.mode) {
            Mode.VIDEO -> request.addOption("--merge-output-format", "mp4/mkv")
            Mode.AUDIO_MP3 -> request.addOption("-x").addOption("--audio-format", "mp3")
        }
        cancelRequested = false
        _state.update { it.copy(download = DownloadState.Running(0f, -1, "Starting…")) }
        RYoutubeDL.download(
            request = request,
            progressCallBack = { percent, eta, line ->
                _state.update {
                    if (it.download is DownloadState.Running) it.copy(download = DownloadState.Running(percent, eta, line)) else it
                }
            },
            onStartProcess = { id -> processId = id },
            onEndProcess = { response ->
                Timber.i("Download finished: $response")
                processId = null
                viewModelScope.launch { save(workDir) }
            },
            onError = { e ->
                processId = null
                workDir.deleteRecursively()
                val result = if (cancelRequested) DownloadState.Canceled else DownloadState.Failed(shortError(e))
                if (!cancelRequested) Timber.e(e)
                _state.update { it.copy(download = result) }
            }
        )
    }

    fun cancel() {
        val id = processId ?: return
        cancelRequested = true
        RYoutubeDL.destroyProcessById(id)
    }

    fun dismissDownload() = _state.update { it.copy(download = DownloadState.Idle) }

    /** Moves what yt-dlp produced into Downloads/youtubeDl-boom. */
    private suspend fun save(workDir: File) {
        _state.update { it.copy(download = DownloadState.Saving) }
        val result = withContext(Dispatchers.IO) {
            try {
                val file = workDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.length() }
                    ?: return@withContext DownloadState.Failed("yt-dlp finished but produced no file")
                val name = file.name
                DownloadState.Done(name, Storage.publish(getApplication(), file))
            } catch (e: Exception) {
                Timber.e(e)
                DownloadState.Failed("Could not save to Downloads: ${e.message}")
            } finally {
                workDir.deleteRecursively()
            }
        }
        _state.update { it.copy(download = result) }
    }

    /** yt-dlp errors are long; keep the "ERROR: ..." lines. */
    private fun shortError(e: Throwable): String {
        val text = e.message ?: e.toString()
        val errors = text.lines().filter { it.startsWith("ERROR") }
        return (errors.ifEmpty { text.lines().filter { it.isNotBlank() }.takeLast(3) })
            .joinToString("\n").take(500)
    }
}
