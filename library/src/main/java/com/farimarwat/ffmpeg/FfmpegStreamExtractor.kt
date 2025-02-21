package com.farimarwat.ffmpeg

import com.yausername.youtubedl_android.ProcessUtils
import com.yausername.youtubedl_android.getChildProcessId
import com.yausername.youtubedl_android.getProcessId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.Thread.sleep

/**
 * Extracts and reads the FFmpeg stream output for monitoring progress.
 *
 * This class is responsible for extracting the FFmpeg stream output by tracking its parent Python process ID
 * and reading the progress logs. It provides a coroutine-based approach to continuously monitor
 * FFmpeg execution and notify a callback with the latest progress details.
 *
 * This is specifically designed for use with yt-dlp, where FFmpeg is spawned as a child process.
 */
internal class FfmpegStreamExtractor {

    /**
     * Reads the FFmpeg process output stream and extracts progress details.
     *
     * This function launches a coroutine in the IO dispatcher to monitor the FFmpeg process,
     * which is a child of the Python process running yt-dlp. It reads the progress output file descriptor
     * (`/proc/<pid>/fd/2`) and extracts useful information. If progress is detected, it invokes the provided callback.
     *
     * @param process The Python process that spawns FFmpeg.
     * @param progressCallBack A lambda function that receives progress updates.
     * It provides:
     * - A float representing progress percentage (currently set to -1f as a placeholder).
     * - A long representing the extracted size (currently set to -1L as a placeholder).
     * - A string containing the raw FFmpeg output line.
     * @return A [Job] representing the coroutine that monitors the process output.
     */

     fun readStream(process:Process, progressCallBack: ((Float, Long, String) -> Unit)? = null):Job =
         CoroutineScope(Dispatchers.IO).launch {
        try {
            val pythonPID = process.getProcessId()
            var ffmpegId:Int
            var line: String?
            var ffmpegInitiated = false
            var shouldStop = false
            while (!shouldStop) {
                ffmpegId = pythonPID.getChildProcessId()
                val progressFilePath = "/proc/$ffmpegId/fd/2"
                val progressFile = File(progressFilePath)
                if (progressFile.exists()) {
                    ffmpegInitiated = true
                    val inputStream = FileInputStream(progressFile)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    while (reader.readLine().also { line = it } != null) {
                        val size = ProcessUtils.extractSize(line)
                        progressCallBack?.let { it(-1f  ,-1L,line.toString())}
                    }
                }
                sleep(1000)
                if(ffmpegInitiated && ffmpegId == -1){
                    shouldStop = true
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}