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

    /**
     * `layout`'ta tanımlı, açık kenarları `wiringOrder()` sırasıyla (fiziksel
     * kablolama sırası) dolaşıp her kenarın kendi LED sayısına göre örnekleme
     * bölgelerini üretir. Böylece tam perimetre, yalnızca üst-alt, L-şekilli
     * veya tek kenar gibi farklı fiziksel LED kurulumları desteklenir.
     */
    fun buildZonesForLayout(contentRect: Rect, layout: LedLayoutConfig, marginPercent: Int): List<Rect> {
        val width = contentRect.width()
        val height = contentRect.height()
        if (width <= 0 || height <= 0) return emptyList()

        val margin = max(1, (minOf(width, height) * marginPercent / 100f).roundToInt())
        val zones = mutableListOf<Rect>()

        for (edge in layout.wiringOrder()) {
            val count = layout.configFor(edge).ledCount
            zones += when (edge) {
                Edge.TOP -> topZones(contentRect, count, margin)
                Edge.RIGHT -> rightZones(contentRect, count, margin)
                Edge.BOTTOM -> bottomZones(contentRect, count, margin)
                Edge.LEFT -> leftZones(contentRect, count, margin)
            }
        }
        return zones
    }

    // Üst kenar: soldan sağa
    private fun topZones(rect: Rect, count: Int, margin: Int): List<Rect> {
        val width = rect.width()
        val height = rect.height()
        return (0 until count).map { i ->
            val x0 = (width.toFloat() * i / count).roundToInt()
            val x1 = (width.toFloat() * (i + 1) / count).roundToInt()
            Rect(
                rect.left + x0,
                rect.top,
                rect.left + x1.coerceAtMost(width),
                rect.top + margin.coerceAtMost(height),
            )
        }
    }

    // Sağ kenar: yukarıdan aşağı
    private fun rightZones(rect: Rect, count: Int, margin: Int): List<Rect> {
        val width = rect.width()
        val height = rect.height()
        return (0 until count).map { i ->
            val y0 = (height.toFloat() * i / count).roundToInt()
            val y1 = (height.toFloat() * (i + 1) / count).roundToInt()
            Rect(
                rect.left + (width - margin).coerceAtLeast(0),
                rect.top + y0,
                rect.left + width,
                rect.top + y1.coerceAtMost(height),
            )
        }
    }

    // Alt kenar: sağdan sola
    private fun bottomZones(rect: Rect, count: Int, margin: Int): List<Rect> {
        val width = rect.width()
        val height = rect.height()
        return (0 until count).map { i ->
            val x1 = (width.toFloat() * (count - i) / count).roundToInt()
            val x0 = (width.toFloat() * (count - i - 1) / count).roundToInt()
            Rect(
                rect.left + x0,
                rect.top + (height - margin).coerceAtLeast(0),
                rect.left + x1.coerceAtMost(width),
                rect.top + height,
            )
        }
    }

    // Sol kenar: aşağıdan yukarı
    private fun leftZones(rect: Rect, count: Int, margin: Int): List<Rect> {
        val width = rect.width()
        val height = rect.height()
        return (0 until count).map { i ->
            val y1 = (height.toFloat() * (count - i) / count).roundToInt()
            val y0 = (height.toFloat() * (count - i - 1) / count).roundToInt()
            Rect(
                rect.left,
                rect.top + y0,
                rect.left + margin.coerceAtMost(width),
                rect.top + y1.coerceAtMost(height),
            )
        }
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
