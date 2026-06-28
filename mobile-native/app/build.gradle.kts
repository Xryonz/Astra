plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.astra.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.astra.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Backend Astra existente (mesma URL do VITE_API_URL do app web,
        // .env.production). O Railway mantem o nome antigo "umbra". Termina em "/".
        buildConfigField("String", "BASE_URL", "\"https://umbra-api-production.up.railway.app/\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // LiveKit usa java.time; minSdk 24 precisa de desugaring p/ rodar em <26.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Renomeia o APK de saida: debug -> Astra.apk, release -> Astra-release.apk.
    applicationVariants.all {
        val variantName = name
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                if (variantName == "release") "Astra-release.apk" else "Astra.apk"
        }
    }
}

dependencies {
    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lucide.icons) // icones Lucide (mesma familia do web)
    implementation(libs.androidx.browser) // Custom Tabs (OAuth Google)
    debugImplementation(libs.androidx.ui.tooling)

    // DI — Hilt (via KSP)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Rede — Retrofit + kotlinx.serialization + OkHttp
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.android)

    // Imagens + storage local
    implementation(libs.coil.compose)
    implementation(libs.coil.gif) // decoder GIF/WebP animado (avatar/banner animados)
    implementation(libs.androidx.datastore.preferences)

    // Realtime — Socket.io client (protocolo Engine.io, fala com o server v4)
    implementation(libs.socketio.client)

    // Voz/video — LiveKit Android (WebRTC nativo). Mesmo backend /api/voice/token do web.
    implementation(libs.livekit.android)
    // VideoTrackView (render de video no Compose, cuida do EGL/SurfaceView/lifecycle)
    implementation(libs.livekit.android.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
