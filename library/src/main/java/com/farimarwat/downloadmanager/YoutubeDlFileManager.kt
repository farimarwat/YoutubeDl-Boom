package com.farimarwat.downloadmanager

import android.content.Context
import android.os.Build
import com.farimarwat.downloadmanager.model.YoutubeDlArtifact
import com.farimarwat.ffmpeg.FFmpeg
import com.farimarwat.library.YoutubeDL
import com.farimarwat.library.YoutubeDLUpdater
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object YoutubeDlFileManager {
    private const val TAG = "NativeLibManager"
    var mWithFfmpeg = false
    var mWithAria2c = false
    private val baseUrls = mutableListOf(
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/library/src/main/jniLibs/{arch}/libpython.zip.so"
    )
    var DOWNLOAD_DIR:File? = null
    val arch:String
        get() =  Build.SUPPORTED_ABIS[0]

    // Check if all necessary files exist in the libs directory
    suspend fun isReady(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            DOWNLOAD_DIR = context.filesDir
            baseUrls.add(YoutubeDLUpdater.getYtdDownloadUrl(YoutubeDL.UpdateChannel.STABLE))
            baseUrls.all { url ->
                val fileUrl = url.replace("{arch}", arch)
                val fileName = fileUrl.substringAfterLast('/')
                File(DOWNLOAD_DIR, fileName).exists()
            }
        }
    }

    // Download all files (replace {arch} with the correct architecture for each)
    suspend fun downloadLibFiles(
         callback: suspend (ready: Boolean, error: Exception?) -> Unit = { _, _ -> }
    ) {
        withContext(Dispatchers.IO){
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
            callback(!downloadFailed, downloadError)
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
    private fun getInstance(ffmpeg:Boolean = false, aria2c:Boolean = false):YoutubeDlFileManager{
        mWithFfmpeg = ffmpeg
        mWithAria2c = aria2c
        return this
    }
    class Builder{
        var withFfmpeg = false
        var withAria2c = false
        fun withFFMpeg():Builder{
            baseUrls.add(YoutubeDlArtifact.FFMPEG)
            withFfmpeg = true
            return this
        }
        fun withAria2c():Builder{
            baseUrls.add(YoutubeDlArtifact.ARIA2C)
            withAria2c = true
            return this
        }
        fun build():YoutubeDlFileManager{
            return getInstance(withFfmpeg,withAria2c)
        }
    }
}