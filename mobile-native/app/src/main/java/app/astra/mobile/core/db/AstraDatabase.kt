package app.astra.mobile.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class], version = 5, exportSchema = false)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
