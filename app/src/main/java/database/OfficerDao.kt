package database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfficerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOfficer(officer: OfficerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfficer(officer: OfficerEntity)

    @Query("""
        SELECT * FROM officers 
        WHERE departmentNormalized = :deptNorm AND badgeIdNormalized = :badgeNorm 
        LIMIT 1
    """)
    suspend fun getOfficerByDeptAndBadge(deptNorm: String, badgeNorm: String): OfficerEntity?

    @Query("SELECT * FROM officers WHERE uid = :uid LIMIT 1")
    suspend fun getOfficerByUid(uid: String): OfficerEntity?

    @Query("SELECT * FROM officers WHERE departmentNormalized = :deptNorm")
    suspend fun getOfficersByDepartment(deptNorm: String): List<OfficerEntity>

    @Query("SELECT * FROM officers ORDER BY department ASC, badgeId ASC")
    suspend fun getAllOfficers(): List<OfficerEntity>

    @Query("DELETE FROM officers WHERE uid = :uid")
    suspend fun deleteOfficer(uid: String)
}
