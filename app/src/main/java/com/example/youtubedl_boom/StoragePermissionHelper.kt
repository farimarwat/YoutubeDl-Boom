package com.example.youtubedl_boom

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

object StoragePermissionHelper {
    private const val REQUEST_CODE = 1001

    val downloadDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "youtubeDl-boom"
    ).apply {
        if (!exists()) mkdirs()
    }

    fun checkAndRequestStoragePermission(activity: Activity): Boolean {
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        return if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), REQUEST_CODE)
            false
        }
    }
}
