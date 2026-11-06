package com.yausername.youtubedl_android

import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessUtils {
    private val sizePattern = Regex("""size\s*=\s*(\d+)kB""")

    fun extractSize(line: String?): Int? {
        return line?.let {
            val matchResult = sizePattern.find(line)
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
        Timber.i(ex)
        -1
    }
}
fun Int.getChildProcessId(): Int {
    if (this <= 0) return -1
    var process: Process? = null
    return try {
        val command = "ps --ppid $this | awk 'NR > 1 {print \$2}'"
        process = ProcessBuilder("/system/bin/sh", "-c", command).start()
        val pidLine = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
        process.waitFor()
        pidLine?.trim()?.toIntOrNull() ?: -1
    } catch (e: Exception) {
        Timber.i(e)
        -1
    } finally {
        // Reap the shell and close its pipes; otherwise every call leaves a zombie and open fds.
        process?.destroy()
    }
}
fun Int.killProcess() {
    // -1 means "not found"; never let it reach kill(2), where -1 means every process we can signal.
    if (this > 0) android.os.Process.killProcess(this)
}