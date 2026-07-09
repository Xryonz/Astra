package app.astra.desktop.di

import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.mobile.core.network.AuthApi
import app.astra.shared.AstraShared
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

// Grafo do desktop (Koin). Espelha o wiring dos modulos Hilt do Android:
// Json -> OkHttp -> Retrofit -> APIs -> repositorios.
val appModule = module {
    single { Json { ignoreUnknownKeys = true; explicitNulls = false } }

    single {
        OkHttpClient.Builder()
            // Render free acorda em ate ~50s do sono; timeouts folgados no connect.
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(AstraShared.BASE_URL)
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<AuthApi> { get<Retrofit>().create(AuthApi::class.java) }
    single { SessionStore() }
    single { AuthRepository(get(), get(), get()) }
}
