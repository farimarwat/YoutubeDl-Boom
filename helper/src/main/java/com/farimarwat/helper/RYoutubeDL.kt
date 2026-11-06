package com.farimarwat.helper

import android.content.Context
import android.util.Log
import com.farimarwat.commons.UpdateChannel
import com.farimarwat.commons.VideoInfo
import com.farimarwat.commons.YoutubeDLRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Calls youtubedl-boom's `YoutubeDL` when it lives in a Dynamic Feature Module, where the app
 * module cannot reference it at compile time.
 *
 * Uses plain Java reflection on purpose: kotlin-reflect needs the `kotlin/` builtins resources,
 * which R8 / dynamic-feature apps often do not ship ("Built-in class kotlin.Any is not found").
 */
object RYoutubeDL {
    private const val TAG = "RYoutubeDL"
    private const val YOUTUBE_DL_CLASS = "com.farimarwat.library.YoutubeDL"

    private fun youtubeDL(): Any =
        Class.forName(YOUTUBE_DL_CLASS).getField("INSTANCE").get(null)
            ?: throw IllegalStateException("Failed to access the singleton instance of YoutubeDL")

    private fun method(name: String, vararg parameterTypes: Class<*>): Method =
        Class.forName(YOUTUBE_DL_CLASS).getMethod(name, *parameterTypes)

