package com.farimarwat.common.utils

import android.system.Os
import android.system.OsConstants
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ZipUtils {
    fun unzip(sourceFile: File?, targetDirectory: File) {
        ZipFile(sourceFile).use { zipFile ->
            val entries = zipFile.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryDestination = File(targetDirectory, entry.name)
                // prevent zipSlip
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + entry.name)
                }
                if (entry.isDirectory) {
                    entryDestination.mkdirs()
                } else if (entry.isUnixSymlink) {
                    zipFile.getInputStream(entry).use { `in` ->
                        val symlink = `in`.readBytes().toString(StandardCharsets.UTF_8)
                        Os.symlink(symlink, entryDestination.absolutePath)
                    }
                } else {
                    entryDestination.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { `in` ->
                        FileOutputStream(entryDestination).use { out -> `in`.copyTo(out, 64 * 1024) }
                    }
                }
            }
        }
    }

    fun unzip(inputStream: InputStream?, targetDirectory: File) {
        ZipArchiveInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipArchiveEntry? = null
            while (zis.nextZipEntry.also { entry = it } != null) {
                val entryDestination = File(targetDirectory, entry!!.name)
                // prevent zipSlip
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + entry!!.name)
                }
                if (entry!!.isDirectory) {
                    entryDestination.mkdirs()
                } else {
                    entryDestination.parentFile?.mkdirs()
                    FileOutputStream(entryDestination).use { out -> zis.copyTo(out, 64 * 1024) }
                }
            }
        }
    }

    fun unzip(url: String, targetDir: File) {
        val connection = URL(url).openConnection()
        connection.getInputStream().use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                var entry: ZipEntry?
                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    val file = File(targetDir, entry!!.name)
                    if (!file.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                        throw IllegalAccessException("Entry is outside of the target dir: " + entry!!.name)
                    }
                    if (entry!!.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    /**
     * Deletes [file] and everything under it, ignoring failures. Symlinks are removed, never
     * followed (the bundles contain symlinks, and Kotlin's deleteRecursively follows them).
     */
    internal fun deleteQuietly(file: File?) {
        if (file == null) return
        try {
            if (!isSymlink(file)) file.listFiles()?.forEach { deleteQuietly(it) }
            file.delete()
        } catch (_: Exception) {
        }
    }

    private fun isSymlink(file: File): Boolean = try {
        OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
    } catch (_: Exception) {
        false
    }
}
