package com.sih.drugtestclassifier.models

import kotlin.math.sqrt

/**
 * Canonical shared data models for the Digital Companion for Field Drug
 * Testing project (SIH PS 26231). These are the LOCKED contracts from the
 * project doc (section 5) — every module (camera, classification, security,
 * database, UI) imports these exact classes instead of declaring its own
 * copy, so nothing drifts out of sync during integration.
 *
 *   TestImage           { imageUri, capturedAt }
 *   ClassificationResult{ result, confidence, calibrated }
 *   DigitalTestRecord   { testId, operatorId, timestamp, latitude, longitude,
 *                         result, confidence, imageHash, recordHash, signature }
 */

/** Output of the camera module (Person 1). */
data class TestImage(val imageUri: String, val capturedAt: Long)

/** The three allowed outcomes for the classifier's internal use. */
enum class TestOutcome {
    POSITIVE, NEGATIVE, INCONCLUSIVE;

    val label: String
        get() = when (this) {
            POSITIVE -> "Positive"
            NEGATIVE -> "Negative"
            INCONCLUSIVE -> "Inconclusive"
        }
}

/**
 * Output of the classification module (Person 2), consumed by Person 3
 * (digital record) and Person 5 (result screen). Field names/types match
 * the locked contract exactly — confidence is a Float per the doc.
 */
data class ClassificationResult(
    val result: String,
    val confidence: Float,
    val calibrated: Boolean,
    val reason: String? = null,
) {
    companion object {
        fun inconclusive(reason: String, calibrated: Boolean = false): ClassificationResult =
            ClassificationResult(
                result = TestOutcome.INCONCLUSIVE.label,
                confidence = 0f,
                calibrated = calibrated,
                reason = reason,
            )
    }

    override fun toString(): String {
        val base = "ClassificationResult(result: $result, confidence: ${"%.2f".format(confidence)}, " +
            "calibrated: $calibrated"
        return if (reason != null) "$base, reason: $reason)" else "$base)"
    }
}

/** Digital evidence record (Person 3), stored and searched by Person 4, shown by Person 5.
 *  Room-annotated so Person 4's [database.AppDatabase] can persist it directly — every
 *  field through [signature] is the locked wire contract; [localImageUri] is an extra
 *  persisted column, local storage only, that lets Verify re-read the original photo. */
@androidx.room.Entity(tableName = "digital_test_records")
data class DigitalTestRecord(
    @androidx.room.PrimaryKey
    val testId: String,
    val operatorId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val result: String,
    val confidence: Float,
    val imageHash: String,
    val recordHash: String,
    val signature: String,
    val localImageUri: String = "",
    val locationAddress: String = "",
    val isSynced: Boolean = false,
)

/** A simple RGB color sample (0-255 per channel) — internal to the classification pipeline. */
data class RgbColor(val r: Double, val g: Double, val b: Double) {
    fun scaled(gr: Double, gg: Double, gb: Double): RgbColor = RgbColor(
        (r * gr).coerceIn(0.0, 255.0),
        (g * gg).coerceIn(0.0, 255.0),
        (b * gb).coerceIn(0.0, 255.0),
    )

    override fun toString(): String =
        "RGB(${"%.0f".format(r)}, ${"%.0f".format(g)}, ${"%.0f".format(b)})"
}

/** CIE Lab color — internal to the classification pipeline's colour-distance comparisons. */
data class LabColor(val l: Double, val a: Double, val b: Double) {
    fun distanceTo(other: LabColor): Double {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return sqrt(dl * dl + da * da + db * db)
    }

    override fun toString(): String =
        "Lab(${"%.1f".format(l)}, ${"%.1f".format(a)}, ${"%.1f".format(b)})"
}

/** Why the reference-card detector failed, if it did — internal to the classification pipeline. */
enum class CardDetectionFailure { NOT_FOUND, TOO_SMALL, LOW_CONFIDENCE_PATCH_MATCH }

class CardDetectionResult private constructor(
    val found: Boolean,
    val left: Int?,
    val top: Int?,
    val width: Int?,
    val height: Int?,
    val failure: CardDetectionFailure?,
) {
    companion object {
        fun found(left: Int, top: Int, width: Int, height: Int): CardDetectionResult =
            CardDetectionResult(true, left, top, width, height, null)

        fun notFound(failure: CardDetectionFailure?): CardDetectionResult =
            CardDetectionResult(false, null, null, null, null, failure)
    }
}

data class CalibrationResult(
    val applied: Boolean,
    val gainR: Double = 1.0,
    val gainG: Double = 1.0,
    val gainB: Double = 1.0,
) {
    companion object {
        val NONE = CalibrationResult(applied = false)
    }
}
