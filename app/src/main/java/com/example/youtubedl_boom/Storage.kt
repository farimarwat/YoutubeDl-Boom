package com.example.youtubedl_boom

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * yt-dlp and ffmpeg are separate processes that write by file path (plus temp and merge files),
 * so they download into the app's own folder, which needs no permission on any version.
 * The finished file is then copied to Downloads/youtubeDl-boom:
 * - Android 10+: through MediaStore, no permission.
 * - Android 9 and older: as a plain file, needs WRITE_EXTERNAL_STORAGE.
 */
object Storage {
    const val FOLDER = "youtubeDl-boom"

    val permission: String? =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) Manifest.permission.WRITE_EXTERNAL_STORAGE else null

    fun hasPermission(context: Context): Boolean = permission == null ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** A new empty folder for one download; whatever yt-dlp leaves in it is the result. */
    fun newWorkDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(root, "job-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    /** Copies [file] into Downloads/youtubeDl-boom and deletes it; returns a content Uri to open it. */
    suspend fun publish(context: Context, file: File): Uri? {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                dir.mkdirs()
                val target = uniqueFile(dir, file.name)
                file.copyTo(target)
                scan(context, target, mime)
            }
        } finally {
            file.delete()
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            target = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}")
            n++
        }
        return target
    }

    /** Registers the file with MediaStore (so other apps see it) and returns its content Uri. */
    private suspend fun scan(context: Context, file: File, mime: String): Uri? =
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime)) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
}
