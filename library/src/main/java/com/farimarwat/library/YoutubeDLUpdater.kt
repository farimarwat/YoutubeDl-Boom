package com.farimarwat.library

import android.content.Context
import com.farimarwat.common.SharedPrefsHelper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.URL

internal object YoutubeDLUpdater {
    private const val youtubeDLStableChannelUrl =
        "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    private const val youtubeDLNightlyChannelUrl =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"
    private const val youtubeDLMasterChannelUrl =
        "https://api.github.com/repos/yt-dlp/yt-dlp-master-builds/releases/latest"
    private const val dlpBinaryName = "yt-dlp"
    private const val dlpVersionKey = "dlpVersion"
    private const val dlpVersionNameKey = "dlpVersionName"

    @Throws(IOException::class, YoutubeDLException::class)
    internal fun update(
        appContext: Context?,
        youtubeDLChannel: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel.STABLE
    ): YoutubeDL.UpdateStatus {
        val json = checkForUpdate(appContext!!, youtubeDLChannel)
            ?: return YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE
        val downloadUrl = getDownloadUrl(json)
        val file = download(appContext, downloadUrl)
        val ytdlpDir = getYoutubeDLDir(
            appContext
        )
        val binary = File(ytdlpDir, dlpBinaryName)
        try {
            /* purge older version */
            if (ytdlpDir.exists()) FileUtils.deleteDirectory(ytdlpDir)
            /* install newer version */ytdlpDir.mkdirs()
            FileUtils.copyFile(file, binary)
        } catch (e: Exception) {
            /* if something went wrong restore default version */
            FileUtils.deleteQuietly(ytdlpDir)
            YoutubeDL.getInstance().init_ytdlp(ytdlpDir)
            throw YoutubeDLException(e)
        } finally {
            file.delete()
        }
        updateSharedPrefs(appContext, getTag(json), getName(json))
        return YoutubeDL.UpdateStatus.DONE
    }

    private fun updateSharedPrefs(appContext: Context, tag: String, name: String) {
        SharedPrefsHelper.update(appContext, dlpVersionKey, tag)
        SharedPrefsHelper.update(appContext, dlpVersionNameKey, name)
    }

    @Throws(IOException::class)
    private fun checkForUpdate(appContext: Context, youtubeDLChannel: YoutubeDL.UpdateChannel): JsonNode? {
        val url = URL(youtubeDLChannel.apiUrl)
        val json = YoutubeDL.objectMapper.readTree(url)
        val newVersion = getTag(json)
        val oldVersion = SharedPrefsHelper[appContext, dlpVersionKey]
        return if (newVersion == oldVersion) {
            null
        } else json
    }


    private fun getTag(json: JsonNode): String {
        return json["tag_name"].asText()
    }

    private fun getName(json: JsonNode): String {
        return json["name"].asText()
    }

    @Throws(YoutubeDLException::class)
    private fun getDownloadUrl(json: JsonNode): String {
        val assets = json["assets"] as ArrayNode
        var downloadUrl = ""
        for (asset in assets) {
            if (YoutubeDL.ytdlpBin == asset["name"].asText()) {
                downloadUrl = asset["browser_download_url"].asText()
                break
            }
        }
        if (downloadUrl.isEmpty()) throw YoutubeDLException("unable to get download url")
        return downloadUrl
    }

    suspend fun getYtdDownloadUrl(youtubeDLChannel: YoutubeDL.UpdateChannel):String {
        return withContext(Dispatchers.IO){
            val url = URL(youtubeDLChannel.apiUrl)
            getDownloadUrl(YoutubeDL.objectMapper.readTree(url))
        }

    }

    @Throws(IOException::class)
    private fun download(appContext: Context, url: String): File {
        val downloadUrl = URL(url)
        val file = File.createTempFile(dlpBinaryName, null, appContext.cacheDir)
        FileUtils.copyURLToFile(downloadUrl, file, 5000, 10000)
        return file
    }

    private fun getYoutubeDLDir(appContext: Context): File {
        val baseDir = File(appContext.noBackupFilesDir, YoutubeDL.baseName)
        return File(baseDir, YoutubeDL.ytdlpDirName)
    }

    fun version(appContext: Context?): String? {
        return SharedPrefsHelper[appContext!!, dlpVersionKey]
    }

    fun versionName(appContext: Context?): String? {
        return SharedPrefsHelper[appContext!!, dlpVersionNameKey]
    }
}