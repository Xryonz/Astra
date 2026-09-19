@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val androidVersion: String = providers.gradleProperty("androidVersion").get()

val androidVersionCode: Int = androidVersion.split('.').let { p ->
    require(p.size == 3) { "androidVersion precisa ser maior.menor.correcao, veio \"$androidVersion\"" }
    val n = p.map { it.toIntOrNull() ?: error("androidVersion nao numerico: \"$androidVersion\"") }
    require(n.all { it in 0..99 }) { "cada parte de androidVersion vai ate 99, veio \"$androidVersion\"" }
    n[0] * 10_000 + n[1] * 100 + n[2]
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "app.astra.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.astra.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = androidVersionCode
        versionName = androidVersion

        buildConfigField("String", "BASE_URL", "\"https://astra-kwzc.onrender.com/\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "ETIQUETA_DE_ATUALIZACAO", "\"android-teste-v\"")
        }
        release {
            buildConfigField("String", "ETIQUETA_DE_ATUALIZACAO", "\"android-v\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        val variantName = name
        outputs.all {
            val saida = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val processador = saida.filters.firstOrNull { it.filterType == "ABI" }?.identifier
            saida.outputFileName = when (processador) {
                "arm64-v8a" -> "Astra-$androidVersion-android.apk"
                "armeabi-v7a" -> "Astra-$androidVersion-android-32bits.apk"
                else -> if (variantName == "release") "Astra-$androidVersion-android-universal.apk" else "Astra.apk"
            }
        }
    }
}

dependencies {
    constraints {
        val composeCore = "1.10.0"
        implementation("androidx.compose.ui:ui:$composeCore")
        implementation("androidx.compose.ui:ui-graphics:$composeCore")
        implementation("androidx.compose.ui:ui-tooling-preview:$composeCore")
        implementation("androidx.compose.ui:ui-tooling:$composeCore")
        implementation("androidx.compose.foundation:foundation:$composeCore")
        implementation("androidx.compose.foundation:foundation-layout:$composeCore")
        implementation("androidx.compose.animation:animation:$composeCore")
        implementation("androidx.compose.runtime:runtime:$composeCore")
    }

    implementation(project(":shared"))

    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lucide.icons)
    implementation(libs.haze)
    implementation(libs.androidx.browser)
    implementation(libs.rikkaui.foundation)
    implementation(libs.rikkaui.components)
    implementation(libs.androidx.emoji2.emojipicker)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.leakcanary.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.errorprone.annotations)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.socketio.client)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.livekit.android)
    implementation(libs.livekit.android.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
