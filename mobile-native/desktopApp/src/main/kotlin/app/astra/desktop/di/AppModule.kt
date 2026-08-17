package app.astra.desktop.di

import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.AuthInterceptor
import app.astra.desktop.net.DeviceInterceptor
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.XpStore
import app.astra.desktop.net.DesktopTokenAuthenticator
import app.astra.desktop.prefs.AvisosDaConta
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.update.UpdateService
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DiscoverApi
import app.astra.mobile.core.network.InviteApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.FriendApi
import app.astra.mobile.core.network.GifApi
import app.astra.mobile.core.network.BlockApi
import app.astra.mobile.core.network.BotApi
import app.astra.mobile.core.network.BotPersonaApi
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.BadgeApi
import app.astra.mobile.core.network.SearchApi
import app.astra.mobile.core.network.EmojiApi
import app.astra.mobile.core.network.SoundApi
import app.astra.mobile.core.network.StickerApi
import app.astra.mobile.core.network.MissionApi
import app.astra.mobile.core.network.XpApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.SessionApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.WishApi
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
            // X-Device-Id também aqui: login/register/refresh vivem no plain (#4).
            .addInterceptor(DeviceInterceptor(get()))
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
            .addInterceptor(DeviceInterceptor(get()))
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
    single<SessionApi> { get<Retrofit>(named("authed")).create(SessionApi::class.java) }
    single<DmApi> { get<Retrofit>(named("authed")).create(DmApi::class.java) }
    single<ChannelApi> { get<Retrofit>(named("authed")).create(ChannelApi::class.java) }
    single<UploadApi> { get<Retrofit>(named("authed")).create(UploadApi::class.java) }
    single<VoiceApi> { get<Retrofit>(named("authed")).create(VoiceApi::class.java) }
    single<GifApi> { get<Retrofit>(named("authed")).create(GifApi::class.java) }
    single<DiscoverApi> { get<Retrofit>(named("authed")).create(DiscoverApi::class.java) }
    single<InviteApi> { get<Retrofit>(named("authed")).create(InviteApi::class.java) }
    single<FriendApi> { get<Retrofit>(named("authed")).create(FriendApi::class.java) }
    single<SearchApi> { get<Retrofit>(named("authed")).create(SearchApi::class.java) }
    single<SoundApi> { get<Retrofit>(named("authed")).create(SoundApi::class.java) }
    single<StickerApi> { get<Retrofit>(named("authed")).create(StickerApi::class.java) }
    single<EmojiApi> { get<Retrofit>(named("authed")).create(EmojiApi::class.java) }
    single<XpApi> { get<Retrofit>(named("authed")).create(XpApi::class.java) }
    single<BadgeApi> { get<Retrofit>(named("authed")).create(BadgeApi::class.java) }
    single<MissionApi> { get<Retrofit>(named("authed")).create(MissionApi::class.java) }
    single<NotificationApi> { get<Retrofit>(named("authed")).create(NotificationApi::class.java) }
    single<WishApi> { get<Retrofit>(named("authed")).create(WishApi::class.java) }
    single<BotPersonaApi> { get<Retrofit>(named("authed")).create(BotPersonaApi::class.java) }
    single<BotApi> { get<Retrofit>(named("authed")).create(BotApi::class.java) }
    single<BlockApi> { get<Retrofit>(named("authed")).create(BlockApi::class.java) }

    single { DesktopSocket(get(), get()) }
    single { XpStore(get(), get()) }
    single { MissoesStore(get(), get()) }
    single { AuthRepository(get(), get(), get(), get(), get(), get()) }
    single { DesktopPrefs(get()) }
    single { AvisosDaConta(get()) }
    // Auto-update DIY (zip-swap via GitHub Releases). Usa o OkHttp "plain" (mesmo
    // HTTPS que já funciona no app) — o HttpURLConnection falhava no JRE empacotado.
    single { UpdateService(get(named("plain"))) }
}
