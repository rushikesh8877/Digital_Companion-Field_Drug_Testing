package com.sih.drugtestclassifier.classification

import android.graphics.Bitmap
import android.graphics.Color
import com.sih.drugtestclassifier.models.CardDetectionResult
import com.sih.drugtestclassifier.models.KitProfile
import com.sih.drugtestclassifier.models.RgbColor
import kotlin.math.sqrt

/**
 * Result of sampling the test-reaction area. Ported from
 * lib/classification/color_extractor.dart.
 */
data class ExtractedColor(
    val average: RgbColor,
    val stdDev: Double,
    val sampleCount: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Locates the test-reaction area relative to the detected card and samples
 * its average colour. Ported 1:1 from
 * lib/classification/color_extractor.dart — same rounding, same interior
 * inset sampling, same stdDev computation.
 */
class ColorExtractor(private val edgeInsetFraction: Double = 0.2) {

    fun extractTestArea(image: Bitmap, card: CardDetectionResult, kit: KitProfile): ExtractedColor {
        check(card.found) { "extractTestArea called without a detected card — check card.found first." }

        val cardLeft = card.left!!
        val cardTop = card.top!!
        val cardW = card.width!!
        val cardH = card.height!!

        // Nominal rectangle, per the kit profile's fractional offsets.
        val nominalLeft = Math.round(cardLeft + kit.testAreaOffsetXFractionOfCardWidth * cardW).toInt()
        val nominalTop = Math.round(cardTop + kit.testAreaOffsetYFractionOfCardHeight * cardH).toInt()
        val nominalWidth = Math.round(kit.testAreaWidthFractionOfCardWidth * cardW)
            .toInt().coerceIn(1, image.width)
        val nominalHeight = Math.round(kit.testAreaHeightFractionOfCardHeight * cardH)
            .toInt().coerceIn(1, image.height)

        // Shrink to the interior before actually reading pixels.
        val insetX = Math.round(nominalWidth * edgeInsetFraction).toInt()
        val insetY = Math.round(nominalHeight * edgeInsetFraction).toInt()
        val canInset = nominalWidth - 2 * insetX > 0 && nominalHeight - 2 * insetY > 0
        val left = if (canInset) nominalLeft + insetX else nominalLeft
        val top = if (canInset) nominalTop + insetY else nominalTop
        val width = if (canInset) nominalWidth - 2 * insetX else nominalWidth
        val height = if (canInset) nominalHeight - 2 * insetY else nominalHeight

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        val samplesR = mutableListOf<Double>()
        val samplesG = mutableListOf<Double>()
        val samplesB = mutableListOf<Double>()

        for (y in top until top + height) {
            if (y < 0 || y >= image.height) continue
            for (x in left until left + width) {
                if (x < 0 || x >= image.width) continue
                val p = image.getPixel(x, y)
                val r = Color.red(p).toDouble()
                val g = Color.green(p).toDouble()
                val b = Color.blue(p).toDouble()
                sumR += r
                sumG += g
                sumB += b
                samplesR.add(r)
                samplesG.add(g)
                samplesB.add(b)
            }
        }

        val n = samplesR.size
        if (n == 0) {
            return ExtractedColor(
                average = RgbColor(0.0, 0.0, 0.0),
                stdDev = 0.0,
                sampleCount = 0,
                left = left,
                top = top,
                width = width,
                height = height,
            )
        }

        val avgR = sumR / n
        val avgG = sumG / n
        val avgB = sumB / n

        fun variance(samples: List<Double>, mean: Double): Double {
            var sq = 0.0
            for (s in samples) {
                sq += (s - mean) * (s - mean)
            }
            return sq / samples.size
        }

        val stdR = sqrt(variance(samplesR, avgR))
        val stdG = sqrt(variance(samplesG, avgG))
        val stdB = sqrt(variance(samplesB, avgB))
        val avgStdDev = (stdR + stdG + stdB) / 3

        return ExtractedColor(
            average = RgbColor(avgR, avgG, avgB),
            stdDev = avgStdDev,
            sampleCount = n,
            left = left,
            top = top,
            width = width,
            height = height,
        )
    }
}
