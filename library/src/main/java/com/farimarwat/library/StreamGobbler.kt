package com.farimarwat.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal class StreamGobbler {
    fun readStream(buffer: StringBuffer, stream: InputStream): Job = CoroutineScope(Dispatchers.IO).launch {
        try {
            InputStreamReader(stream, StandardCharsets.UTF_8).use { input ->
                val chars = CharArray(8192)
                var count: Int
                while (input.read(chars).also { count = it } != -1) {
                    buffer.append(chars, 0, count)
                }
            }
        } catch (e: IOException) {
            Timber.i("Failed to read stream: $e")
        }
    }
}
