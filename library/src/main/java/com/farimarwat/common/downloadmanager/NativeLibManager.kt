package com.farimarwat.common.downloadmanager

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object NativeLibManager {
    private const val TAG = "NativeLibManager"

    private val baseUrls = listOf(
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/library/src/main/jniLibs/{arch}/libpython.zip.so",
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/ffmpeg/src/main/jniLibs/{arch}/libffmpeg.zip.so",
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/aria2c/src/main/jniLibs/{arch}/libaria2c.zip.so",
    )
    var DOWNLOAD_DIR:File? = null
    val arch:String
        get() =  Build.SUPPORTED_ABIS[0]

    // Check if all necessary files exist in the libs directory
    fun isReady(context: Context): Boolean {
        DOWNLOAD_DIR = context.filesDir
        return baseUrls.all { url ->
            val fileUrl = url.replace("{arch}", arch)
            val fileName = fileUrl.substringAfterLast('/')
            File(DOWNLOAD_DIR, fileName).exists()
        }
    }

    // Download all files (replace {arch} with the correct architecture for each)
    fun downloadLibFiles(
        callback: (ready: Boolean, error: Exception?) -> Unit = { _, _ -> }
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            var downloadError: Exception? = null
            var downloadFailed = false
            for (url in baseUrls) {
                val fileUrl = url.replace("{arch}", arch)
                val fileName = fileUrl.substringAfterLast('/')
                val file = File(DOWNLOAD_DIR, fileName)
                Timber.i("Download ${fileName} for $arch")
                if (!file.exists()) {
                    val success = downloadFile(fileUrl, file)
                    if (!success) {
                        downloadFailed = true
                        downloadError = Exception("Download failed for $fileName")
                        break
                    }
                }
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