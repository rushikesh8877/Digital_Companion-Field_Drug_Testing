package com.sih.drugtestclassifier.integration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.ui.data.TestRecordRepository
import com.sih.drugtestclassifier.location.LocationHelper
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.DigitalTestRecord
import com.sih.drugtestclassifier.models.KitProfile
import com.sih.drugtestclassifier.models.KitProfileLoader
import com.sih.drugtestclassifier.models.TestImage
import com.sih.drugtestclassifier.pipeline.ClassificationPipeline
import com.sih.drugtestclassifier.security.VerificationResult
import com.sih.drugtestclassifier.security.buildCanonicalRecord
import com.sih.drugtestclassifier.security.hashImage
import com.sih.drugtestclassifier.security.hashRecord
import com.sih.drugtestclassifier.security.signRecordHash
import com.sih.drugtestclassifier.security.verifyRecord
import database.DatabaseProvider
import database.TestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The integration layer: this is what turns the five separate teammate
 * modules (camera, classification/processing, security, database, UI) into
 * one real end-to-end app instead of five independent demos.
 *
 * Implements Person 5's [TestRecordRepository] contract, but every method
 * now does the real thing:
 *  - [classify] decodes the actual captured photo and runs Person 2's
 *    [ClassificationPipeline] (HSV/Lab colour calibration + classification)
 *    on it.
 *  - [createRecord] hashes the real image bytes, reads a best-effort GPS
 *    fix, and signs the record hash in the Android Keystore using Person
 *    3's security module.
 *  - [saveRecord] / [getRecords] persist to Person 4's real Room database
 *    (`database.AppDatabase` / `database.TestRepository`).
 *  - [verifyDetailed] re-reads the original photo (via the record's
 *    [DigitalTestRecord.localImageUri], a local-only column Room persists
 *    alongside the locked contract fields) and re-runs the same
 *    hash/signature checks, so a tampered record is genuinely detected
 *    rather than simulated.
 */
class RealTestRecordRepository(
    private val context: Context,
    private val syncManager: com.sih.drugtestclassifier.sync.FirebaseSyncManager = com.sih.drugtestclassifier.sync.FirebaseSyncManager.getInstance(context),
) : TestRecordRepository {

    private val kit: KitProfile by lazy { KitProfileLoader.loadDefault(context.assets) }
    private val pipeline: ClassificationPipeline by lazy { ClassificationPipeline(kit = kit) }
    private val dbRepository = TestRepository(DatabaseProvider.getDatabase(context).testDao())

    @Volatile
    private var cachedRecords: List<DigitalTestRecord> = runBlocking { dbRepository.getAllTests() }

    init {
        syncManager.setDataChangedListener {
            cachedRecords = runBlocking { dbRepository.getAllTests() }
        }
    }

    override fun getRecords(): List<DigitalTestRecord> = cachedRecords.sortedByDescending { it.timestamp }

    override suspend fun classify(image: TestImage): ClassificationResult = withContext(Dispatchers.Default) {
        val bitmap = decodeBitmap(image.imageUri)
            ?: return@withContext ClassificationResult.inconclusive("Could not read the captured image.")
        try {
            pipeline.classifyImage(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun createRecord(
        operatorId: String,
        image: TestImage,
        classification: ClassificationResult,
        customLocation: LocationHelper.LatLon?,
    ): DigitalTestRecord {
        val bytes = readBytes(image.imageUri) ?: ByteArray(0)
        val imageHash = hashImage(bytes)
        val location = customLocation ?: LocationHelper.lastKnown(context)
        val testId = "ST-${image.capturedAt.toString().takeLast(8)}"
        val resolvedOperatorId = operatorId.ifBlank { "OP-UNKNOWN" }

        val canonical = buildCanonicalRecord(
            testId = testId,
            operatorId = resolvedOperatorId,
            timestamp = image.capturedAt,
            latitude = location.latitude,
            longitude = location.longitude,
            classification = classification,
            imageHash = imageHash,
        )
        val recordHash = hashRecord(canonical)
        val signature = signRecordHash(recordHash)

        return DigitalTestRecord(
            testId = testId,
            operatorId = resolvedOperatorId,
            timestamp = image.capturedAt,
            latitude = location.latitude,
            longitude = location.longitude,
            result = classification.result,
            confidence = classification.confidence,
            imageHash = imageHash,
            recordHash = recordHash,
            signature = signature,
            localImageUri = image.imageUri,
            locationAddress = location.address,
        )
    }

    override fun saveRecord(record: DigitalTestRecord) = runBlocking {
        dbRepository.addTest(record)
        cachedRecords = dbRepository.getAllTests()
        syncManager.onRecordSavedLocally(record)
    }

    override fun verify(record: DigitalTestRecord): Boolean = verifyDetailed(record).allPassed

    override fun verifyDetailed(record: DigitalTestRecord): VerificationResult {
        val bytes = record.localImageUri.takeIf { it.isNotBlank() }?.let { readBytes(it) }
        if (bytes == null) {
            // Original photo no longer on disk (or this record predates the column) — the
            // image-hash check can't be performed, but record-hash/signature checks still can.
            return verifyRecord(record, ByteArray(0)).copy(imageHashMatch = false)
        }
        return verifyRecord(record, bytes)
    }

    private fun decodeBitmap(imageUri: String): Bitmap? {
        val bytes = readBytes(imageUri) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun readBytes(imageUri: String): ByteArray? {
        return try {
            when {
                imageUri.startsWith("content://") ->
                    context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { it.readBytes() }
                imageUri.startsWith("file://") -> {
                    val path = Uri.parse(imageUri).path ?: return null
                    File(path).readBytes()
                }
                imageUri.startsWith("mock://") -> null
                else -> File(imageUri).readBytes()
            }
        } catch (_: Exception) {
            null
        }
    }
}
