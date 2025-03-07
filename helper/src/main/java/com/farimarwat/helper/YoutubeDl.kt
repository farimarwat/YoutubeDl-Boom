package com.farimarwat.helper

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.full.memberFunctions

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

}