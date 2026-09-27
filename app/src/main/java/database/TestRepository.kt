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
}