package app.astra.desktop.di

import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.AuthInterceptor
import app.astra.desktop.net.DesktopTokenAuthenticator
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UserApi
import app.astra.shared.AstraShared
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

// Grafo do desktop (Koin). Dois Retrofits, como no Android:
// - "plain": sem auth — login/refresh (o authenticator usa este, sem ciclo).
// - "authed": Bearer + renovacao automatica no 401 — todo o resto.
val appModule = module {
    single { Json { ignoreUnknownKeys = true; explicitNulls = false } }
    single { SessionStore() }

    single(named("plain")) {
        OkHttpClient.Builder()
            // Render free acorda em ate ~50s do sono; timeouts folgados no connect.
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single(named("plain")) {
        Retrofit.Builder()
            .baseUrl(AstraShared.BASE_URL)
            .client(get(named("plain")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<AuthApi> { get<Retrofit>(named("plain")).create(AuthApi::class.java) }
    single<RefreshApi> { get<Retrofit>(named("plain")).create(RefreshApi::class.java) }

    single(named("authed")) {
        get<OkHttpClient>(named("plain")).newBuilder()
            .addInterceptor(AuthInterceptor(get()))
            .authenticator(DesktopTokenAuthenticator(get(), lazy { get<RefreshApi>() }))
            .build()
    }

    single(named("authed")) {
        Retrofit.Builder()
            .baseUrl(AstraShared.BASE_URL)
            .client(get(named("authed")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<ServerApi> { get<Retrofit>(named("authed")).create(ServerApi::class.java) }
    single<UserApi> { get<Retrofit>(named("authed")).create(UserApi::class.java) }
    single<DmApi> { get<Retrofit>(named("authed")).create(DmApi::class.java) }

    single { AuthRepository(get(), get(), get()) }
}
