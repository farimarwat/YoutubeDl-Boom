package com.farimarwat.helper

import android.content.Context
import com.farimarwat.helper.mapper.VideoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties


object YoutubeDl {

    fun init(
        appContext: Context,
        withFfmpeg: Boolean = false,
        withAria2c: Boolean = false,
        onSuccess:(Any) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin
                val youtubeDLInstance = youtubeDLClass.objectInstance
                if (youtubeDLInstance == null) {
                    onError(IllegalStateException("Failed to access the singleton instance of YoutubeDL"))
                    return@launch
                }
                val initMethod = youtubeDLClass.memberFunctions.find { it.name == "init" }
                if (initMethod == null) {
                    onError(NoSuchMethodException("init method not found in YoutubeDL"))
                    return@launch
                }
                val job = initMethod.call(
                    youtubeDLInstance,
                    appContext,
                    withFfmpeg,
                    withAria2c,
                    { youtubeDL:Any -> onSuccess(youtubeDLInstance) },
                    { throwable:Throwable -> onError(throwable) }
                ) as? Job

                if (job == null) {
                    onError(IllegalStateException("Failed to start initialization job"))
                    return@launch
                }
                job.join()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun getInfo(
        url: String,
        onSuccess: (Any) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin

                val youtubeDLInstance = youtubeDLClass.objectInstance
                    if(youtubeDLInstance == null){
                        onError(IllegalStateException("Failed to access the singleton instance of YoutubeDL"))
                        return@launch
                    }

                val getInfoMethod = youtubeDLClass.memberFunctions.find { function ->
                    function.name == "getInfo" &&
                            function.parameters.getOrNull(1)?.type?.classifier == String::class
                }
                if(getInfoMethod == null){
                    onError(NoSuchMethodException("getInfo method with String URL not found in YoutubeDL"))
                    return@launch
                }

                getInfoMethod.call(
                    youtubeDLInstance,
                    url,
                    { videoInfo: Any -> onSuccess(videoInfo) },
                    { throwable: Throwable -> onError(throwable) }
                )

            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun download(
        request: YoutubeDLRequest, // Use the moved class directly
        pId: String? = null,
        progressCallBack: ((Float, Long, String) -> Unit)? = null,
        onStartProcess: (String) -> Unit = {},
        onEndProcess: (Any) -> Unit = {}, // Use `Any` since we can't reference `YoutubeDLResponse` directly
        onError: (Throwable) -> Unit = {}
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Load the YoutubeDL class dynamically
                val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin

                // Step 2: Access the singleton instance of the object
                val youtubeDLInstance = youtubeDLClass.objectInstance
                if (youtubeDLInstance == null) {
                    throw IllegalStateException("Failed to access the singleton instance of YoutubeDL")
                }

                // Step 3: Find the `download` method with the correct signature
                val downloadMethod = youtubeDLClass.memberFunctions.find { function ->
                    function.name == "download" &&
                            function.parameters.size >= 6 // At least 6 parameters: instance, request, pId, progressCallBack, onStartProcess, onEndProcess, onError
                }

                if (downloadMethod == null) {
                    throw NoSuchMethodException("download method not found in YoutubeDL")
                }

                // Step 4: Prepare the parameters for the method call
                val params = listOf(
                    request, // YoutubeDLRequest
                    pId,    // Process ID
                    progressCallBack, // Progress callback
                    { processId: String -> onStartProcess(processId) }, // onStartProcess
                    { response: Any -> onEndProcess(response) }, // onEndProcess
                    { throwable: Throwable -> onError(throwable) } // onError
                )

                // Step 5: Invoke the `download` method with the parameters
                downloadMethod.callBy(mapOf(
                    downloadMethod.parameters[0] to youtubeDLInstance, // Object instance
                    downloadMethod.parameters[1] to params[0], // request
                    downloadMethod.parameters[2] to params[1], // pId
                    downloadMethod.parameters[3] to params[2], // progressCallBack
                    downloadMethod.parameters[4] to params[3], // onStartProcess
                    downloadMethod.parameters[5] to params[4], // onEndProcess
                    downloadMethod.parameters[6] to params[5] // onError
                ))
            } catch (e: Exception) {
                onError(e)
            }
        }
    }


