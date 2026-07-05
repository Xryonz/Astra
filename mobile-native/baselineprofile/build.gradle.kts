// Modulo de TESTE que gera o Baseline Profile do :app rodando o journey de
// startup num device real (fluxo manual: o plugin androidx.baselineprofile
// quebra o KSP na variante nonMinified que ele cria com o AGP 9 em modo
// legado; aqui usamos um build type "benchmark" comum + BaselineProfileRule
// e copiamos o txt gerado pra app/src/main/baseline-prof.txt).
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.astra.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Casa com o build type "benchmark" do :app (mesmo nome = match direto).
        create("benchmark") {
            isDebuggable = true
            // Build type custom NAO herda assinatura: sem isso o APK de teste
            // sai sem certificado e o install falha (INSTALL_PARSE_FAILED_NO_CERTIFICATES).
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
