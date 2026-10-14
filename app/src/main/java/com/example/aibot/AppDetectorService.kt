package com.example.aibot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@SuppressLint("NewApi")
class AppDetectorService : AccessibilityService() {

    companion object {
        @Volatile var tradingBoxVisible = false
        @Volatile var currentApp = ""
        private val MARKERS = listOf("Payout", "Investment", "Buy", "Sell")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayStatus: TextView? = null
    private var overlayTimer: TextView? = null
    private var overlaySignal: TextView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var bangla = false
    private var wasVisible = false
    private var lastSignal = ""
    private var seconds = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val bn = Locale("bn", "BD")
                val ok = tts?.isLanguageAvailable(bn)
                bangla = ok == TextToSpeech.LANG_AVAILABLE ||
                         ok == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                         ok == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                tts?.language = if (bangla) bn else Locale.ENGLISH
                tts?.setSpeechRate(0.95f)
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                ttsReady = true
            } else {
                showSignal("Voice engine nai!")
            }
        }
        startTicker()
        startLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        currentApp = event.packageName?.toString() ?: currentApp
        tradingBoxVisible = scan(rootInActiveWindow)
    }

    private fun scan(root: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (root == null || depth > 30) return false
        val text = root.text?.toString() ?: ""
        if (text.isNotEmpty() && MARKERS.any { text.contains(it, ignoreCase = true) }) return true
        for (i in 0 until root.childCount) {
            if (scan(root.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun startTicker() = scope.launch {
        while (isActive) {
            val prefs = getSharedPreferences("bot", MODE_PRIVATE)
            val enabled = prefs.getBoolean("bot_on", true)

            mainHandler.post {
                if (!enabled) {
                    removeOverlay()
                } else {
                    if (overlayView == null) showOverlay()
                    seconds++
                    overlayStatus?.text = if (tradingBoxVisible) "AI ACTIVE" else "AI SEARCHING"
                    overlayStatus?.setTextColor(
                        if (tradingBoxVisible) 0xFF00E676.toInt() else 0xFFFFB300.toInt())
                    overlayTimer?.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
                    overlaySignal?.visibility =
                        if (tradingBoxVisible) View.VISIBLE else View.GONE
                }
            }

            if (enabled && ttsReady) {
                if (tradingBoxVisible && !wasVisible) {
                    wasVisible = true
                    say(if (bangla) "হ্যাঁ, আমি ট্রেডিং চার্ট দেখতে পাচ্ছি এবং বিশ্লেষণ করছি। অনুগ্রহ করে অপেক্ষা করুন।"
                        else "Yes, I can see the trading chart and I am analyzing it. Please wait.")
                } else if (!tradingBoxVisible && wasVisible) {
                    wasVisible = false
                    say(if (bangla) "আমি কোনো ট্রেডিং চার্ট দেখতে পাচ্ছি না। অনুগ্রহ করে একটি ট্রেডিং চার্ট খুলুন।"
                        else "I cannot see any trading chart. Please open a trading chart.")
                }
            }
            delay(1000)
        }
    }

    private fun startLoop() = scope.launch {
        while (isActive) {
            val prefs = getSharedPreferences("bot", MODE_PRIVATE)
            val enabled = prefs.getBoolean("bot_on", true)
            val apiKey = prefs.getString("api_key", "") ?: ""
            if (enabled && tradingBoxVisible && apiKey.isNotEmpty()) {
                takeShot(apiKey)
            }
            delay(10000)
        }
    }

    private fun takeShot(apiKey: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            showSignal("Android 11+ lagbe")
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY,
            ContextCompat.getMainExecutor(this),
            object : TakeScreenshotCallback {
                override fun onSuccess(photo: ScreenshotResult) {
                    val hw = Bitmap.wrapHardwareBuffer(photo.hardwareBuffer, photo.colorSpace)
                    photo.hardwareBuffer.close()
                    val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    if (sw == null) { showSignal("Screenshot fail"); return }
                    scope.launch {
                        try {
                            val data = ChartAnalyzer.analyze(sw)
                            val result = MercuryClient.analyze(apiKey, data)
                            showSignal(result)
                            speakSignal(result)
                        } catch (e: Exception) {
                            showSignal("Error: ${e.message}")
                        }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    showSignal("Screenshot error: $errorCode")
                }
            })
    }

    private fun showSignal(text: String) {
        mainHandler.post {
            overlaySignal?.text = signalText(text)
            overlaySignal?.setTextColor(signalColor(text))
        }
    }

    private fun signalText(text: String): String = when {
        text.contains("DOWN", true) && !text.contains("UP", true) -> "SELL SIGNAL"
        text.contains("UP", true) && text.contains("signal", true) -> "BUY SIGNAL"
        else -> text.take(60)
    }

    private fun signalColor(text: String): Int = when {
        text.contains("DOWN", true) -> 0xFFFF5252.toInt()
        text.contains("UP", true) && text.contains("signal", true) -> 0xFF00E676.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    private fun speakSignal(text: String) {
        if (text == lastSignal) return
        lastSignal = text
        when {
            text.contains("DOWN", true) && !text.contains("UP", true) ->
                say(if (bangla) "সেল সিগন্যাল" else "Signal SELL")
            text.contains("UP", true) ->
                say(if (bangla) "বাই সিগন্যাল" else "Signal BUY")
        }
    }

    private fun say(text: String) {
        mainHandler.post {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "v${System.currentTimeMillis()}")
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            overlayView = null
            overlayStatus = null
            overlayTimer = null
            overlaySignal = null
        }
    }

    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { x = 0; y = -300 }

        overlayView = LayoutInflater.from(this).inflate(R.layout.widget_view, null)
        overlayStatus = overlayView?.findViewById(R.id.widgetStatus)
        overlayTimer = overlayView?.findViewById(R.id.widgetTimer)
        overlaySignal = overlayView?.findViewById(R.id.widgetSignal)
        overlayView?.findViewById<TextView>(R.id.widgetClose)?.setOnClickListener {
            getSharedPreferences("bot", MODE_PRIVATE).edit()
                .putBoolean("bot_on", false).apply()
        }

        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = params.x; sy = params.y }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = sx + (e.rawX - dx).toInt()
                        params.y = sy + (e.rawY - dy).toInt()
                        wm.updateViewLayout(overlayView, params)
                    }
                }
                return true
            }
        })

        try { wm.addView(overlayView, params) }
        catch (e: Exception) { Toast.makeText(this, "Overlay permission dao", Toast.LENGTH_LONG).show() }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        removeOverlay()
        super.onDestroy()
    }
}
