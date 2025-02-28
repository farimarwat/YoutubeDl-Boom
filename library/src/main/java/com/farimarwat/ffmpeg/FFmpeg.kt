package com.farimarwat.ffmpeg

import android.content.Context
import com.farimarwat.common.SharedPrefsHelper
import com.farimarwat.common.SharedPrefsHelper.update
import com.farimarwat.common.utils.ZipUtils.unzip
import com.farimarwat.downloadmanager.YoutubeDlFileManager
import com.farimarwat.library.YoutubeDLException

import org.apache.commons.io.FileUtils
import java.io.File

object FFmpeg {
    private var initialized = false
    private var binDir: File? = null

    @Synchronized
    fun init(appContext: Context) {
        if (initialized) return
        val baseDir = File(appContext.noBackupFilesDir, baseName)
        if (!baseDir.exists()) baseDir.mkdir()
        binDir = YoutubeDlFileManager.DOWNLOAD_DIR
        val packagesDir = File(baseDir, packagesRoot)
        val ffmpegDir = File(packagesDir, ffmegDirName)
        initFFmpeg(appContext, ffmpegDir)
        initialized = true
    }

    private fun initFFmpeg(appContext: Context, ffmpegDir: File) {
        val ffmpegLib = File(binDir, ffmpegLibName)
        // using size of lib as version
        val ffmpegSize = ffmpegLib.length().toString()
        if (!ffmpegDir.exists() || shouldUpdateFFmpeg(appContext, ffmpegSize)) {
            FileUtils.deleteQuietly(ffmpegDir)
            ffmpegDir.mkdirs()
            try {
                unzip(ffmpegLib, ffmpegDir)
            } catch (e: Exception) {
                FileUtils.deleteQuietly(ffmpegDir)
                throw YoutubeDLException("failed to initialize", e)
            }
            updateFFmpeg(appContext, ffmpegSize)
        }
    }

    private fun shouldUpdateFFmpeg(appContext: Context, version: String): Boolean {
        return version != SharedPrefsHelper[appContext, ffmpegLibVersion]
    }

    private fun updateFFmpeg(appContext: Context, version: String) {
        update(appContext, ffmpegLibVersion, version)
    }

    @JvmStatic
    fun getInstance() = this
    private const val baseName = "youtubedl-android"
    private const val packagesRoot = "packages"
    private const val ffmegDirName = "ffmpeg"
    private const val ffmpegLibName = "libffmpeg.zip.so"
    private const val ffmpegLibVersion = "ffmpegLibVersion"

}