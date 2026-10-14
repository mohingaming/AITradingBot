package com.example.aibot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("bot", Context.MODE_PRIVATE)
        val input = findViewById<EditText>(R.id.apiKeyInput)
        val status = findViewById<TextView>(R.id.statusText)
        input.setText(prefs.getString("api_key", ""))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit().putString("api_key", input.text.toString().trim()).apply()
            status.text = "API key saved"
        }

        findViewById<View>(R.id.tileAuto).setOnClickListener {
            val nowOn = !prefs.getBoolean("bot_on", true)
            prefs.edit().putBoolean("bot_on", nowOn).apply()
            status.text = if (nowOn) "Bot ON — trading chart kholo" else "Bot OFF"
        }

        findViewById<View>(R.id.tileScreenshot).setOnClickListener {
            Toast.makeText(this, "Screenshot analysis: OCR mode e cholbe", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.tileFuture).setOnClickListener {
            Toast.makeText(this, "Future signal: same AI engine", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.tileCourse).setOnClickListener {
            Toast.makeText(this, "Trading course: Coming soon!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
