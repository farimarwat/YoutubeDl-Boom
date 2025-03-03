package com.farimarwat.library

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

internal class StreamProcessExtractor {
    companion object{
        private val TAG = StreamProcessExtractor::class.java.simpleName
        private const val ETA: Long = -1
        private const val PERCENT = -1.0f
        private const val GROUP_PERCENT = 1
        private const val GROUP_MINUTES = 2
        private const val GROUP_SECONDS = 3
    }
    private val p = Pattern.compile("\\[download]\\s+(\\d+\\.\\d)% .* ETA (\\d+):(\\d+)")
    private val pAria2c =
        Pattern.compile("\\[#\\w{6}.*\\((\\d*\\.*\\d+)%\\).*?((\\d+)m)*((\\d+)s)*]")
    private var progress = PERCENT
    private var eta = ETA

    fun readStream(
        buffer: StringBuffer,
        stream: InputStream,
        callback: ((Float, Long, String) -> Unit)? = null
    ): Job = CoroutineScope(Dispatchers.IO).launch {
        try {
            InputStreamReader(stream, StandardCharsets.UTF_8).use { input ->
                val currentLine = StringBuilder()
                val charArray = CharArray(8192)
                var charsRead: Int
                while (input.read(charArray).also { charsRead = it } != -1) {
                    buffer.append(charArray, 0, charsRead)
                    for (i in 0 until charsRead) {
                        val nextChar = charArray[i]
                        if (nextChar == '\r' || nextChar == '\n') {
                            val line = currentLine.toString()
                            callback?.invoke(getProgress(line),getEta(line),line)
                            currentLine.setLength(0)
                        } else {
                            currentLine.append(nextChar)
                        }
                    }
                }
            }
        } catch(e: OutOfMemoryError){
            Timber.i(e)
        }
        catch (e:InterruptedIOException){
            Timber.i(e)
        }
        catch (e: Exception) {
            Timber.i("failed to read stream", e)
        }
    }

    private fun getProgress(line: String): Float {
        val matcher = p.matcher(line)
        if (matcher.find()) return matcher.group(GROUP_PERCENT).toFloat()
            .also { progress = it } else {
            val mAria2c = pAria2c.matcher(line)
            if (mAria2c.find()) return mAria2c.group(1).toFloat().also { progress = it }
        }
        return progress
    }

    private fun getEta(line: String): Long {
        val matcher = p.matcher(line)
        if (matcher.find()) return convertToSeconds(
            matcher.group(GROUP_MINUTES),
            matcher.group(GROUP_SECONDS)
        ).also { eta = it.toLong() }.toLong() else {
            val mAria2c = pAria2c.matcher(line)
            if (mAria2c.find()) return convertToSeconds(
                mAria2c.group(3),
                mAria2c.group(5)
            ).also { eta = it.toLong() }.toLong()
        }
        return eta
    }

    private fun convertToSeconds(minutes: String?, seconds: String?): Int {
        if (seconds == null) return 0 else if (minutes == null) return seconds.toInt()
        return minutes.toInt() * 60 + seconds.toInt()
    }

}