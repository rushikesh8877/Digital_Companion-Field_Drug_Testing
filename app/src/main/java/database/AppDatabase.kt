package database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sih.drugtestclassifier.models.DigitalTestRecord

@Database(
    entities = [
        DigitalTestRecord::class,
        OfficerEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun testDao(): TestDao
    abstract fun officerDao(): OfficerDao
}