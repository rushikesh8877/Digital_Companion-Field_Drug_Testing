package database

import com.sih.drugtestclassifier.models.DigitalTestRecord

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TestDao {

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertTest(record: DigitalTestRecord)

    @Query("SELECT * FROM digital_test_records ORDER BY timestamp DESC")
    suspend fun getAllTests(): List<DigitalTestRecord>

    @Query("""
        SELECT * FROM digital_test_records
        WHERE testId LIKE '%' || :query || '%'
        OR operatorId LIKE '%' || :query || '%'
        OR result LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    suspend fun searchTests(query: String): List<DigitalTestRecord>

    @Query("""
        SELECT * FROM digital_test_records
        WHERE result = :result
        ORDER BY timestamp DESC
    """)
    suspend fun filterByResult(result: String): List<DigitalTestRecord>
}