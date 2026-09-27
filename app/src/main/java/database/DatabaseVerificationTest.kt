package database

import kotlinx.coroutines.runBlocking

fun verifyDatabase(repository: TestRepository) {

    runBlocking {

        // 1. Get all records
        val allTests = repository.getAllTests()
        println("ALL TESTS: ${allTests.size}")

        // 2. Search
        val searchResult = repository.searchTests("TEST001")
        println("SEARCH RESULT: ${searchResult.size}")

        // 3. Filter Positive
        val positiveTests = repository.filterByResult("Positive")
        println("POSITIVE TESTS: ${positiveTests.size}")

        // 4. Filter Negative
        val negativeTests = repository.filterByResult("Negative")
        println("NEGATIVE TESTS: ${negativeTests.size}")

        // 5. Filter Inconclusive
        val inconclusiveTests =
            repository.filterByResult("Inconclusive")

        println(
            "INCONCLUSIVE TESTS: ${inconclusiveTests.size}"
        )
    }
}