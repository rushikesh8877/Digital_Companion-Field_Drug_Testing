package com.sih.drugtestclassifier.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sih.drugtestclassifier.calibration.ColorCalibrator
import com.sih.drugtestclassifier.calibration.ReferenceCardDetector
import com.sih.drugtestclassifier.classification.Classifier
import com.sih.drugtestclassifier.classification.ColorExtractor
import com.sih.drugtestclassifier.classification.ExtractedColor
import com.sih.drugtestclassifier.color.ColorSpace
import com.sih.drugtestclassifier.models.CardDetectionFailure
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.KitProfile
import com.sih.drugtestclassifier.models.RgbColor
import com.sih.drugtestclassifier.models.TestImage
import java.io.File

/**
 * Everything the pipeline computed along the way to a [ClassificationResult] —
 * card detection, raw vs. calibrated colour, and the colour distance to every
 * reference in the kit profile. Not used by the normal capture flow; exists
 * so a calibration screen can show *why* a real photo isn't classifying the
 * way you expect, and what real Lab numbers to put in the kit profile.
 */
data class DiagnosticReport(
    val cardFound: Boolean,
    val cardFailureReason: String?,
    val cardBox: String?,
    val testAreaBox: String?,
    val sampleCount: Int,
    val stdDev: Double?,
    val calibrationApplied: Boolean,
    val gains: Triple<Double, Double, Double>?,
    val rawRgb: RgbColor?,
    val calibratedRgb: RgbColor?,
    val lab: String?,
    val distances: List<Pair<String, Double>>,
    val maxConfidentDeltaE: Double,
    val result: ClassificationResult?,
)

/**
 * Full pipeline: Camera output (TestImage) -> Image Processing -> Colour
 * Classification -> ClassificationResult. Ported 1:1 from
 * lib/pipeline/classification_pipeline.dart. This is the ONE class the UI
 * and any downstream record-keeping code need to call.
 */
class ClassificationPipeline(
    private val kit: KitProfile,
    private val cardDetector: ReferenceCardDetector = ReferenceCardDetector(),
    private val calibrator: ColorCalibrator = ColorCalibrator(),
    private val extractor: ColorExtractor = ColorExtractor(),
    private val classifier: Classifier = Classifier(),
) {

    /** Runs the full pipeline against a [TestImage] whose imageUri points at a local file path. */
    fun classify(testImage: TestImage): ClassificationResult {
        val file = File(stripFileScheme(testImage.imageUri))
        if (!file.exists()) {
            return ClassificationResult.inconclusive("Image file not found: ${testImage.imageUri}")
        }
        val bytes = file.readBytes()
        return classifyBytes(bytes)
    }

    /** Same as [classify] but takes raw image bytes directly. */
    fun classifyBytes(bytes: ByteArray): ClassificationResult {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return ClassificationResult.inconclusive("Could not decode image.")
        return classifyImage(decoded)
    }

    fun classifyImage(image: Bitmap): ClassificationResult {
        // 1. Detect the reference card's white patch.
        val card = cardDetector.detect(image)
        if (!card.found) {
            return ClassificationResult.inconclusive(
                "Reference colour card not detected — keep the whole card in frame " +
                    "and retake the photo.",
                calibrated = false,
            )
        }

        // 2. Derive white-balance gains from the card, so lighting doesn't
        //    shift the extracted test colour.
        val calibration = calibrator.computeFromWhitePatch(image, card)

        // 3. Locate and sample the test-reaction area, relative to the card.
        val extracted = extractor.extractTestArea(image, card, kit)

        // 4. Apply the white-balance correction to the sampled colour.
        val calibratedColor = calibrator.apply(extracted.average, calibration)
        val calibratedExtracted = ExtractedColor(
            average = calibratedColor,
            stdDev = extracted.stdDev,
            sampleCount = extracted.sampleCount,
            left = extracted.left,
            top = extracted.top,
            width = extracted.width,
            height = extracted.height,
        )

        // 5. Classify.
        return classifier.classify(
            extracted = calibratedExtracted,
            kit = kit,
            calibrated = calibration.applied,
        )
    }

    /** Same steps as [classifyImage], but returns every intermediate value instead of just the verdict. */
    fun diagnose(image: Bitmap): DiagnosticReport {
        val card = cardDetector.detect(image)
        if (!card.found) {
            val reason = when (card.failure) {
                CardDetectionFailure.NOT_FOUND -> "No white-ish blob found at all — is the reference card's white patch actually in frame?"
                CardDetectionFailure.TOO_SMALL -> "A white-ish blob was found but it's too small a fraction of the frame — move the camera closer."
                CardDetectionFailure.LOW_CONFIDENCE_PATCH_MATCH, null -> "Card not detected."
            }
            return DiagnosticReport(
                cardFound = false, cardFailureReason = reason, cardBox = null, testAreaBox = null,
                sampleCount = 0, stdDev = null, calibrationApplied = false, gains = null,
                rawRgb = null, calibratedRgb = null, lab = null, distances = emptyList(),
                maxConfidentDeltaE = kit.maxConfidentDeltaE, result = null,
            )
        }
        val cardBoxText = "left=${card.left}, top=${card.top}, width=${card.width}, height=${card.height}"

        val calibration = calibrator.computeFromWhitePatch(image, card)
        val extracted = extractor.extractTestArea(image, card, kit)
        val testAreaBoxText = "left=${extracted.left}, top=${extracted.top}, width=${extracted.width}, height=${extracted.height}"

        val calibratedColor = calibrator.apply(extracted.average, calibration)
        val calibratedExtracted = ExtractedColor(
            average = calibratedColor, stdDev = extracted.stdDev, sampleCount = extracted.sampleCount,
            left = extracted.left, top = extracted.top, width = extracted.width, height = extracted.height,
        )

        val lab = ColorSpace.rgbToLab(calibratedColor)
        val distances = kit.references
            .map { ref -> ref.outcome.label to lab.distanceTo(ref.labColor) }
            .sortedBy { it.second }

        val result = classifier.classify(calibratedExtracted, kit, calibration.applied)

        return DiagnosticReport(
            cardFound = true,
            cardFailureReason = null,
            cardBox = cardBoxText,
            testAreaBox = testAreaBoxText,
            sampleCount = extracted.sampleCount,
            stdDev = extracted.stdDev,
            calibrationApplied = calibration.applied,
            gains = Triple(calibration.gainR, calibration.gainG, calibration.gainB),
            rawRgb = extracted.average,
            calibratedRgb = calibratedColor,
            lab = lab.toString(),
            distances = distances,
            maxConfidentDeltaE = kit.maxConfidentDeltaE,
            result = result,
        )
    }

    private fun stripFileScheme(uri: String): String =
        if (uri.startsWith("file://")) uri.substring(7) else uri
}

