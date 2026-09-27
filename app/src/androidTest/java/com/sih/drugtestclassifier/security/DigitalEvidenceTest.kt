package com.sih.drugtestclassifier.security

import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.DigitalTestRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalEvidenceTest {
    private val image = "dummy offline image bytes".toByteArray()

    private fun validRecord(): DigitalTestRecord {
        val imageHash = hashImage(image)
        val classification = ClassificationResult("Positive", 0.97f, calibrated = true)
        val canonical = buildCanonicalRecord("test-1", "operator-7", 1_750_000_000L, 12.5, 77.25, classification, imageHash)
        val recordHash = hashRecord(canonical)
        return DigitalTestRecord(
            "test-1", "operator-7", 1_750_000_000L, 12.5, 77.25,
            classification.result, classification.confidence, imageHash, recordHash, signRecordHash(recordHash),
        )
    }

    @Test fun validRecordPassesEveryCheck() {
        assertEquals(VerificationResult(true, true, true), verifyRecord(validRecord(), image))
    }

    @Test fun modifiedImageFailsImageHashCheck() {
        val result = verifyRecord(validRecord(), "changed image".toByteArray())
        assertFalse(result.imageHashMatch)
        assertTrue(result.recordHashMatch)
        assertTrue(result.signatureValid)
    }

    @Test fun modifiedRecordHashFailsHashAndSignatureChecks() {
        val result = verifyRecord(validRecord().copy(recordHash = "00"), image)
        assertTrue(result.imageHashMatch)
        assertFalse(result.recordHashMatch)
        assertFalse(result.signatureValid)
    }

    @Test fun modifiedSignatureFailsOnlySignatureCheck() {
        val result = verifyRecord(validRecord().copy(signature = "not-a-valid-signature"), image)
        assertTrue(result.imageHashMatch)
        assertTrue(result.recordHashMatch)
        assertFalse(result.signatureValid)
    }
}
