package com.sih.drugtestclassifier.calibration

import android.graphics.Bitmap
import android.graphics.Color
import com.sih.drugtestclassifier.models.CalibrationResult
import com.sih.drugtestclassifier.models.CardDetectionResult
import com.sih.drugtestclassifier.models.RgbColor

/**
 * Computes and applies a simple per-channel white-balance correction from
 * the reference card's white patch. Ported 1:1 from
 * lib/calibration/color_calibrator.dart.
 */
class ColorCalibrator(
    private val targetWhite: RgbColor = RgbColor(235.0, 235.0, 235.0),
    private val edgeInsetFraction: Double = 0.15,
) {

    fun computeFromWhitePatch(image: Bitmap, box: CardDetectionResult): CalibrationResult {
        if (!box.found) return CalibrationResult.NONE

        val rawLeft = box.left!!
        val rawTop = box.top!!
        val rawW = box.width!!
        val rawH = box.height!!
        val insetX = Math.round(rawW * edgeInsetFraction).toInt()
        val insetY = Math.round(rawH * edgeInsetFraction).toInt()

        val canInset = rawW - 2 * insetX > 0 && rawH - 2 * insetY > 0
        val left = if (canInset) rawLeft + insetX else rawLeft
        val top = if (canInset) rawTop + insetY else rawTop
        val w = if (canInset) rawW - 2 * insetX else rawW
        val h = if (canInset) rawH - 2 * insetY else rawH

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        var y = top
        while (y < top + h) {
            if (y < 0 || y >= image.height) { y++; continue }
            var x = left
            while (x < left + w) {
                if (x < 0 || x >= image.width) { x++; continue }
                val p = image.getPixel(x, y)
                sumR += Color.red(p)
                sumG += Color.green(p)
                sumB += Color.blue(p)
                count++
                x++
            }
            y++
        }

        if (count == 0) return CalibrationResult.NONE

        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count
        if (avgR < 1 || avgG < 1 || avgB < 1) {
            return CalibrationResult.NONE
        }

        fun clampGain(g: Double): Double = g.coerceIn(0.5, 2.0)

        return CalibrationResult(
            applied = true,
            gainR = clampGain(targetWhite.r / avgR),
            gainG = clampGain(targetWhite.g / avgG),
            gainB = clampGain(targetWhite.b / avgB),
        )
    }

    fun apply(color: RgbColor, calibration: CalibrationResult): RgbColor {
        if (!calibration.applied) return color
        return color.scaled(calibration.gainR, calibration.gainG, calibration.gainB)
    }
}
