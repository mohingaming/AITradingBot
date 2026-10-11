package com.example.aibot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("bot", Context.MODE_PRIVATE)
        val input = findViewById<EditText>(R.id.apiKeyInput)
        val status = findViewById<TextView>(R.id.statusText)
        val toggle = findViewById<ToggleButton>(R.id.botToggle)
        input.setText(prefs.getString("api_key", ""))
        toggle.isChecked = prefs.getBoolean("bot_on", true)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit().putString("api_key", input.text.toString().trim()).apply()
            status.text = "API key saved"
        }

        toggle.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            prefs.edit().putBoolean("bot_on", isChecked).apply()
            status.text = if (isChecked) "Bot ON" else "Bot OFF"
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
