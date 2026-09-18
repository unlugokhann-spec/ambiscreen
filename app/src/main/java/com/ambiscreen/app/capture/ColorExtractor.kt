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

    /**
     * Sinemaskop/letterbox içerikte üstte ve altta (ya da pillarbox'ta yanlarda)
     * kalan katı siyah şeritleri tespit edip gerçek görüntü alanını döner.
     * Kenardan içeri doğru satır/sütun ortalama parlaklığı eşik değerin altında
     * kaldığı sürece kırpılır; yanlış pozitifleri önlemek için toplam kırpma
     * oranı `maxTrimFraction` ile sınırlanır (gerçekten karanlık bir sahne
     * tamamen siyah şerit sanılıp aşırı kırpılmasın diye).
     */
    fun detectContentRect(
        bitmap: Bitmap,
        blackThreshold: Int = 16,
        maxTrimFraction: Float = 0.4f,
    ): Rect {
        val width = bitmap.width
        val height = bitmap.height
        val maxTrimRows = (height * maxTrimFraction).roundToInt()
        val maxTrimCols = (width * maxTrimFraction).roundToInt()

        var top = 0
        while (top < maxTrimRows && isRowBlack(bitmap, top, blackThreshold)) top++

        var bottom = height - 1
        var trimmedBottom = 0
        while (trimmedBottom < maxTrimRows && bottom > top && isRowBlack(bitmap, bottom, blackThreshold)) {
            bottom--
            trimmedBottom++
        }

        var left = 0
        while (left < maxTrimCols && isColumnBlack(bitmap, left, top, bottom, blackThreshold)) left++

        var right = width - 1
        var trimmedRight = 0
        while (trimmedRight < maxTrimCols && right > left &&
            isColumnBlack(bitmap, right, top, bottom, blackThreshold)
        ) {
            right--
            trimmedRight++
        }

        return Rect(left, top, (right + 1).coerceAtMost(width), (bottom + 1).coerceAtMost(height))
    }

    private fun isRowBlack(bitmap: Bitmap, y: Int, threshold: Int): Boolean {
        val pixels = IntArray(bitmap.width)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
        return averageLuma(pixels) < threshold
    }

    private fun isColumnBlack(bitmap: Bitmap, x: Int, top: Int, bottom: Int, threshold: Int): Boolean {
        val h = (bottom - top + 1).coerceAtLeast(1)
        val pixels = IntArray(h)
        bitmap.getPixels(pixels, 0, 1, x, top, 1, h)
        return averageLuma(pixels) < threshold
    }

    private fun averageLuma(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0
        var sum = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            sum += (r + g + b) / 3
        }
        return (sum / pixels.size).toInt()
    }

    fun buildPerimeterZones(contentRect: Rect, ledCount: Int, marginPercent: Int): List<Rect> {
        val width = contentRect.width()
        val height = contentRect.height()
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

        val offsetX = contentRect.left
        val offsetY = contentRect.top
        val zones = mutableListOf<Rect>()

        // Üst kenar: soldan sağa
        for (i in 0 until topCount) {
            val x0 = (width.toFloat() * i / topCount).roundToInt()
            val x1 = (width.toFloat() * (i + 1) / topCount).roundToInt()
            zones += Rect(
                offsetX + x0,
                offsetY,
                offsetX + x1.coerceAtMost(width),
                offsetY + margin.coerceAtMost(height),
            )
        }

        // Sağ kenar: yukarıdan aşağı
        for (i in 0 until rightCount) {
            val y0 = (height.toFloat() * i / rightCount).roundToInt()
            val y1 = (height.toFloat() * (i + 1) / rightCount).roundToInt()
            zones += Rect(
                offsetX + (width - margin).coerceAtLeast(0),
                offsetY + y0,
                offsetX + width,
                offsetY + y1.coerceAtMost(height),
            )
        }

        // Alt kenar: sağdan sola
        for (i in 0 until bottomCount) {
            val x1 = (width.toFloat() * (bottomCount - i) / bottomCount).roundToInt()
            val x0 = (width.toFloat() * (bottomCount - i - 1) / bottomCount).roundToInt()
            zones += Rect(
                offsetX + x0,
                offsetY + (height - margin).coerceAtLeast(0),
                offsetX + x1.coerceAtMost(width),
                offsetY + height,
            )
        }

        // Sol kenar: aşağıdan yukarı
        for (i in 0 until leftCount) {
            val y1 = (height.toFloat() * (leftCount - i) / leftCount).roundToInt()
            val y0 = (height.toFloat() * (leftCount - i - 1) / leftCount).roundToInt()
            zones += Rect(
                offsetX,
                offsetY + y0,
                offsetX + margin.coerceAtMost(width),
                offsetY + y1.coerceAtMost(height),
            )
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
