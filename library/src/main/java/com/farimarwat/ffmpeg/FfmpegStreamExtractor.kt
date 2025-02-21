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

class FfmpegStreamExtractor{

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
                        progressCallBack?.let { it(0f  ,0L,line.toString())}
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