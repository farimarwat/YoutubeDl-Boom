package com.yausername.youtubedl_common.downloadmanager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object NativeLibManager {
    private const val TAG = "NativeLibManager"
    private val supportedArchitectures = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    private val baseUrls = listOf(
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/library/src/main/jniLibs/arm64-v8a/libpython.zip.so",
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/ffmpeg/src/main/jniLibs/arm64-v8a/libffmpeg.zip.so",
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/aria2c/src/main/jniLibs/{arch}/libaria2c.zip.so",
    )
    var DOWNLOAD_DIR:File? = null

    // Check if all necessary files exist in the libs directory
    fun isReady(context: Context): Boolean {
        DOWNLOAD_DIR = context.filesDir
        return supportedArchitectures.all { arch ->
            baseUrls.all { url ->
                val fileUrl = url.replace("{arch}", arch)
                val fileName = fileUrl.substringAfterLast('/')
                File(DOWNLOAD_DIR, fileName).exists()
            }
        }
    }

    // Download all files (replace {arch} with the correct architecture for each)
    fun downloadLibFiles(
        context: Context,
        callback: (ready: Boolean, error: Exception?) -> Unit = { _, _ -> }
    ) {
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            var downloadError: Exception? = null
            var downloadFailed = false

            for (arch in supportedArchitectures) {
                for (url in baseUrls) {
                    val fileUrl = url.replace("{arch}", arch)
                    val fileName = fileUrl.substringAfterLast('/')
                    val file = File(DOWNLOAD_DIR, fileName)

                    if (!file.exists()) {
                        Timber.i("Downloading $fileName for architecture $arch...")
                        val success = downloadFile(fileUrl, file)
                        if (!success) {
                            downloadFailed = true
                            downloadError = Exception("Download failed for $fileName")
                            break
                        }
                    }
                }
                if (downloadFailed) break
            }

            withContext(Dispatchers.Main) {
                callback(!downloadFailed, downloadError)
            }
        }
    }

    // Download a single file
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
                    Timber.i("DownloadResponse: ${connection.responseCode}")

                    // Set chmod +x if the file is libpython.so
                    if (destinationFile.name == "libpython.so") {
                        destinationFile.setExecutable(true, false)
                        Timber.i("Set executable permission for ${destinationFile.name}")
                    }

                    true
                } else {
                    Timber.i("Failed to download $fileUrl. Response code: ${connection.responseCode}")
                    false
                }
            } catch (e: Exception) {
                Timber.i("Error downloading $fileUrl $e")
                false
            }
        }
    }
}