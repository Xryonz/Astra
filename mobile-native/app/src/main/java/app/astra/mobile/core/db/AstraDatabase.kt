package app.astra.mobile.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

// v6: +authorFont no MessageEntity (fonte do autor renderizada na mensagem).
// Migracao destrutiva (DatabaseModule) so limpa o cache; recarrega da rede.
@Database(entities = [MessageEntity::class], version = 6, exportSchema = false)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
