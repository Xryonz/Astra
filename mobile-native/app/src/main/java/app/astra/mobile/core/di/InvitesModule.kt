package app.astra.mobile.core.di

import app.astra.mobile.feature.invite.data.InvitesRepositoryImpl
import app.astra.mobile.feature.invite.domain.InvitesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InvitesModule {
    @Binds
    @Singleton
    abstract fun bindInvitesRepository(impl: InvitesRepositoryImpl): InvitesRepository
}
