package com.farimarwat.downloadmanager

import android.content.Context
import android.os.Build
import com.farimarwat.downloadmanager.model.YoutubeDlArtifact
import com.farimarwat.library.YoutubeDL
import com.farimarwat.library.YoutubeDLUpdater
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * `YoutubeDlFileManager` is responsible for dynamically managing and downloading the required
 * external dependencies for YouTube-DL on Android. This helps minimize the APK size by
 * fetching only necessary libraries based on the device's architecture.
 */
object YoutubeDlFileManager {
    private const val TAG = "NativeLibManager"

    /** Indicates whether FFmpeg support is enabled. */
    private var mWithFfmpeg = false

    /** Indicates whether Aria2c support is enabled. */
    private var mWithAria2c = false

    /** List of base URLs for downloading libraries, with `{arch}` placeholder for architecture. */
    private val baseUrls = mutableListOf(
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/library/src/main/jniLibs/{arch}/libpython.zip.so"
    )

    /** Directory where downloaded files will be stored. */
    var DOWNLOAD_DIR: File? = null

    var CACHE_DIR:File? = null

    /** Retrieves the primary supported architecture of the device. */
    val arch: String
        get() = Build.SUPPORTED_ABIS[0]

    /**
     * Checks if FFMPEG support is enabled.
     *
     * @return `true` if FFMPEG is enabled, otherwise `false`.
     */
    fun isFfmpegEnabled(): Boolean {
        return mWithFfmpeg
    }

    /**
     * Checks if Aria2c support is enabled.
     *
     * @return `true` if Aria2c is enabled, otherwise `false`.
     */
    fun isAria2cEnabled(): Boolean {
        return mWithAria2c
    }

    /**
     * Checks if all necessary library files exist in the download directory.
     *
     * @param context The application context.
     * @return `true` if all required files are present, `false` otherwise.
     */
    suspend fun isReady(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            DOWNLOAD_DIR = context.filesDir
            CACHE_DIR = context.cacheDir
            baseUrls.add(YoutubeDLUpdater.getYtdDownloadUrl(YoutubeDL.UpdateChannel.STABLE))
            baseUrls.all { url ->
                val fileUrl = url.replace("{arch}", arch)
                val fileName = fileUrl.substringAfterLast('/')
                File(DOWNLOAD_DIR, fileName).exists()
            }
        }
    }

    /**
     * Downloads all required library files if they are not already present.
     *
     * @param callback A lambda function that provides a success flag (`true` if successful,
     * `false` otherwise) and an optional exception if an error occurs.
     */
    suspend fun downloadLibFiles(
        callback: suspend (ready: Boolean, error: Exception?) -> Unit = { _, _ -> }
    ) {
        withContext(Dispatchers.IO) {
            var downloadError: Exception? = null
            var downloadFailed = false
            for (url in baseUrls) {
                val fileUrl = url.replace("{arch}", arch)
                val fileName = fileUrl.substringAfterLast('/')
                val tempFile = File(CACHE_DIR, fileName)
                val destinationFile = File(DOWNLOAD_DIR,fileName)
                Timber.i("Downloading $fileName for $arch")
                if (!destinationFile.exists()) {
                    val success = downloadFile(fileUrl, tempFile)
                    if (!success) {
                        downloadFailed = true
                        downloadError = Exception("Download failed for $fileName")
                        break
                    }
                }

                copyFile(tempFile,destinationFile)
            }
            callback(!downloadFailed, downloadError)
        }
    }

    private fun copyFile(source: File, destinationFile: File) {
        source.inputStream().use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Downloads a single file from the given URL.
     *
     * @param fileUrl The URL of the file to download.
     * @param destinationFile The file where the downloaded data will be saved.
     * @return `true` if the download was successful, `false` otherwise.
     */
    private suspend fun downloadFile(fileUrl: String, destinationFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val outputStream = FileOutputStream(destinationFile)

                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    true
                } else {
                    Timber.i("Failed to download $fileUrl. Response code: ${connection.responseCode}")
                    false
                }
            } catch (e: Exception) {
                Timber.i("Error downloading $fileUrl: $e")
                false
            }
        }
    }

    /**
     * Initializes and returns an instance of `YoutubeDlFileManager` with optional FFmpeg
     * and Aria2c support.
     *
     * @param ffmpeg Enables FFmpeg support if `true`.
     * @param aria2c Enables Aria2c support if `true`.
     * @return `YoutubeDlFileManager` instance with the specified configuration.
     */
    private fun getInstance(ffmpeg: Boolean = false, aria2c: Boolean = false): YoutubeDlFileManager {
        mWithFfmpeg = ffmpeg
        mWithAria2c = aria2c
        return this
    }

    /**
     * Builder class for configuring and constructing an instance of `YoutubeDlFileManager`.
     */
    class Builder {
        private var withFfmpeg = false
        private var withAria2c = false

        /**
         * Adds FFmpeg support.
         *
         * @return `Builder` instance for chaining.
         */
        fun withFFMpeg(): Builder {
            baseUrls.add(YoutubeDlArtifact.FFMPEG)
            withFfmpeg = true
            return this
        }

        /**
         * Adds Aria2c support.
         *
         * @return `Builder` instance for chaining.
         */
        fun withAria2c(): Builder {
            baseUrls.add(YoutubeDlArtifact.ARIA2C)
            withAria2c = true
            return this
        }

        /**
         * Builds and returns a configured `YoutubeDlFileManager` instance.
         *
         * @return The configured `YoutubeDlFileManager` instance.
         */
        fun build(): YoutubeDlFileManager {
            return getInstance(withFfmpeg, withAria2c)
        }
    }
}