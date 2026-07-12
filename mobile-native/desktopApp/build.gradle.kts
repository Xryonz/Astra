import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.zip.ZipFile

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
    // Icones Lucide (mesma lib/versao do :app Android) — variante -jvm. Os ~1.7k
    // ImageVectors ficam sob com.composables.icons.lucide.* (igual ao mobile).
    implementation(libs.lucide.icons.jvm)
    // Realtime: socket.io-client e Java puro (mesma lib do Android, mesmo backend).
    implementation(libs.socketio.client)
    // RikkaUI e Compose Multiplatform (foundation-only) -> componentes do mobile
    // (Input, Dialog, ...) rodam identicos no desktop.
    implementation(libs.rikkaui.foundation)
    implementation(libs.rikkaui.components)
    // DPAPI (CryptProtectData) pro SessionStore — tokens cifrados em repouso.
    implementation(libs.jna.platform)
    // Vidro/blur real (backdrop) — haze e CMP, mesma lib do Android.
    implementation(libs.haze)
    // Voz nativa (fase V1+): WebRTC pra JVM + natives do Windows por classifier.
    implementation(libs.webrtc.java)
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:windows-x86_64")
    // Signaling do LiveKit: runtime do protobuf. As classes Java ficam
    // COMMITADAS em src/main/java/livekit (geradas 1x na mao) porque o protoc,
    // como o jpackage, nao engole o path com acento do repo. Pra regenerar
    // (quando os .proto em src/main/proto mudarem): copiar os protos pra um
    // dir ASCII (ex: C:/astra-dist/proto-tmp, com google/protobuf/timestamp
    // e descriptor extraidos do jar do protobuf-java) e rodar:
    //   protoc --proto_path=C:/astra-dist/proto-tmp --java_out=<saida> \
    //     livekit_rtc.proto livekit_models.proto livekit_metrics.proto logger/options.proto
    implementation(libs.protobuf.java)
}

// Baixa o ffmpeg.exe (com ddagrab) pro appResources se faltar — o binario e
// grande e fica FORA do git. Num clone limpo: `./gradlew :desktopApp:fetchFfmpeg`
// antes de empacotar (o createDistributable ja depende dele). Build lgpl (sem os
// codecs GPL) porque so usamos captura+escala, nao encoders.
// Paths resolvidos no topo (receiver Project); o lambda da task so ve estas vals.
val ffmpegOut = project.file("appResources/windows/ffmpeg.exe")
val ffmpegZip = layout.buildDirectory.file("ffmpeg-dl.zip").get().asFile
val ffmpegUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-lgpl.zip"
val fetchFfmpeg = tasks.register("fetchFfmpeg") {
    outputs.file(ffmpegOut)
    onlyIf { !ffmpegOut.exists() }
    doLast {
        ffmpegZip.parentFile.mkdirs()
        logger.lifecycle("Baixando ffmpeg (ddagrab) ...")
        URI(ffmpegUrl).toURL().openStream().use { i -> ffmpegZip.outputStream().use { i.copyTo(it) } }
        ffmpegOut.parentFile.mkdirs()
        ZipFile(ffmpegZip).use { zf ->
            val e = zf.entries().asSequence().first { it.name.endsWith("bin/ffmpeg.exe") }
            zf.getInputStream(e).use { i -> ffmpegOut.outputStream().use { i.copyTo(it) } }
        }
        ffmpegZip.delete()
        logger.lifecycle("ffmpeg.exe -> ${ffmpegOut.length() / 1024 / 1024} MB")
    }
}
tasks.matching { it.name == "createDistributable" || it.name == "packageDistributionForCurrentOS" }
    .configureEach { dependsOn(fetchFfmpeg) }

compose.desktop {
    application {
        mainClass = "app.astra.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Astra"
            packageVersion = "0.1.0"
            // Recursos por-SO empacotados no app-image. appResources/windows/ffmpeg.exe
            // = capturador DXGI (ddagrab) da transmissao 60fps; em runtime sai em
            // System.getProperty("compose.application.resources.dir"). O binario e
            // gitignored (grande) — quem for buildar roda `:desktopApp:fetchFfmpeg`.
            appResourcesRootDir.set(project.file("appResources"))
            windows {
                // Logo do Astra (mesmo favicon.ico do site) no Astra.exe.
                iconFile.set(project.file("icons/astra.ico"))
            }
        }
    }
}