    /** Calls [method] on the YoutubeDL instance and rethrows the library's own exception, not the reflection wrapper. */
    private fun invoke(method: Method, vararg args: Any?): Any? = try {
        method.invoke(youtubeDL(), *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }

    /**
     * Initializes the YoutubeDL library in a background coroutine.
     *
     * @param appContext The application context used for initialization.
     * @param withFfmpeg Whether to initialize with FFmpeg support. Defaults to `false`.
     * @param withAria2c Whether to initialize with Aria2c support. Defaults to `false`.
     * @param onSuccess Callback invoked upon successful initialization with the YoutubeDL instance.
     * @param onError Callback invoked in case of an error during initialization.
     *
     * @return A [Job] representing the coroutine performing the initialization.
     */
    fun init(
        appContext: Context,
        withFfmpeg: Boolean = false,
        withAria2c: Boolean = false,
        onSuccess: suspend (Any) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val initMethod = method(
                    "init",
                    Context::class.java,
                    Boolean::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    Function2::class.java,
                    Function1::class.java
                )
                // The library's onSuccess is `suspend (YoutubeDL) -> Unit`: a Function2 taking a Continuation last.
                val successCallback: (Any?, Any?) -> Any? = { instance, _ ->
                    CoroutineScope(Dispatchers.Main).launch { onSuccess(instance ?: youtubeDL()) }
                    Unit
                }
                val errorCallback: (Throwable) -> Unit = { throwable -> onError(throwable) }
                val job = invoke(
                    initMethod, appContext, withFfmpeg, withAria2c, successCallback, errorCallback
                ) as? Job
                if (job == null) {
                    onError(IllegalStateException("Failed to start initialization job"))
                    return@launch
                }
                job.join()
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    /**
     * Retrieves information about a given video URL using the YoutubeDL library.
     *
     * This function runs on the IO thread to perform network or disk operations without blocking the main thread.
     *
     * @param url The URL of the video to fetch information for.
     * @param onSuccess Callback invoked with the retrieved video information upon success.
     * @param onError Callback invoked in case of an error during the retrieval process.
     *
     * @return A [Job] representing the coroutine performing the information retrieval on [Dispatchers.IO].
     */
    fun getInfo(
        url: String,
        onSuccess: suspend (VideoInfo) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job = callGetInfo(String::class.java, url, onSuccess, onError)

    /**
     * Retrieves video information for a [YoutubeDLRequest], so extra yt-dlp options can be passed
     * (for example `--use-extractors`). `--dump-json` is added by the library.
     *
     * @param request The request containing the URL and options.
     * @param onSuccess Callback invoked on the main thread with the retrieved video information.
     * @param onError Callback invoked in case of an error during the retrieval process.
     *
     * @return A [Job] representing the coroutine that starts the retrieval on [Dispatchers.IO].
     */
    fun getInfo(
        request: YoutubeDLRequest,
        onSuccess: suspend (VideoInfo) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job = callGetInfo(YoutubeDLRequest::class.java, request, onSuccess, onError)

    private fun callGetInfo(
        argumentType: Class<*>,
        argument: Any,
        onSuccess: suspend (VideoInfo) -> Unit,
        onError: (Throwable) -> Unit
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val getInfoMethod =
                    method("getInfo", argumentType, Function1::class.java, Function1::class.java)
                val successCallback: (VideoInfo) -> Unit = { videoInfo ->
                    CoroutineScope(Dispatchers.Main).launch { onSuccess(videoInfo) }
                }
                val errorCallback: (Throwable) -> Unit = { throwable -> onError(throwable) }
                invoke(getInfoMethod, argument, successCallback, errorCallback)
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    /**
     * Initiates the download process for a given request using the YoutubeDL library.
     *
     * This function runs on the IO thread to handle potentially long-running operations such as downloading.
     *
     * @param request The dynamically created YoutubeDLRequest object containing download parameters.
     * @param pId An optional process ID to track the download instance.
     * @param progressCallBack Callback invoked to report download progress, providing the progress percentage,
     *        total file size, and formatted download speed.
     * @param onStartProcess Callback invoked when the download process starts, receiving the process ID.
     * @param onEndProcess Callback invoked upon successful completion of the download, returning the response.
     * @param onError Callback invoked in case of an error during the download process.
     *
     * @return A [Job] representing the coroutine performing the download on [Dispatchers.IO].
     */
    fun download(
        request: YoutubeDLRequest,
        pId: String? = null,
        progressCallBack: ((Float, Long, String) -> Unit)? = null,
        onStartProcess: (String) -> Unit = {},
        onEndProcess: (Any) -> Unit = {}, // Use `Any` since we can't reference `YoutubeDLResponse` directly
        onError: (Throwable) -> Unit = {}
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadMethod = method(
                    "download",
                    YoutubeDLRequest::class.java,
                    String::class.java,
                    Function3::class.java,
                    Function1::class.java,
                    Function1::class.java,
                    Function1::class.java
                )
                val startCallback: (String) -> Unit = { processId -> onStartProcess(processId) }
                val endCallback: (Any) -> Unit = { response -> onEndProcess(response) }
                val errorCallback: (Throwable) -> Unit = { throwable -> onError(throwable) }
                invoke(
                    downloadMethod,
                    request, pId, progressCallBack, startCallback, endCallback, errorCallback
                )
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    /**
     * Destroys a process by its ID using the `YoutubeDL` singleton instance.
     *
     * @param id The ID of the process to be destroyed.
     * @return `true` if the process was successfully destroyed, `false` otherwise.
     */
    fun destroyProcessById(id: String): Boolean {
        return try {
            invoke(method("destroyProcessById", String::class.java), id) as Boolean
        } catch (e: Throwable) {
            Log.e(TAG, "destroyProcessById failed", e)
            false
        }
    }

    /**
     * Retrieves the count of in-progress downloads using the `YoutubeDL` singleton instance.
     *
     * @return The number of in-progress downloads. Returns `0` if an error occurs.
     */
    fun getInProgressDownloadsCount(): Int {
        return try {
            invoke(method("getInProgressDownloadsCount")) as Int
        } catch (e: Throwable) {
            Log.e(TAG, "getInProgressDownloadsCount failed", e)
            0
        }
    }

    /**
     * Updates the YoutubeDL library asynchronously using the provided update channel.
     *
     * @param appContext The application context required for the update process.
     * @param updateChannel The update channel (e.g., stable, nightly, master).
     * @param onSuccess A suspend function invoked on the main thread when the update succeeds.
     * @param onError A callback invoked when an error occurs during the update.
     * @return A [Job] representing the coroutine performing the update.
     */
    fun updateYoutubeDL(
        appContext: Context,
        updateChannel: UpdateChannel,
        onSuccess: suspend (Any) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        val exception = CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
        return CoroutineScope(Dispatchers.IO + exception).launch {
            try {
                // `suspend fun updateYoutubeDL(...)` compiles to a method taking a trailing Continuation.
                val updateMethod = method(
                    "updateYoutubeDL",
                    Context::class.java,
                    UpdateChannel::class.java,
                    Function1::class.java,
                    Function1::class.java,
                    Continuation::class.java
                )
                val successCallback: (Any) -> Unit = { updateStatus ->
                    CoroutineScope(Dispatchers.Main).launch { onSuccess(updateStatus) }
                }
                val errorCallback: (Throwable) -> Unit = { throwable -> onError(throwable) }
                suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
                    invoke(
                        updateMethod,
                        appContext, updateChannel, successCallback, errorCallback, continuation
                    )
                }
            } catch (e: Throwable) {
                onError(e)
            }
        }
    }

    /**
     * Retrieves the version of the yt-dlp library.
     *
     * @param appContext The application context required for retrieving the version.
     * @return The version string, or `null` if an error occurs.
     */
    suspend fun version(appContext: Context?): String? = callVersion("version", appContext)

    /**
     * Retrieves the version name of the yt-dlp library.
     *
     * @param appContext The application context required for retrieving the version name.
     * @return The version name string, or `null` if an error occurs.
     */
    suspend fun versionName(appContext: Context?): String? = callVersion("versionName", appContext)

    private suspend fun callVersion(name: String, appContext: Context?): String? {
        return withContext(Dispatchers.IO) {
            try {
                invoke(method(name, Context::class.java), appContext) as? String
            } catch (e: Throwable) {
                Log.e(TAG, "$name failed", e)
                null
            }
        }
    }

    val CHANNEL_STABLE = "STABLE"
    val CHANNEL_NIGHTLY = "NIGHTLY"
    val CHANNEL_MASTER = "MASTER"

}
