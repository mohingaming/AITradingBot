package com.example.aibot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BotService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlayView: View? = null
    private var overlayStatus: TextView? = null
    private var overlaySignal: TextView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiKey = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        apiKey = getSharedPreferences("bot", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""

        when (intent?.action) {
            ACTION_START -> {
                startForeground(1, buildNotification("AI Bot ON"))
                showOverlay()
                val rc = intent.getIntExtra("resultCode", 0)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>("data")
                if (rc != 0 && data != null) initProjection(rc, data)
                startLoop()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun initProjection(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels,
            PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "bot", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null)
    }

    private fun capture(): Bitmap? {
        val img = imageReader?.acquireLatestImage() ?: return null
        val plane = img.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * img.width
        val bmp = Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        img.close()
        return Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
    }

    private fun startLoop() = scope.launch {
        while (isActive) {
            val visible = AppDetectorService.tradingBoxVisible
            Handler(Looper.getMainLooper()).post {
                overlayStatus?.text = if (visible) "Trading box dekhte pachhi"
                                      else "Kichu dekhte pachhi na"
                overlayStatus?.setTextColor(
                    if (visible) 0xFF4CAF50.toInt() else 0xFFFF5555.toInt())
            }

            if (visible && apiKey.isNotEmpty()) {
                try {
                    val bmp = capture() ?: continue
                    val chartData = ChartAnalyzer.analyze(bmp)
                    val result = MercuryClient.analyze(apiKey, chartData)
                    Handler(Looper.getMainLooper()).post {
                        overlaySignal?.text = "Signal: $result"
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        overlaySignal?.text = "Error: ${e.message}"
                    }
                }
            }
            delay(5000)
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
        overlaySignal = overlayView?.findViewById(R.id.widgetSignal)
        overlayView?.findViewById<View>(R.id.btnStop)
            ?.setOnClickListener { stopSelf() }

        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = params.x; startY = params.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (e.rawX - downX).toInt()
                        params.y = startY + (e.rawY - downY).toInt()
                        wm.updateViewLayout(overlayView, params)
                    }
                }
                return false
            }
        })

        wm.addView(overlayView, params)
    }

    private fun buildNotification(text: String): Notification {
        val ch = NotificationChannel("bot", "AI Bot", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        return Notification.Builder(this, "bot")
            .setContentTitle(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        overlayView?.let {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
