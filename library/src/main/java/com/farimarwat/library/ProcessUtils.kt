package com.yausername.youtubedl_android

import android.util.Log
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessUtils {
    fun extractSize(line: String?): Int? {
        return line?.let {
            val pattern = Regex("""size\s*=\s*(\d+)kB""")
            val matchResult = pattern.find(line)
            val sizeValue = matchResult?.groupValues?.getOrNull(1)
            sizeValue?.toIntOrNull()
        }
    }
    fun getPythonProcessId(process: Process): Int {
        return try{
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        }catch (ex:Exception){
            ex.printStackTrace()
            -1
        }
    }

    fun getFFMPEGProcessId(pythonPID: Int): Int {
        return try {
            val command = "ps --ppid $pythonPID | awk 'NR > 1 {print \$2}'"
            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val pidLine = reader.readLine()
            if (pidLine != null) {
                Timber.i( "FFMPEG Line: $pidLine")
                val pid = pidLine.trim().toIntOrNull()
                Timber.i("FFMPEG Line: PID: $pid")
                return pid ?: -1
            }

            val exitCode = process.waitFor()
            Timber.i("Process exited with code: $exitCode")
            -1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }


    //Kill that specific ffmpeg process
    fun killChildProcess(process: Process) {
        val pythonId = getPythonProcessId(process)
        val ffmpegId = getFFMPEGProcessId(pythonId)
        android.os.Process.killProcess(ffmpegId)
    }
    fun killProcess(pid:Int) {
        android.os.Process.killProcess(pid)
    }
}