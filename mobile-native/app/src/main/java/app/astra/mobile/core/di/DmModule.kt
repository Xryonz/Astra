package app.astra.mobile.core.di

import app.astra.mobile.feature.dm.data.DmRepositoryImpl
import app.astra.mobile.feature.dm.domain.DmRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DmModule {
    @Binds
    @Singleton
    abstract fun bindDmRepository(impl: DmRepositoryImpl): DmRepository
}
