package com.farimarwat.library.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.farimarwat.downloadmanager.YoutubeDlFileManager
import com.farimarwat.library.R
import com.farimarwat.library.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber


class YoutubeDlService:Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    var withFfmpeg = false
    var withAria2c = false

    private var _youtubeDl:MutableStateFlow<YoutubeDL?> = MutableStateFlow(null)
    var youtubeDl = _youtubeDl.asStateFlow()
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        withFfmpeg = intent?.getBooleanExtra(EXTRA_PARAM_WITH_FFMPEG,false) ?: false
        withAria2c = intent?.getBooleanExtra(EXTRA_PARAM_WITH_ARIA2C,false) ?: false
         super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            1,
            createNotification(),
           FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        initializeYoutubeDl()
        return START_STICKY
    }

    inner class LocalBinder:Binder(){
        fun getService():YoutubeDlService = this@YoutubeDlService
    }
    private val binder = LocalBinder()
    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "download_channel")
            .setContentTitle(application.getString(R.string.preparing_files))
            .setSmallIcon(R.drawable.baseline_download_for_offline_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "download_channel",
                "Filter Download",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    fun initializeYoutubeDl(){
        val manager = YoutubeDlFileManager
            .Builder().apply {
                if(withFfmpeg) withFFMpeg()
                if(withAria2c) withAria2c()
            }
            .build()

        YoutubeDL.getInstance().init(
            appContext = this,
            fileManager = manager,
            onSuccess = {
                Timber.i("Initialized successfully")
                _youtubeDl.value = it
                stopSelf()
            },
            onError = {
                Timber.e(it)
                stopSelf()
            }
        )
    }

    companion object{
        val EXTRA_PARAM_WITH_FFMPEG = "with_ffmpeg"
        val EXTRA_PARAM_WITH_ARIA2C = "with_aria2c"
    }
}