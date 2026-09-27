package com.sih.drugtestclassifier.security

/*
 * Digital Evidence & Security module (Person 3's workstream).
 * Ported unchanged from the original DigitalEvidence.kt — only the local
 * copies of TestImage/ClassificationResult/DigitalTestRecord were removed
 * in favour of the project's single shared contract package
 * (com.sih.drugtestclassifier.models), so every module hashes/signs/reads
 * the exact same record shape. VerificationResult is kept here since it's
 * an internal detail of this module, not one of the locked contracts.
 */

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.DigitalTestRecord
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature

data class VerificationResult(
    val imageHashMatch: Boolean,
    val recordHashMatch: Boolean,
    val signatureValid: Boolean,
) {
    val allPassed: Boolean get() = imageHashMatch && recordHashMatch && signatureValid
}

private const val KEY_ALIAS = "verifai.digital-evidence.ecdsa"
private const val HASH_ALGORITHM = "SHA-256"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

fun hashImage(imageBytes: ByteArray): String = sha256(imageBytes)

/** Length-prefixed UTF-8 fields avoid delimiter ambiguity; field order is part of the format. */
fun buildCanonicalRecord(
    testId: String,
    operatorId: String,
    timestamp: Long,
    latitude: Double,
    longitude: Double,
    classification: ClassificationResult,
    imageHash: String,
): String {
    val fields = listOf(
        testId,
        operatorId,
        timestamp.toString(),
        canonicalNumber(latitude),
        canonicalNumber(longitude),
        classification.result,
        canonicalNumber(classification.confidence.toDouble()),
        imageHash,
    )
    return fields.joinToString(separator = "") { field ->
        val bytes = field.toByteArray(StandardCharsets.UTF_8)
        "${bytes.size}:$field"
    }
}

fun hashRecord(canonicalRecord: String): String =
    sha256(canonicalRecord.toByteArray(StandardCharsets.UTF_8))

/** Creates the signing key once in Android Keystore; its private half never leaves the device. */
fun signRecordHash(recordHash: String): String {
    val keyStore = loadKeyStore()
    if (!keyStore.containsAlias(KEY_ALIAS)) {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        generator.generateKeyPair()
    }
    val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
    val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
    signer.initSign(privateKey)
    signer.update(recordHash.toByteArray(StandardCharsets.UTF_8))
    return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
}

/** Rebuilds the digest from record fields and verifies the signature using the Keystore public key. */
fun verifyRecord(record: DigitalTestRecord, originalImageBytes: ByteArray): VerificationResult {
    val imageMatches = constantTimeEquals(hashImage(originalImageBytes), record.imageHash)
    val classification = ClassificationResult(record.result, record.confidence, calibrated = false)
    val canonical = buildCanonicalRecord(
        record.testId,
        record.operatorId,
        record.timestamp,
        record.latitude,
        record.longitude,
        classification,
        record.imageHash,
    )
    val computedRecordHash = hashRecord(canonical)
    val recordMatches = constantTimeEquals(computedRecordHash, record.recordHash)
    val signatureValid = try {
        val publicKey = loadKeyStore().getCertificate(KEY_ALIAS)?.publicKey ?: return VerificationResult(
            imageMatches, recordMatches, false,
        )
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(record.recordHash.toByteArray(StandardCharsets.UTF_8))
        verifier.verify(Base64.decode(record.signature, Base64.NO_WRAP))
    } catch (_: Exception) {
        false
    }
    return VerificationResult(imageMatches, recordMatches, signatureValid)
}

private fun loadKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

private fun canonicalNumber(value: Double): String {
    require(value.isFinite()) { "Canonical record numbers must be finite" }
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance(HASH_ALGORITHM)
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(left.toByteArray(StandardCharsets.US_ASCII), right.toByteArray(StandardCharsets.US_ASCII))
