package database

import com.sih.drugtestclassifier.models.DigitalTestRecord

class TestHistoryManager(
    private val repository: TestRepository
) {

    suspend fun getHistory(): List<DigitalTestRecord> {
        return repository.getAllTests()
    }

    suspend fun search(query: String): List<DigitalTestRecord> {
        return repository.searchTests(query)
    }

    suspend fun filter(result: String): List<DigitalTestRecord> {
        return repository.filterByResult(result)
    }
}
