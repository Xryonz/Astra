import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :shared — codigo comum (dominio/dados) compartilhado entre :app (Android) e
// :desktopApp. Kotlin/JVM puro: os dois alvos rodam sobre a JVM, entao NAO
// precisamos de KMP/expect-actual (o AGP 9 quebrou o KMP-Android classico).
// REGRA: nada de android.* nem Compose aqui -> so tipos puros, rede, serializacao.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // Mesma linha do :app e :desktopApp (JDK do build e 21) pra evitar
    // "Inconsistent JVM Target" e manter os bytecodes alinhados.
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