    suspend fun mapVideoInfo(source: Any): VideoInfo {
        return withContext(Dispatchers.IO){
            VideoInfo(
                id = source::class.memberProperties.find { it.name == "id" }?.call(source) as? String,
                fulltitle = source::class.memberProperties.find { it.name == "fulltitle" }?.call(source) as? String,
                title = source::class.memberProperties.find { it.name == "title" }?.call(source) as? String,
                uploadDate = source::class.memberProperties.find { it.name == "uploadDate" }?.call(source) as? String,
                displayId = source::class.memberProperties.find { it.name == "displayId" }?.call(source) as? String,
                duration = source::class.memberProperties.find { it.name == "duration" }?.call(source) as? Int ?: 0,
                description = source::class.memberProperties.find { it.name == "description" }?.call(source) as? String,
                thumbnail = source::class.memberProperties.find { it.name == "thumbnail" }?.call(source) as? String,
                license = source::class.memberProperties.find { it.name == "license" }?.call(source) as? String,
                extractor = source::class.memberProperties.find { it.name == "extractor" }?.call(source) as? String,
                extractorKey = source::class.memberProperties.find { it.name == "extractorKey" }?.call(source) as? String,
                viewCount = source::class.memberProperties.find { it.name == "viewCount" }?.call(source) as? String,
                likeCount = source::class.memberProperties.find { it.name == "likeCount" }?.call(source) as? String,
                dislikeCount = source::class.memberProperties.find { it.name == "dislikeCount" }?.call(source) as? String,
                repostCount = source::class.memberProperties.find { it.name == "repostCount" }?.call(source) as? String,
                averageRating = source::class.memberProperties.find { it.name == "averageRating" }?.call(source) as? String,
                uploaderId = source::class.memberProperties.find { it.name == "uploaderId" }?.call(source) as? String,
                uploader = source::class.memberProperties.find { it.name == "uploader" }?.call(source) as? String,
                playerUrl = source::class.memberProperties.find { it.name == "playerUrl" }?.call(source) as? String,
                webpageUrl = source::class.memberProperties.find { it.name == "webpageUrl" }?.call(source) as? String,
                webpageUrlBasename = source::class.memberProperties.find { it.name == "webpageUrlBasename" }?.call(source) as? String,
                resolution = source::class.memberProperties.find { it.name == "resolution" }?.call(source) as? String,
                width = source::class.memberProperties.find { it.name == "width" }?.call(source) as? Int ?: 0,
                height = source::class.memberProperties.find { it.name == "height" }?.call(source) as? Int ?: 0,
                format = source::class.memberProperties.find { it.name == "format" }?.call(source) as? String,
                formatId = source::class.memberProperties.find { it.name == "formatId" }?.call(source) as? String,
                ext = source::class.memberProperties.find { it.name == "ext" }?.call(source) as? String,
                fileSize = source::class.memberProperties.find { it.name == "fileSize" }?.call(source) as? Long ?: 0,
                fileSizeApproximate = source::class.memberProperties.find { it.name == "fileSizeApproximate" }?.call(source) as? Long ?: 0,
                httpHeaders = source::class.memberProperties.find { it.name == "httpHeaders" }?.call(source) as? Map<String, String>,
                categories = source::class.memberProperties.find { it.name == "categories" }?.call(source) as? ArrayList<String>,
                tags = source::class.memberProperties.find { it.name == "tags" }?.call(source) as? ArrayList<String>,
                requestedFormats = source::class.memberProperties.find { it.name == "requestedFormats" }?.call(source) as? ArrayList<com.farimarwat.helper.mapper.VideoFormat>,
                formats = source::class.memberProperties.find { it.name == "formats" }?.call(source) as? ArrayList<com.farimarwat.helper.mapper.VideoFormat>,
                thumbnails = source::class.memberProperties.find { it.name == "thumbnails" }?.call(source) as? ArrayList<com.farimarwat.helper.mapper.VideoThumbnail>,
                manifestUrl = source::class.memberProperties.find { it.name == "manifestUrl" }?.call(source) as? String,
                url = source::class.memberProperties.find { it.name == "url" }?.call(source) as? String,
                isLive = source::class.memberProperties.find { it.name == "isLive" }?.call(source) as? Boolean
            )
        }
    }
}