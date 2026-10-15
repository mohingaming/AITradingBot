package com.example.aibot

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

object ChartAnalyzer {

    private var lastPrice: Double? = null
    private val history = ArrayDeque<Double>()

    private const val CROP_LEFT = 0.62f
    private const val CROP_TOP = 0.22f
    private const val CROP_RIGHT = 1.00f
    private const val CROP_BOTTOM = 0.85f

    suspend fun analyze(fullBitmap: Bitmap): String {
        val left = (fullBitmap.width * CROP_LEFT).toInt()
        val top = (fullBitmap.height * CROP_TOP).toInt()
        val width = (fullBitmap.width * (CROP_RIGHT - CROP_LEFT)).toInt()
        val height = (fullBitmap.height * (CROP_BOTTOM - CROP_TOP)).toInt()
        val cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(InputImage.fromBitmap(cropped, 0)).await()

        val prices = result.textBlocks
            .flatMap { it.lines }
            .flatMap { line -> Regex("""\d{1,6}\.\d{1,5}""").findAll(line.text).map { it.value.toDouble() } }
            .filter { it in 0.0001..100000.0 }
            .distinct()
            .sorted()

        if (prices.size < 2) return "ERROR: chart price pora jayni"

        val guess = lastPrice ?: prices[prices.size / 2]
        val current = prices.minByOrNull { abs(it - guess) } ?: guess
        val smoothed = lastPrice?.let { (it + current) / 2.0 } ?: current
        lastPrice = smoothed

        history.addLast(smoothed)
        if (history.size > 10) history.removeFirst()

        val change = if (history.size >= 2)
            ((smoothed - history.first()) / history.first()) * 100.0 else 0.0

        val durationSec = detectDuration(fullBitmap)

        return """Live price: $smoothed
Recent price trend: ${"%.4f".format(change)} percent (${if (change >= 0) "RISING" else "FALLING"})
Visible range: ${prices.first()} to ${prices.last()}
Selected trade duration: $durationSec seconds
Samples: ${history.size}
Rule: trade duration is $durationSec seconds. For very short durations (under 60s), weight the most recent momentum heavily. If trend is clearly positive, signal UP. If clearly negative, signal DOWN. If near zero, WAIT."""
    }

    private suspend fun detectDuration(bitmap: Bitmap): Int {
        return try {
            val left = (bitmap.width * 0.03f).toInt()
            val top = (bitmap.height * 0.79f).toInt()
            val width = (bitmap.width * 0.45f).toInt()
            val height = (bitmap.height * 0.10f).toInt()
            if (width <= 10 || height <= 10) return 60
            val crop = Bitmap.createBitmap(bitmap, left, top, width, height)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(InputImage.fromBitmap(crop, 0)).await()
            val regex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
            var best = 60
            regex.findAll(result.text).forEach { m ->
                val g = m.groupValues
                val hh = g[1].toIntOrNull() ?: 0
                val mm = g[2].toIntOrNull() ?: 0
                val ss = g[3].toIntOrNull() ?: 0
                val total = hh * 3600 + mm * 60 + ss
                if (total in 5..3600 && total > 0) best = total
            }
            best
        } catch (e: Exception) {
            60
        }
    }
}
