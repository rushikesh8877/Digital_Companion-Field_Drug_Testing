package database

import com.sih.drugtestclassifier.models.DigitalTestRecord

suspend fun insertSampleData(repository: TestRepository) {

    val testRecords = listOf(

        DigitalTestRecord(
            testId = "TEST001",
            operatorId = "OP001",
            timestamp = System.currentTimeMillis() - 3000,
            latitude = 20.0059,
            longitude = 73.7900,
            result = "Negative",
            confidence = 0.94f,
            imageHash = "sample_image_hash_001",
            recordHash = "sample_record_hash_001",
            signature = "sample_signature_001"
        ),

        DigitalTestRecord(
            testId = "TEST002",
            operatorId = "OP002",
            timestamp = System.currentTimeMillis() - 2000,
            latitude = 20.0060,
            longitude = 73.7901,
            result = "Positive",
            confidence = 0.91f,
            imageHash = "sample_image_hash_002",
            recordHash = "sample_record_hash_002",
            signature = "sample_signature_002"
        ),

        DigitalTestRecord(
            testId = "TEST003",
            operatorId = "OP001",
            timestamp = System.currentTimeMillis() - 1000,
            latitude = 20.0061,
            longitude = 73.7902,
            result = "Inconclusive",
            confidence = 0.52f,
            imageHash = "sample_image_hash_003",
            recordHash = "sample_record_hash_003",
            signature = "sample_signature_003"
        )
    )

    testRecords.forEach { record ->
        repository.addTest(record)
    }
}