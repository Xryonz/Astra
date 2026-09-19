package app.astra.mobile.core.di

import app.astra.mobile.feature.channel.data.ChannelRepositoryImpl
import app.astra.mobile.feature.channel.domain.ChannelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChannelModule {
    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository
}
