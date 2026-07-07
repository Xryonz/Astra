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

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
}

compose.desktop {
    application {
        mainClass = "app.astra.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Astra"
            packageVersion = "0.1.0"
        }
    }
}
