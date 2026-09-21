package app.astra.mobile.core.di

import android.content.Context
import androidx.room.Room
import app.astra.mobile.core.db.AstraDatabase
import app.astra.mobile.core.db.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AstraDatabase =
        Room.databaseBuilder(context, AstraDatabase::class.java, "astra.db")

            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMessageDao(db: AstraDatabase): MessageDao = db.messageDao()
}
