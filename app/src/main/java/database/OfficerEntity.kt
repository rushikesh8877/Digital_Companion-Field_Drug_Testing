package database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists registered officers in the local Room database.
 *
 * Enforces a strict SQLite UNIQUE constraint on (departmentNormalized, badgeIdNormalized)
 * so that no two officers can ever be granted the same ID in the same department,
 * even when offline.
 */
@Entity(
    tableName = "officers",
    indices = [
        Index(value = ["departmentNormalized", "badgeIdNormalized"], unique = true),
        Index(value = ["uid"], unique = true),
    ],
)
data class OfficerEntity(
    @PrimaryKey
    val uid: String,
    val email: String,
    val badgeId: String,
    val name: String,
    val department: String,
    val departmentNormalized: String,
    val badgeIdNormalized: String,
    val registeredAt: Long = System.currentTimeMillis(),
)
