package com.example.aibot

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object ChartAnalyzer {

    private const val CROP_LEFT = 0.70f
    private const val CROP_TOP = 0.35f
    private const val CROP_RIGHT = 1.00f
    private const val CROP_BOTTOM = 0.80f

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
            .flatMap { line -> Regex("""\d{3,6}\.\d{1,2}""").findAll(line.text).map { it.value.toDouble() } }
            .distinct()
            .sortedDescending()

        if (prices.size < 3) return "ERROR: chart price পড়া যায়নি"

        val max = prices.first()
        val min = prices.last()
        val mid = (max + min) / 2
        return """Chart axis prices (top to bottom): ${prices.joinToString(", ")}
Range: $min to $max, mid: ${"%.2f".format(mid)}
Axis labels detected: ${prices.size}"""
    }
}
