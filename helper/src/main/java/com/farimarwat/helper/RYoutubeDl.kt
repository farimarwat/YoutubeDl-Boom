package com.farimarwat.helper

import android.content.Context
import com.farimarwat.commons.VideoInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties


object RYoutubeDl {
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
                    { youtubeDL: Any ->
                        CoroutineScope(Dispatchers.Main).launch {
                            onSuccess(youtubeDLInstance)
                        }
                    },
                    { throwable: Throwable -> onError(throwable) }
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
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin

                val youtubeDLInstance = youtubeDLClass.objectInstance
                if (youtubeDLInstance == null) {
                    onError(IllegalStateException("Failed to access the singleton instance of YoutubeDL"))
                    return@launch
                }

                val getInfoMethod = youtubeDLClass.memberFunctions.find { function ->
                    function.name == "getInfo" &&
                            function.parameters.getOrNull(1)?.type?.classifier == String::class
                }
                if (getInfoMethod == null) {
                    onError(NoSuchMethodException("getInfo method with String URL not found in YoutubeDL"))
                    return@launch
                }

                getInfoMethod.call(
                    youtubeDLInstance,
                    url,
                    { videoInfo: VideoInfo ->
                        CoroutineScope(Dispatchers.Main).launch {
                            onSuccess(videoInfo)
                        }
                    },
                    { throwable: Throwable -> onError(throwable) }
                )

            } catch (e: Exception) {
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
        request: Any, // Dynamically created YoutubeDLRequest
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
                downloadMethod.callBy(
                    mapOf(
                        downloadMethod.parameters[0] to youtubeDLInstance, // Object instance
                        downloadMethod.parameters[1] to params[0], // request
                        downloadMethod.parameters[2] to params[1], // pId
                        downloadMethod.parameters[3] to params[2], // progressCallBack
                        downloadMethod.parameters[4] to params[3], // onStartProcess
                        downloadMethod.parameters[5] to params[4], // onEndProcess
                        downloadMethod.parameters[6] to params[5] // onError
                    )
                )
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Dynamically creates an instance of `YoutubeDLRequest` using reflection.
     *
     * This method loads the `YoutubeDLRequest` class at runtime, retrieves its primary constructor,
     * and instantiates it with the provided URL.
     *
     * @param url The video URL to be used for creating the `YoutubeDLRequest`.
     * @return An instance of `YoutubeDLRequest` created dynamically.
     * @throws IllegalStateException If the class or constructor is not found, or instantiation fails.
     */
    fun createYoutubeDLRequest(url: String): Any {
        return try {
            // Step 1: Load the YoutubeDLRequest class dynamically
            val requestClass = Class.forName("com.farimarwat.library.YoutubeDLRequest").kotlin

            // Step 2: Access the primary constructor that takes a String (URL)
            val constructor =
                requestClass.constructors.find { it.parameters.size == 1 && it.parameters[0].type.classifier == String::class }
            if (constructor == null) {
                throw IllegalStateException("YoutubeDLRequest primary constructor not found")
            }

            // Step 3: Create an instance of YoutubeDLRequest
            constructor.call(url)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create YoutubeDLRequest: ${e.message}")
        }
    }

    /**
     * Adds an option to a dynamically loaded `YoutubeDLRequest` instance.
     *
     * This function uses reflection to find and invoke the `addOption` method of the `YoutubeDLRequest` class.
     *
     * @param request The `YoutubeDLRequest` instance to which the option should be added.
     * @param option The name of the option to be added.
     * @param argument The value of the option, which can be of any type.
     * @return The result of invoking the `addOption` method, or `null` if no return value is expected.
     * @throws IllegalStateException If the `YoutubeDLRequest` class or `addOption` method cannot be found,
     * or if invocation fails.
     */
    fun addOption(request: Any, option: String, argument: Any): Any? {
        return try {
            // Step 1: Load the YoutubeDLRequest class dynamically
            val requestClass = Class.forName("com.farimarwat.library.YoutubeDLRequest").kotlin

            // Step 2: Find the `addOption` method based on the argument type
            val addOptionMethod = requestClass.memberFunctions.find { function ->
                function.name == "addOption" &&
                        function.parameters.size == 3 && // instance, option, argument
                        function.parameters[1].type.classifier == String::class && // option is String
                        function.parameters[2].type.classifier == argument::class // argument matches the type
            }

            if (addOptionMethod == null) {
                throw NoSuchMethodException("addOption method not found in YoutubeDLRequest")
            }

            // Step 3: Invoke the `addOption` method
            addOptionMethod.call(request, option, argument)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to add option to YoutubeDLRequest: ${e.message}")
        }
    }

    /**
     * Adds an option to a dynamically loaded `YoutubeDLRequest` instance.
     *
     * This function uses reflection to find and invoke the `addOption` method of the `YoutubeDLRequest` class,
     * which accepts a single string parameter.
     *
     * @param request The `YoutubeDLRequest` instance to which the option should be added.
     * @param option The name of the option to be added.
     * @return The result of invoking the `addOption` method, or `null` if no return value is expected.
     * @throws IllegalStateException If the `YoutubeDLRequest` class or the corresponding `addOption` method
     * cannot be found, or if invocation fails.
     */
    fun addOption(request: Any, option: String): Any? {
        return try {
            // Step 1: Load the YoutubeDLRequest class dynamically
            val requestClass = Class.forName("com.farimarwat.library.YoutubeDLRequest").kotlin

            // Step 2: Find the `addOption` method that takes a single String parameter
            val addOptionMethod = requestClass.memberFunctions.find { function ->
                function.name == "addOption" &&
                        function.parameters.size == 2 && // instance, option
                        function.parameters[1].type.classifier == String::class // option is String
            }

            if (addOptionMethod == null) {
                throw NoSuchMethodException("addOption method with single String parameter not found in YoutubeDLRequest")
            }

            // Step 3: Invoke the `addOption` method
            addOptionMethod.call(request, option)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to add option to YoutubeDLRequest: ${e.message}")
        }
    }

    /**
     * Destroys a process by its ID using the `YoutubeDL` singleton instance.
     *
     * This function dynamically loads the `YoutubeDL` class, retrieves its singleton instance,
     * and invokes the `destroyProcessById` method to terminate the specified process.
     *
     * @param id The ID of the process to be destroyed.
     * @return `true` if the process was successfully destroyed, `false` otherwise.
     * @throws IllegalStateException If the `YoutubeDL` singleton instance cannot be accessed.
     * @throws NoSuchMethodException If the `destroyProcessById` method is not found in `YoutubeDL`.
     */
    fun destroyProcessById(id: String): Boolean {
        return try {
            val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin

            val youtubeDLInstance = youtubeDLClass.objectInstance
            if (youtubeDLInstance == null) {
                throw IllegalStateException("Failed to access the singleton instance of YoutubeDL")
            }
            val destroyProcessByIdMethod = youtubeDLClass.memberFunctions.find { function ->
                function.name == "destroyProcessById" &&
                        function.parameters.size == 2 && // instance, id
                        function.parameters[1].type.classifier == String::class // id is String
            }

            if (destroyProcessByIdMethod == null) {
                throw NoSuchMethodException("destroyProcessById method not found in YoutubeDL")
            }
            destroyProcessByIdMethod.call(youtubeDLInstance, id) as Boolean
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    /**
     * Retrieves the count of in-progress downloads using the `YoutubeDL` singleton instance.
     *
     * This function dynamically loads the `YoutubeDL` class, retrieves its singleton instance,
     * and invokes the `getInProgressDownloadsCount` method to obtain the number of active downloads.
     *
     * @return The number of in-progress downloads. Returns `0` if an error occurs.
     * @throws IllegalStateException If the `YoutubeDL` singleton instance cannot be accessed.
     * @throws NoSuchMethodException If the `getInProgressDownloadsCount` method is not found in `YoutubeDL`.
     */
    fun getInProgressDownloadsCount(): Int {
        return try {
            val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin
            val youtubeDLInstance = youtubeDLClass.objectInstance
            if (youtubeDLInstance == null) {
                throw IllegalStateException("Failed to access the singleton instance of YoutubeDL")
            }
            val getInProgressDownloadsCountMethod =
                youtubeDLClass.memberFunctions.find { function ->
                    function.name == "getInProgressDownloadsCount" &&
                            function.parameters.size == 1 // instance
                }
            if (getInProgressDownloadsCountMethod == null) {
                throw NoSuchMethodException("getInProgressDownloadsCount method not found in YoutubeDL")
            }
            getInProgressDownloadsCountMethod.call(youtubeDLInstance) as Int
        } catch (e: Exception) {
            println("Error getting in-progress downloads count: ${e.message}")
            0
        }
    }


    /**
     * Updates the YoutubeDL library asynchronously using the provided update channel.
     *
     * This function dynamically loads the `YoutubeDL` class, retrieves its singleton instance,
     * and invokes the `updateYoutubeDL` method to initiate the update process.
     *
     * @param appContext The application context required for the update process.
     * @param updateChannel The update channel (e.g., stable, nightly, master).
     * @param onSuccess A suspend function invoked on the main thread when the update succeeds.
     * @param onError A callback invoked when an error occurs during the update.
     * @return A [Job] representing the coroutine performing the update.
     * @throws IllegalStateException If the `YoutubeDL` singleton instance cannot be accessed.
     * @throws NoSuchMethodException If the `updateYoutubeDL` method is not found in `YoutubeDL`.
     */
    fun updateYoutubeDL(
        appContext: Context,
        updateChannel: Any,
        onSuccess: suspend (Any) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Job {
        val exception = CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
        return CoroutineScope(Dispatchers.IO + exception).launch {
            try {
                // Step 1: Load the YoutubeDL class dynamically
                val youtubeDLClass = Class.forName("com.farimarwat.library.YoutubeDL").kotlin

                // Step 2: Access the singleton instance of the object
                val youtubeDLInstance = youtubeDLClass.objectInstance
                if (youtubeDLInstance == null) {
                    throw IllegalStateException("Failed to access the singleton instance of YoutubeDL")
                }

                // Step 3: Find the `updateYoutubeDL` method
                val updateYoutubeDLMethod = youtubeDLClass.memberFunctions.find { function ->
                    function.name == "updateYoutubeDL" &&
                            function.parameters.size == 5 // instance, appContext, updateChannel, onSuccess, onError
                }

                if (updateYoutubeDLMethod == null) {
                    throw NoSuchMethodException("updateYoutubeDL method not found in YoutubeDL")
                }

                // Step 4: Prepare the parameters for the method call
                val params = mapOf(
                    updateYoutubeDLMethod.parameters[0] to youtubeDLInstance, // Object instance
                    updateYoutubeDLMethod.parameters[1] to appContext, // appContext
                    updateYoutubeDLMethod.parameters[2] to updateChannel, // updateChannel
                    updateYoutubeDLMethod.parameters[3] to { updateStatus: Any ->
                        // Ensure the onSuccess callback runs on the main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            onSuccess(updateStatus)
                        }
                    }, // onSuccess
                    updateYoutubeDLMethod.parameters[4] to { throwable: Throwable ->
                        onError(
                            throwable
                        )
                    } // onError
                )
                updateYoutubeDLMethod.callSuspendBy(params)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Maps an update status object to its corresponding name as a string.
     *
     * This function dynamically retrieves the `name` property of the `UpdateStatus` class
     * from the `YoutubeDL` library.
     *
     * @param updateStatus The update status object to map.
     * @return The name of the update status, or `null` if an error occurs.
     * @throws IllegalStateException If the `name` property cannot be accessed.
     */
    suspend fun mapUpdateStatus(updateStatus: Any): String? {
        return withContext(Dispatchers.IO) {
            try {
                val updateStatusClass =
                    Class.forName("com.farimarwat.library.YoutubeDL\$UpdateStatus").kotlin
                val nameProperty = updateStatusClass.memberProperties.find { it.name == "name" }
                if (nameProperty == null) {
                    throw IllegalStateException("Failed to access 'name' property of UpdateStatus")
                }
                nameProperty.call(updateStatus) as String
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    }

    /**
     * Retrieves the update channel instance corresponding to the provided string value.
     *
     * This function dynamically loads the `UpdateChannel` class from the `YoutubeDL` library
     * and retrieves the appropriate update channel constant.
     *
     * @param value The string representation of the update channel (e.g., "STABLE", "NIGHTLY", "MASTER").
     * @return The corresponding update channel instance.
     * @throws IllegalArgumentException If the provided value is not a valid update channel.
     * @throws IllegalStateException If the update channel instance cannot be accessed.
     */
    suspend fun getUpdateChannel(value: String): Any {
        return withContext(Dispatchers.IO) {
            try {
                val updateChannelClass =
                    Class.forName("com.farimarwat.library.YoutubeDL\$UpdateChannel").kotlin
                val companionObject = updateChannelClass.companionObjectInstance
                if (companionObject == null) {
                    throw IllegalStateException("Failed to access UpdateChannel companion object")
                }
                val updateChannel = when (value) {
                    "STABLE" -> companionObject::class.memberProperties.find { it.name == "_STABLE" }
                        ?.call(companionObject)

                    "NIGHTLY" -> companionObject::class.memberProperties.find { it.name == "_NIGHTLY" }
                        ?.call(companionObject)

                    "MASTER" -> companionObject::class.memberProperties.find { it.name == "_MASTER" }
                        ?.call(companionObject)

                    else -> throw IllegalArgumentException("Invalid UpdateChannel value: $value")
                }

                if (updateChannel == null) {
                    throw IllegalStateException("Failed to access UpdateChannel instance: $value")
                }

                updateChannel
            } catch (e: Exception) {
                throw IllegalStateException("Failed to get UpdateChannel: ${e.message}")
            }
        }
    }

    /**
     * Retrieves the version of the yt-dlp library.
     *
     * This function dynamically loads the `YoutubeDLUpdater` class, retrieves its instance,
     * and invokes the `version` method to obtain the version string.
     *
     * @param appContext The application context required for retrieving the version.
     * @return The version string, or `null` if an error occurs.
     * @throws NoSuchMethodException If the `version` method is not found in `YoutubeDLUpdater`.
     */
    suspend fun version(appContext: Context?): String? {
        return withContext(Dispatchers.IO) {
            try {
                val youtubeDLUpdaterClass =
                    Class.forName("com.farimarwat.library.YoutubeDLUpdater").kotlin
                val instance =
                    youtubeDLUpdaterClass.objectInstance ?: youtubeDLUpdaterClass.createInstance()

                val versionMethod = youtubeDLUpdaterClass.memberFunctions.find { function ->
                    function.name == "version" &&
                            function.parameters.size == 2 // instance, appContext
                }
                if (versionMethod == null) {
                    throw NoSuchMethodException("version method not found in YoutubeDLUpdater")
                }
                versionMethod.call(instance, appContext) as? String
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    }

    /**
     * Retrieves the version name of the yt-dlp library.
     *
     * This function dynamically loads the `YoutubeDLUpdater` class, retrieves its instance,
     * and invokes the `versionName` method to obtain the version name.
     *
     * @param appContext The application context required for retrieving the version name.
     * @return The version name string, or `null` if an error occurs.
     * @throws NoSuchMethodException If the `versionName` method is not found in `YoutubeDLUpdater`.
     */
    suspend fun versionName(appContext: Context?): String? {
        return withContext(Dispatchers.IO) {
            try {
                val youtubeDLUpdaterClass =
                    Class.forName("com.farimarwat.library.YoutubeDLUpdater").kotlin
                // Create an instance of YoutubeDLUpdater
                val instance =
                    youtubeDLUpdaterClass.objectInstance ?: youtubeDLUpdaterClass.createInstance()

                val versionNameMethod = youtubeDLUpdaterClass.memberFunctions.find { function ->
                    function.name == "versionName" &&
                            function.parameters.size == 2 // instance, appContext
                }
                if (versionNameMethod == null) {
                    throw NoSuchMethodException("versionName method not found in YoutubeDLUpdater")
                }
                // Pass the instance as the first argument
                versionNameMethod.call(instance, appContext) as? String
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    }

    val CHANNEL_STABLE = "STABLE"
    val CHANNEL_NIGHTLY = "NIGHTLY"
    val CHANNEL_MASTER = "MASTER"

}