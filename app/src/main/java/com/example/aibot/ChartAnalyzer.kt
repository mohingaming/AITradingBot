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
        if (history.size > 6) history.removeFirst()

        val overall = if (history.size >= 2)
            ((smoothed - history.first()) / history.first()) * 100.0 else 0.0

        val list = history.toList()
        val lastMove = if (list.size >= 2)
            ((list.last() - list[list.size - 2]) / list[list.size - 2]) * 100.0 else 0.0

        val durationSec = detectDuration(fullBitmap)

        return """Live price: $smoothed
Overall trend: ${"%.4f".format(overall)} percent
Latest move (last scan): ${"%.4f".format(lastMove)} percent (${if (lastMove >= 0) "RISING" else "FALLING"})
Selected trade duration: $durationSec seconds
Rule: Answer WAIT only if the latest move is between -0.003 and +0.003 percent. Otherwise signal must follow the latest move direction (positive = UP, negative = DOWN). Duration ${durationSec}s."""
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
