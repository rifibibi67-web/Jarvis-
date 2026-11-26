package com.jarvis.app

import android.Manifest.permission.*
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("j", 0)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 120, 40, 40) }
        val key = EditText(this).apply { hint = "Claude API key (sk-ant-...)"; setText(p.getString("key", "")) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        l.addView(key)
        l.addView(btn("1. Permissions do") {
            requestPermissions(arrayOf(RECORD_AUDIO, SEND_SMS, CALL_PHONE, READ_CONTACTS, POST_NOTIFICATIONS), 1)
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        l.addView(btn("2. Accessibility ON karo (Jarvis)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        l.addView(btn("3. Jarvis START") {
            p.edit().putString("key", key.text.toString().trim()).apply()
            startForegroundService(Intent(this, JarvisService::class.java))
        })
        l.addView(btn("STOP") { stopService(Intent(this, JarvisService::class.java)) })
        setContentView(l)
    }
}
