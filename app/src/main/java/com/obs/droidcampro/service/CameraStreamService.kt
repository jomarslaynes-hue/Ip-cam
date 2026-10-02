package com.obs.droidcampro.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.obs.droidcampro.MainActivity
import com.obs.droidcampro.R
import com.obs.droidcampro.audio.AudioStreamer
import com.obs.droidcampro.camera.CameraController
import com.obs.droidcampro.network.NetworkUtils
import com.obs.droidcampro.security.SecurityManager
import com.obs.droidcampro.server.HttpStreamServer

class CameraStreamService : LifecycleService() {

    private val binder = LocalBinder()
    val cameraController by lazy { CameraController(applicationContext) }
    val securityManager by lazy { SecurityManager(applicationContext) }
    val audioStreamer by lazy { AudioStreamer() }

    var server: HttpStreamServer? = null
        private set

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): CameraStreamService = this@CameraStreamService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire WakeLock & High-Performance Wi-Fi Lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DroidCamPro::WakeLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 12 hours max
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            },
            "DroidCamPro::WifiLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }

        // Initialize CameraController
        cameraController.initialize(this)

        // Initialize Server
        server = HttpStreamServer(applicationContext, cameraController, securityManager, 8080)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        server?.start()
        audioStreamer.start()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DroidCam Pro Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps camera and streaming server active in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val ip = NetworkUtils.getLocalIpAddress(applicationContext)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DroidCam Pro Live Streaming")
            .setContentText("OBS Stream URL: http://$ip:8080/stream")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioStreamer.stop()
        server?.stop()
        cameraController.shutdown()

        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        const val CHANNEL_ID = "droidcam_stream_channel"
        const val NOTIFICATION_ID = 1001
    }
}
