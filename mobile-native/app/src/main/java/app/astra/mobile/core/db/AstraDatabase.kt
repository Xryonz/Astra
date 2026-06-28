package app.astra.mobile.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Banco local do Astra. exportSchema=false por ora (sem migracoes ainda; quando
 * houver, ligar o export + pasta de schemas). version sobe a cada mudanca de
 * entidade.
 */
@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
