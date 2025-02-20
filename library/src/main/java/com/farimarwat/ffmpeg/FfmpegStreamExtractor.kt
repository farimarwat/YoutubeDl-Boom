package com.farimarwat.ffmpeg

import com.yausername.youtubedl_android.ProcessUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.Thread.sleep

object FfmpegStreamExtractor{
    private var mFfmpegPid = 0
    private var shouldStop = false
     fun readStream(process:Process, progressCallBack: ((Float, Long, String) -> Unit)? = null):Job =
         CoroutineScope(Dispatchers.IO).launch {
        try {
            val pythonPID = ProcessUtils.getPythonProcessId(process)
            var line: String?
            var ffmpegInitiated = false
            while (!shouldStop) {
                mFfmpegPid = ProcessUtils.getFFMPEGProcessId(pythonPID)
                val progressFilePath = "/proc/$mFfmpegPid/fd/2"
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
                if(ffmpegInitiated && mFfmpegPid == -1){
                    shouldStop = true
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun stopNow(process: Process) {
        val pythonPID = ProcessUtils.getPythonProcessId(process)
        val ffmpegid = ProcessUtils.getFFMPEGProcessId(pythonPID)
        ProcessUtils.killProcess(ffmpegid)
        process.destroy()
        shouldStop = true
    }
    fun hasFfmpegProcess(process: Process):Boolean{
        val pythonPID = ProcessUtils.getPythonProcessId(process)
        val ffmpegid = ProcessUtils.getFFMPEGProcessId(pythonPID)
        return if(ffmpegid > 0) true else false
    }

}