import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :desktopApp — cliente desktop do Astra (Compose Multiplatform / JVM).
// D0: so abre uma janela obsidiana. O codigo compartilhado (dominio/dados/UI)
// entra num :shared em D1+; por ora este modulo e standalone e NAO toca no :app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)   // compiler Compose (ship junto do Kotlin)
    alias(libs.plugins.jetbrains.compose) // Compose Multiplatform (compose.desktop)
}

kotlin {
    // Compose Desktop exige bytecode 11+. 17 = mesma linha do :app. A JDK do build
    // e 21, entao alinhamos compileJava (java{} abaixo) e compileKotlin no mesmo 17
    // pra nao dar "Inconsistent JVM Target" (a validacao do Gradle).
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// jpackage/jlink quebram com caminho non-ASCII no Windows (o repo mora em
// ".../Codigos e Loucuras/..."). Pra EMPACOTAR o .exe, redirecione o build deste
// modulo pra um path sem acento:
//   ./gradlew :desktopApp:createDistributable -Pastra.distDir=C:/astra-dist
// Sem a flag, nada muda (build normal em build/).
providers.gradleProperty("astra.distDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    // Rede: mesmas libs do Android (Retrofit vem via :shared; aqui o wiring).
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    // DI: Koin (Hilt nao roda fora do Android).
    implementation(libs.koin.core)
    // Imagens: Coil3 e KMP, mesmos artefatos do Android rodam no desktop.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Realtime: socket.io-client e Java puro (mesma lib do Android, mesmo backend).
    implementation(libs.socketio.client)
    // RikkaUI e Compose Multiplatform (foundation-only) -> componentes do mobile
    // (Input, Dialog, ...) rodam identicos no desktop.
    implementation(libs.rikkaui.foundation)
    implementation(libs.rikkaui.components)
}

compose.desktop {
    application {
        mainClass = "app.astra.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Astra"
            packageVersion = "0.1.0"
            windows {
                // Logo do Astra (mesmo favicon.ico do site) no Astra.exe.
                iconFile.set(project.file("icons/astra.ico"))
            }
        }
    }
}
