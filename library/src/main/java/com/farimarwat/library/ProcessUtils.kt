package com.yausername.youtubedl_android

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
}
fun Process.getProcessId(): Int {
    return try{
        val field = this.javaClass.getDeclaredField("pid")
        field.isAccessible = true
        field.getInt(this)
    }catch (ex:Exception){
        ex.printStackTrace()
        -1
    }
}
fun Int.getChildProcessId(): Int {
    return try {
        val command = "ps --ppid $this | awk 'NR > 1 {print \$2}'"
        val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val pidLine = reader.readLine()
        if (pidLine != null) {
            val pid = pidLine.trim().toIntOrNull()
            return pid ?: -1
        }

        val exitCode = process.waitFor()
        Timber.i("No ffmpeg process found")
        -1
    } catch (e: Exception) {
        e.printStackTrace()
        -1
    }
}
fun Int.killProcess() {
    android.os.Process.killProcess(this)
}