package database

import com.sih.drugtestclassifier.models.DigitalTestRecord

class TestRepository(
    private val dao: TestDao
) {

    suspend fun addTest(record: DigitalTestRecord) {
        dao.insertTest(record)
    }

    suspend fun getAllTests(): List<DigitalTestRecord> {
        return dao.getAllTests()
    }

    suspend fun searchTests(query: String): List<DigitalTestRecord> {
        return dao.searchTests(query)
    }

    suspend fun filterByResult(result: String): List<DigitalTestRecord> {
        return dao.filterByResult(result)
    }

    suspend fun getUnsyncedTests(): List<DigitalTestRecord> {
        return dao.getUnsyncedTests()
    }

    suspend fun markAsSynced(testId: String) {
        dao.markAsSynced(testId)
    }

    suspend fun insertTests(records: List<DigitalTestRecord>) {
        dao.insertTests(records)
    }
}