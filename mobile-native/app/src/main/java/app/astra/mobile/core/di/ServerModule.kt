package app.astra.mobile.core.di

import app.astra.mobile.feature.server.data.ServerRepositoryImpl
import app.astra.mobile.feature.server.domain.ServerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServerModule {
    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: ServerRepositoryImpl): ServerRepository
}
