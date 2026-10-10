package com.example.aibot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private val PROJECTION_REQ = 1001

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

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), PROJECTION_REQ)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQ && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, BotService::class.java).apply {
                action = BotService.ACTION_START
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(i)
            findViewById<TextView>(R.id.statusText).text = "Bot RUNNING (widget dekho)"
        }
    }
}
