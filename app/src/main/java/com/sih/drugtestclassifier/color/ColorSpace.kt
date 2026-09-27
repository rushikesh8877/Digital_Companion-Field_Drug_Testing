package com.sih.drugtestclassifier.color

import com.sih.drugtestclassifier.models.LabColor
import com.sih.drugtestclassifier.models.RgbColor
import kotlin.math.pow

/**
 * Colour-space conversions. Ported 1:1 from lib/color/color_space.dart —
 * same formulas, same constants, same order of operations — so results are
 * numerically identical to the original Flutter/Dart pipeline.
 */
object ColorSpace {

    /** Simple holder for (h in 0-360, s in 0-1, v in 0-1). */
    data class Hsv(val h: Double, val s: Double, val v: Double)

    /** RGB (0-255 each) -> HSV. */
    fun rgbToHsv(c: RgbColor): Hsv {
        val r = c.r / 255.0
        val g = c.g / 255.0
        val b = c.b / 255.0
        val maxV = maxOf(r, g, b)
        val minV = minOf(r, g, b)
        val delta = maxV - minV

        var h: Double
        if (delta == 0.0) {
            h = 0.0
        } else if (maxV == r) {
            h = 60 * (((g - b) / delta).mod(6.0))
        } else if (maxV == g) {
            h = 60 * (((b - r) / delta) + 2)
        } else {
            h = 60 * (((r - g) / delta) + 4)
        }
        if (h < 0) h += 360

        val s = if (maxV == 0.0) 0.0 else delta / maxV
        val v = maxV
        return Hsv(h, s, v)
    }

    /** RGB (0-255 each, sRGB) -> CIE Lab (D65 white point). */
    fun rgbToLab(c: RgbColor): LabColor {
        fun linearize(channel: Double): Double {
            val v = channel / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

        val rl = linearize(c.r)
        val gl = linearize(c.g)
        val bl = linearize(c.b)

        // sRGB -> XYZ (D65)
        val x = rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375
        val y = rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750
        val z = rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041

        // Normalise by D65 reference white
        val xn = 0.95047
        val yn = 1.00000
        val zn = 1.08883
        val fx = labF(x / xn)
        val fy = labF(y / yn)
        val fz = labF(z / zn)

        val l = 116 * fy - 16
        val a = 500 * (fx - fy)
        val b = 200 * (fy - fz)
        return LabColor(l, a, b)
    }

    private fun labF(t: Double): Double {
        val delta = 6.0 / 29.0
        return if (t > delta * delta * delta) {
            t.pow(1.0 / 3.0)
        } else {
            t / (3 * delta * delta) + 4.0 / 29.0
        }
    }
}
