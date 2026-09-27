package app.ui.data

import com.sih.drugtestclassifier.location.LocationHelper
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.DigitalTestRecord
import com.sih.drugtestclassifier.models.TestImage
import com.sih.drugtestclassifier.security.VerificationResult
import kotlinx.coroutines.delay

/**
 * Local-only interface. Replace its implementation without changing screen
 * contracts. [verifyDetailed] was added during integration so the
 * verification screen can show the same three-way breakdown
 * (image hash / record hash / signature) that the security module's own
 * checks produce, instead of a single pass/fail boolean.
 */
interface TestRecordRepository {
    fun getRecords(): List<DigitalTestRecord>
    suspend fun classify(image: TestImage): ClassificationResult
    fun createRecord(
        operatorId: String,
        image: TestImage,
        classification: ClassificationResult,
        customLocation: LocationHelper.LatLon? = null,
    ): DigitalTestRecord
    fun saveRecord(record: DigitalTestRecord)
    fun verify(record: DigitalTestRecord): Boolean
    fun verifyDetailed(record: DigitalTestRecord): VerificationResult =
        VerificationResult(imageHashMatch = verify(record), recordHashMatch = verify(record), signatureValid = verify(record))
}

/**
 * Offline in-memory fake, useful for previews/tests or running the UI
 * before the real camera/classifier/security modules are wired in. The
 * real app uses com.sih.drugtestclassifier.integration.RealTestRecordRepository
 * instead (see MainActivity).
 */
class FakeTestRecordRepository : TestRecordRepository {
    private val records = mutableListOf<DigitalTestRecord>().apply { addAll(mockRecords()) }
    private val reasons = listOf("card not detected", "glare", "poor lighting")
    private var nextOutcome = 0

    override fun getRecords(): List<DigitalTestRecord> = records.toList().sortedByDescending { it.timestamp }

    override suspend fun classify(image: TestImage): ClassificationResult {
        delay(1_300)
        // Cycle deterministic demo cases so every edge case is reachable without hardware.
        val outcome = nextOutcome++ % 6
        return when (outcome) {
            0 -> ClassificationResult("Positive", .94f, true)
            1 -> ClassificationResult("Negative", .97f, true)
            else -> ClassificationResult("Inconclusive", .42f, false)
        }
    }

    fun reasonFor(result: ClassificationResult): String? =
        if (result.result == "Inconclusive") reasons[(nextOutcome - 1).coerceAtLeast(0) % reasons.size] else null

    override fun createRecord(
        operatorId: String,
        image: TestImage,
        classification: ClassificationResult,
        customLocation: LocationHelper.LatLon?,
    ): DigitalTestRecord {
        val id = "ST-${image.capturedAt.toString().takeLast(8)}"
        val lat = customLocation?.latitude ?: 20.0059
        val lon = customLocation?.longitude ?: 73.7600
        val address = customLocation?.address?.ifBlank { null } ?: "College Road, Nashik"
        return DigitalTestRecord(
            testId = id,
            operatorId = operatorId.ifBlank { "OP-1042" },
            timestamp = image.capturedAt,
            latitude = lat,
            longitude = lon,
            result = classification.result,
            confidence = classification.confidence,
            imageHash = "SHA256:8f2a…d91c",
            recordHash = "SHA256:3b7e…aa40",
            signature = "ED25519:MEUCIQD…QIg==",
            locationAddress = address,
        )
    }

    override fun saveRecord(record: DigitalTestRecord) {
        records.removeAll { it.testId == record.testId }
        records.add(0, record)
    }

    override fun verify(record: DigitalTestRecord): Boolean = !record.testId.endsWith("7")

    private fun mockRecords(): List<DigitalTestRecord> {
        val now = System.currentTimeMillis()
        val outcomes = listOf("Positive", "Negative", "Inconclusive", "Negative", "Positive", "Inconclusive", "Negative", "Positive", "Inconclusive")
        val sampleLocations = listOf(
            "Makhmalabad, Nashik",
            "Madhur Sweets, College Road, Nashik",
            "Panchavati, Nashik",
            "Gangapur Road, Nashik",
            "Mumbai Naka, Nashik",
        )
        return outcomes.mapIndexed { index, result ->
            val time = now - index * 86_400_000L
            DigitalTestRecord(
                testId = "ST-${(10000001 + index)}",
                operatorId = listOf("OP-1042", "OP-2088", "OP-1042")[index % 3],
                timestamp = time,
                latitude = 20.0059 + index * .001,
                longitude = 73.7600 + index * .001,
                result = result,
                confidence = if (result == "Inconclusive") .42f else .91f + (index % 8) * .01f,
                imageHash = "SHA256:${"%04x".format(index * 193 + 0x8f2a)}…d91c",
                recordHash = "SHA256:${"%04x".format(index * 311 + 0x3b7e)}…aa40",
                signature = "ED25519:MEUCIQD${index}…QIg==",
                locationAddress = sampleLocations[index % sampleLocations.size],
            )
        }
    }
}
