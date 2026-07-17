package app.astra.desktop.di

import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.AuthInterceptor
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.net.DesktopTokenAuthenticator
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.update.UpdateService
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DiscoverApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.FriendApi
import app.astra.mobile.core.network.GifApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
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
            // Teto da chamada inteira (fila incluida): nada pendura pra sempre.
            .callTimeout(75, TimeUnit.SECONDS)
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
        // Cliente PROPRIO, nunca newBuilder() do plain: newBuilder compartilha o
        // Dispatcher (5 requests/host). O boot dispara 5 chamadas autenticadas;
        // com token vencido as 5 seguram os slots dentro do authenticator e o
        // refresh (mesmo host) fica na fila pra sempre — deadlock do
        // "carregando o ceu…". Dispatcher separado = refresh sempre anda.
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
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
    single<ChannelApi> { get<Retrofit>(named("authed")).create(ChannelApi::class.java) }
    single<UploadApi> { get<Retrofit>(named("authed")).create(UploadApi::class.java) }
    single<VoiceApi> { get<Retrofit>(named("authed")).create(VoiceApi::class.java) }
    single<GifApi> { get<Retrofit>(named("authed")).create(GifApi::class.java) }
    single<DiscoverApi> { get<Retrofit>(named("authed")).create(DiscoverApi::class.java) }
    single<FriendApi> { get<Retrofit>(named("authed")).create(FriendApi::class.java) }

    single { DesktopSocket(get()) }
    single { AuthRepository(get(), get(), get()) }
    single { DesktopPrefs(get()) }
    // Auto-update DIY (zip-swap via GitHub Releases). Usa o OkHttp "plain" (mesmo
    // HTTPS que ja funciona no app) — o HttpURLConnection falhava no JRE empacotado.
    single { UpdateService(get(named("plain"))) }
}
