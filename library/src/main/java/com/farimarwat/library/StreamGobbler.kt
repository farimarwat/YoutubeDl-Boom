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
import java.io.Reader
import java.nio.charset.StandardCharsets

internal object StreamGobbler{
    private lateinit var buffer: StringBuffer
    private lateinit var stream: InputStream
    private val TAG = StreamGobbler::class.java.simpleName
    fun readStream(buffer:StringBuffer,stream: InputStream):Job = CoroutineScope(Dispatchers.IO).launch {
        this@StreamGobbler.buffer = buffer
        this@StreamGobbler.stream = stream
        try {
            val `in`: Reader = InputStreamReader(stream, StandardCharsets.UTF_8)
            var nextChar: Int
            while (`in`.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
            }
        } catch (e: IOException) {
           Timber.i("Faile to read stream: ${e}")
        }
    }
}