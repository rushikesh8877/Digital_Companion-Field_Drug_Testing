package com.sih.drugtestclassifier.database

import android.content.Context
import com.sih.drugtestclassifier.models.DigitalTestRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Database & Search module (Person 4's workstream) — NOT included among the
 * four files this project was assembled from, so this is a minimal
 * placeholder that closes the loop end-to-end: it persists
 * [DigitalTestRecord]s (plus, locally, the path to the original captured
 * image so Verify can re-read it) to a single JSON file under the app's
 * private storage. No network, no third-party database — works fully
 * offline, per the project's offline-first requirement.
 *
 * Swap this out for Room/SQLCipher (see project doc's "FUTURE SCOPE") if
 * Person 4 needs real search performance or encryption at rest; the
 * [TestRecordStorage] interface-shaped surface (loadAll/saveAll) is the seam
 * to replace without touching the rest of the app.
 */
data class StoredTestRecord(val record: DigitalTestRecord, val imageUri: String)

class TestRecordStorage(context: Context) {
    private val file = File(context.filesDir, "test_records.json")

    fun loadAll(): List<StoredTestRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        } catch (_: Exception) {
            // Corrupt or unreadable store — fail safe to an empty history rather than crash.
            emptyList()
        }
    }

    fun saveAll(entries: List<StoredTestRecord>) {
        val array = JSONArray()
        entries.forEach { array.put(toJson(it)) }
        file.writeText(array.toString(), Charsets.UTF_8)
    }

    private fun toJson(entry: StoredTestRecord): JSONObject = JSONObject().apply {
        put("testId", entry.record.testId)
        put("operatorId", entry.record.operatorId)
        put("timestamp", entry.record.timestamp)
        put("latitude", entry.record.latitude)
        put("longitude", entry.record.longitude)
        put("result", entry.record.result)
        put("confidence", entry.record.confidence.toDouble())
        put("imageHash", entry.record.imageHash)
        put("recordHash", entry.record.recordHash)
        put("signature", entry.record.signature)
        put("imageUri", entry.imageUri)
    }

    private fun fromJson(json: JSONObject): StoredTestRecord = StoredTestRecord(
        record = DigitalTestRecord(
            testId = json.getString("testId"),
            operatorId = json.getString("operatorId"),
            timestamp = json.getLong("timestamp"),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            result = json.getString("result"),
            confidence = json.getDouble("confidence").toFloat(),
            imageHash = json.getString("imageHash"),
            recordHash = json.getString("recordHash"),
            signature = json.getString("signature"),
        ),
        imageUri = json.optString("imageUri", ""),
    )
}
