package com.example.aibot

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

object ChartAnalyzer {

    private var lastPrice: Double? = null
    private var prevLines: List<Pair<Double, Int>> = emptyList()
    private var lastRoundSec = -1
    private val history = ArrayDeque<Double>()

    private fun crop(b: Bitmap, l: Float, t: Float, r: Float, bb: Float): Bitmap {
        val x = (b.width * l).toInt()
        val y = (b.height * t).toInt()
        val w = ((b.width * (r - l)).toInt()).coerceAtLeast(1)
        val h = ((b.height * (bb - t)).toInt()).coerceAtLeast(1)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    private suspend fun ocrText(b: Bitmap): String {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(b, 0)).await().text
    }

    suspend fun analyze(full: Bitmap): String {
        // ১. দামের এলাকা OCR — লাইন + position সহ
        val priceText = ocrText(crop(full, 0.62f, 0.22f, 1.00f, 0.85f))
        val lines = Regex("""(\d{1,6}\.\d{2,5})[^\d]*""").findAll(priceText)
            .mapNotNull { m ->
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v in 0.0001..100000.0) v else null
            }
            .distinct()
            .toList()

        // position ছাড়া value-only তাই আলাদা approach: আগের frame-এর সাথে মিলিয়ে বদলানো value টাই current price
        var current: Double? = null
        if (lines.isNotEmpty() && prevLines.isNotEmpty()) {
            // যে value আগের frame-এ ছিল না সেটাই নীল tag
            val changed = lines.filter { v -> prevLines.none { abs(it - v) < 0.000001 } }
            if (changed.isNotEmpty()) {
                val guess = lastPrice ?: changed.first()
                current = changed.minByOrNull { abs(it - guess) }
            }
        }
        if (current == null && lines.isNotEmpty()) {
            val guess = lastPrice ?: lines.sorted().let { it[it.size / 2] }
            current = lines.minByOrNull { abs(it - guess) }
        }
        prevLines = lines

        if (current == null) return "ERROR"

        val smoothed = lastPrice?.let { (it + current) / 2.0 } ?: current
        lastPrice = smoothed
        history.addLast(smoothed)
        if (history.size > 6) history.removeFirst()
        val list = history.toList()
        val move = if (list.size >= 2) ((list.last() - list[list.size - 2]) / list[list.size - 2]) * 100.0 else 0.0
        val overall = if (list.size >= 2) ((list.last() - list.first()) / list.first()) * 100.0 else 0.0

        // ২. রাউন্ড countdown (বিভ্রান্তিকর লাইনের পাশের 00:XX)
        val rcText = ocrText(crop(full, 0.55f, 0.33f, 0.82f, 0.44f))
        var roundSec = -1
        Regex("""\d{1,2}:(\d{2})""").findAll(rcText).forEach { m ->
            val s = m.groupValues[1].toIntOrNull() ?: return@forEach
            if (s in 0..59 && m.value.startsWith("00")) roundSec = s
        }
        var newRound = false
        if (roundSec in 0..59) {
            if (lastRoundSec in 0..59 && roundSec > lastRoundSec + 5) newRound = true
            lastRoundSec = roundSec
        }

        // ৩. Server clock (উপরে বামে HH:MM:SS UTC+6)
        val clock = Regex("""(\d{1,2}:\d{2}:\d{2})""")
            .find(ocrText(crop(full, 0.12f, 0.15f, 0.42f, 0.21f)))
            ?.groupValues?.get(1) ?: ""

        return "PRICE=$smoothed MOVE=${"%.5f".format(move)} OVERALL=${"%.5f".format(overall)} ROUND=$roundSec NEWROUND=$newRound CLOCK=$clock"
    }
}
