package app.astra.mobile.core.di

import app.astra.mobile.BuildConfig
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.AuthInterceptor
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.FriendsApi
import app.astra.mobile.core.network.InvitesApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.TokenAuthenticator
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // backend pode mandar campos que o cliente ignora
        explicitNulls = false
    }

    private fun logging() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging())
        .authenticator(authenticator) // refresh transparente no 401
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideDmApi(retrofit: Retrofit): DmApi = retrofit.create(DmApi::class.java)

    @Provides
    @Singleton
    fun provideServerApi(retrofit: Retrofit): ServerApi = retrofit.create(ServerApi::class.java)

    @Provides
    @Singleton
    fun provideChannelApi(retrofit: Retrofit): ChannelApi = retrofit.create(ChannelApi::class.java)

    @Provides
    @Singleton
    fun provideVoiceApi(retrofit: Retrofit): VoiceApi = retrofit.create(VoiceApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideUploadApi(retrofit: Retrofit): UploadApi = retrofit.create(UploadApi::class.java)

    @Provides
    @Singleton
    fun provideFriendsApi(retrofit: Retrofit): FriendsApi = retrofit.create(FriendsApi::class.java)

    @Provides
    @Singleton
    fun provideInvitesApi(retrofit: Retrofit): InvitesApi = retrofit.create(InvitesApi::class.java)

    /**
     * RefreshApi roda num client SEPARADO e pelado (sem AuthInterceptor nem
     * Authenticator) pra que o /refresh nunca dispare outro ciclo de refresh.
     */
    @Provides
    @Singleton
    fun provideRefreshApi(json: Json): RefreshApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(logging())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(RefreshApi::class.java)
    }
}
