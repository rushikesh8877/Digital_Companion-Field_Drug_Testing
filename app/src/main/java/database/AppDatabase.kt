package database

import com.sih.drugtestclassifier.models.DigitalTestRecord

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DigitalTestRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun testDao(): TestDao
}