package com.ambiscreen.app.capture

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ekranın çevresini (perimetre) tek bir LED şeridi gibi bölerek her LED'in
 * baktığı bölgenin ortalama rengini hesaplar. Hyperion/Ambilight projelerindeki
 * "edge sampling" yaklaşımının basitleştirilmiş hâlidir.
 *
 * LED sırası: üst kenar (soldan sağa) -> sağ kenar (yukarıdan aşağı) ->
 * alt kenar (sağdan sola) -> sol kenar (aşağıdan yukarı), saat yönünde.
 * Fiziksel kurulum ters yönlüyse `reverse` ayarıyla telafi edilir.
 */
object ColorExtractor {

    fun buildPerimeterZones(width: Int, height: Int, ledCount: Int, marginPercent: Int): List<Rect> {
        if (ledCount <= 0 || width <= 0 || height <= 0) return emptyList()

        val margin = max(1, (minOf(width, height) * marginPercent / 100f).roundToInt())

        val topLen = width.toFloat()
        val rightLen = height.toFloat()
        val bottomLen = width.toFloat()
        val leftLen = height.toFloat()
        val total = topLen + rightLen + bottomLen + leftLen

        // En büyük kalan yöntemiyle her kenara orantılı LED sayısı dağıt.
        val rawCounts = listOf(topLen, rightLen, bottomLen, leftLen).map { it / total * ledCount }
        val baseCounts = rawCounts.map { it.toInt() }.toMutableList()
        var remaining = ledCount - baseCounts.sum()
        val remainders = rawCounts.mapIndexed { i, v -> i to (v - v.toInt()) }
            .sortedByDescending { it.second }
        var idx = 0
        while (remaining > 0 && idx < remainders.size) {
            baseCounts[remainders[idx].first] += 1
            remaining--
            idx++
        }
        val (topCount, rightCount, bottomCount, leftCount) = baseCounts

        val zones = mutableListOf<Rect>()

        // Üst kenar: soldan sağa
        for (i in 0 until topCount) {
            val x0 = (width.toFloat() * i / topCount).roundToInt()
            val x1 = (width.toFloat() * (i + 1) / topCount).roundToInt()
            zones += Rect(x0, 0, x1.coerceAtMost(width), margin.coerceAtMost(height))
        }

        // Sağ kenar: yukarıdan aşağı
        for (i in 0 until rightCount) {
            val y0 = (height.toFloat() * i / rightCount).roundToInt()
            val y1 = (height.toFloat() * (i + 1) / rightCount).roundToInt()
            zones += Rect((width - margin).coerceAtLeast(0), y0, width, y1.coerceAtMost(height))
        }

        // Alt kenar: sağdan sola
        for (i in 0 until bottomCount) {
            val x1 = (width.toFloat() * (bottomCount - i) / bottomCount).roundToInt()
            val x0 = (width.toFloat() * (bottomCount - i - 1) / bottomCount).roundToInt()
            zones += Rect(x0, (height - margin).coerceAtLeast(0), x1.coerceAtMost(width), height)
        }

        // Sol kenar: aşağıdan yukarı
        for (i in 0 until leftCount) {
            val y1 = (height.toFloat() * (leftCount - i) / leftCount).roundToInt()
            val y0 = (height.toFloat() * (leftCount - i - 1) / leftCount).roundToInt()
            zones += Rect(0, y0, margin.coerceAtMost(width), y1.coerceAtMost(height))
        }

        return zones
    }

    /** Her zonun ortalama rengini 0xRRGGBB (alfasız) Int olarak döner. */
    fun averageColors(bitmap: Bitmap, zones: List<Rect>): IntArray {
        val result = IntArray(zones.size)
        for ((index, rect) in zones.withIndex()) {
            result[index] = averageColor(bitmap, rect)
        }
        return result
    }

    private fun averageColor(bitmap: Bitmap, rect: Rect): Int {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return 0

        var r = 0L
        var g = 0L
        var b = 0L
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)
        for (pixel in pixels) {
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
        }
        val count = pixels.size
        return ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }
}
