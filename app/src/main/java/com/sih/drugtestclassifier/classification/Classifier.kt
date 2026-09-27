package com.sih.drugtestclassifier.classification

import com.sih.drugtestclassifier.color.ColorSpace
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.KitProfile

/**
 * Turns a sampled test-area colour into a [ClassificationResult] using
 * calibrated colour-distance thresholds. Ported 1:1 from
 * lib/classification/classifier.dart.
 *
 * Decision rule:
 *   1. If the sample is too noisy (glare/motion blur) -> Inconclusive.
 *   2. Convert the (calibrated) average colour to Lab.
 *   3. Find the nearest reference outcome colour for this kit.
 *   4. If that distance is within the kit's confident threshold -> call it,
 *      with confidence derived from how comfortably it beat the runner-up.
 *   5. Otherwise -> Inconclusive.
 */
class Classifier(private val maxAllowedStdDevForConfidentCall: Double = 22.0) {

    fun classify(extracted: ExtractedColor, kit: KitProfile, calibrated: Boolean): ClassificationResult {
        if (extracted.sampleCount == 0) {
            return ClassificationResult.inconclusive(
                "Test area fell outside the image frame.",
                calibrated = calibrated,
            )
        }

        if (extracted.stdDev > maxAllowedStdDevForConfidentCall) {
            return ClassificationResult.inconclusive(
                "Test area colour was too inconsistent to read reliably " +
                    "(likely glare or motion blur) — retake the photo.",
                calibrated = calibrated,
            )
        }

        val lab = ColorSpace.rgbToLab(extracted.average)

        // Distance to every configured outcome for this kit, sorted nearest-first.
        val distances = kit.references
            .map { ref -> ref.outcome to lab.distanceTo(ref.labColor) }
            .sortedBy { it.second }

        if (distances.isEmpty()) {
            return ClassificationResult.inconclusive(
                "No reference colours configured for kit \"${kit.kitId}\".",
                calibrated = calibrated,
            )
        }

        val nearest = distances.first()
        if (nearest.second > kit.maxConfidentDeltaE) {
            return ClassificationResult.inconclusive(
                "Sampled colour did not clearly match any known result for this kit.",
                calibrated = calibrated,
            )
        }

        // Confidence: 1.0 when dead-on the reference colour, decaying linearly
        // to ~0.5 at the confidence threshold itself.
        val normalizedDistance = (nearest.second / kit.maxConfidentDeltaE).coerceIn(0.0, 1.0)
        val confidence = 1.0 - 0.5 * normalizedDistance

        return ClassificationResult(
            result = nearest.first.label,
            confidence = confidence.toFloat(),
            calibrated = calibrated,
        )
    }
}
