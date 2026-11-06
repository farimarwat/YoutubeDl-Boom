package com.farimarwat.library

import android.content.Context
import com.farimarwat.common.SharedPrefsHelper
import com.farimarwat.commons.UpdateChannel
import com.farimarwat.commons.UpdateStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.farimarwat.common.utils.ZipUtils
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
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
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    @Throws(IOException::class, YoutubeDLException::class)
    internal fun update(
        appContext: Context?,
        youtubeDLChannel: UpdateChannel = UpdateChannel.STABLE
    ): UpdateStatus {
        val json = checkForUpdate(appContext!!, youtubeDLChannel)
            ?: return UpdateStatus.ALREADY_UP_TO_DATE
        val downloadUrl = getDownloadUrl(json)
        val file = download(appContext, downloadUrl)
        val ytdlpDir = getYoutubeDLDir(
            appContext
        )
        val binary = File(ytdlpDir, dlpBinaryName)
        try {
            /* purge older version */
            if (ytdlpDir.exists()) ZipUtils.deleteQuietly(ytdlpDir)
            /* install newer version */ytdlpDir.mkdirs()
            file.copyTo(binary, overwrite = true)
        } catch (e: Exception) {
            /* if something went wrong restore default version */
            ZipUtils.deleteQuietly(ytdlpDir)
            YoutubeDL.getInstance().init_ytdlp(appContext,ytdlpDir)
            YoutubeDL.prepareYtdlp()
            throw YoutubeDLException(e)
        } finally {
            file.delete()
        }
        updateSharedPrefs(appContext, getTag(json), getName(json))
        YoutubeDL.prepareYtdlp()
        return UpdateStatus.DONE
    }

    internal fun saveVersion(appContext: Context, tag: String, name: String) =
        updateSharedPrefs(appContext, tag, name)

    private fun updateSharedPrefs(appContext: Context, tag: String, name: String) {
        SharedPrefsHelper.update(appContext, dlpVersionKey, tag)
        SharedPrefsHelper.update(appContext, dlpVersionNameKey, name)
    }

    @Throws(IOException::class)
    private fun checkForUpdate(appContext: Context, youtubeDLChannel: UpdateChannel): JsonNode? {
        val json = readJson(youtubeDLChannel.apiUrl)
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

    suspend fun getYtdDownloadUrl(youtubeDLChannel: UpdateChannel):String {
        return withContext(Dispatchers.IO){
            getDownloadUrl(readJson(youtubeDLChannel.apiUrl))
        }

    }

    @Throws(IOException::class)
    private fun download(appContext: Context, url: String): File {
        val file = File.createTempFile(dlpBinaryName, null, appContext.cacheDir)
        try {
            openConnection(url).inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        return file
    }

    /** objectMapper.readTree(URL) had no timeouts: on a bad network the update hung forever. */
    @Throws(IOException::class)
    private fun readJson(url: String): JsonNode =
        openConnection(url).inputStream.use { YoutubeDL.objectMapper.readTree(it) }

    @Throws(IOException::class)
    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
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