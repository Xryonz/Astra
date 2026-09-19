package app.astra.mobile.core.di

import app.astra.mobile.feature.friends.data.FriendsRepositoryImpl
import app.astra.mobile.feature.friends.domain.FriendsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendsModule {
    @Binds
    @Singleton
    abstract fun bindFriendsRepository(impl: FriendsRepositoryImpl): FriendsRepository
}
