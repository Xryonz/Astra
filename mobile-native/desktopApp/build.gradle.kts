import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))

    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
}

providers.gradleProperty("astra.dist").orNull?.let {
    layout.buildDirectory.set(file(it.ifBlank { "C:/Astra/build" }))
}
providers.gradleProperty("astra.distDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val astraVersion: String = providers.gradleProperty("astraVersion").get()

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.lucide.icons.jvm)
    implementation(libs.socketio.client)
    implementation(libs.rikkaui.foundation)
    implementation(libs.rikkaui.components)
    implementation(libs.jna.platform)
    implementation(libs.mp3spi)
    implementation(libs.vorbisspi)
    implementation(libs.compottie.lite)
    implementation(libs.nucleus.notification.windows)
}

val motorFonte = project.file("../../sidecar-voz-rs")
val recursosDoPacote = project.file("appResources")
val motorSaida = recursosDoPacote.resolve("windows/astra-voz.exe")
val alvoDoCargo = providers.environmentVariable("CARGO_TARGET_DIR")
    .map { File(it) }
    .orElse(motorFonte.resolve("target"))
val compilarMotorDeVoz = tasks.register("compilarMotorDeVoz") {
    inputs.dir(motorFonte.resolve("src")).withPropertyName("fonte")
    inputs.files(
        motorFonte.resolve("Cargo.toml"),
        motorFonte.resolve("Cargo.lock"),
        motorFonte.resolve("build.rs"),
        project.file("icons/astra.ico"),
    ).withPropertyName("receita")
    outputs.file(motorSaida)
    doLast {
        logger.lifecycle("Compilando o motor de voz (Rust) ...")
        val p = ProcessBuilder("cargo", "build", "--release", "--locked")
            .directory(motorFonte)
            .redirectErrorStream(true)
            .start()
        val saida = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) {
            throw GradleException("cargo build falhou. Rust instalado? Saida:\n$saida")
        }
        alvoDoCargo.get().resolve("release/astra-voz.exe").copyTo(motorSaida, overwrite = true)
        logger.lifecycle("astra-voz.exe -> ${motorSaida.length() / 1024} KB")
    }
}

tasks.matching {
    it.name == "createDistributable" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name == "prepareAppResources"
}.configureEach { dependsOn(compilarMotorDeVoz) }

tasks.withType<AbstractJPackageTask>().configureEach {
    inputs.files(recursosDoPacote)
        .withPropertyName("recursosDoPacote")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.withType<AbstractJLinkTask>().configureEach {
    freeArgs.add("--include-locales=pt,en")
}

tasks.register("zipDistributable") {
    dependsOn("createDistributable")
    val imagem = layout.buildDirectory.dir("compose/binaries/main/app")
    val pacote = layout.buildDirectory.file("Astra-$astraVersion-win-x64.zip")
    val jarDosModulos = Regex("""^(${project.name}|${project(":shared").name})-[0-9a-f]+\.jar$""")
    inputs.dir(imagem)
    outputs.file(pacote)
    doLast {
        val horaFixa = LocalDateTime.of(1980, 2, 1, 0, 0)
        ZipOutputStream(pacote.get().asFile.outputStream().buffered()).use { zip ->
            fun incluir(arquivo: File, nome: String) {
                val entrada = ZipEntry(if (arquivo.isDirectory) "$nome/" else nome)
                entrada.timeLocal = horaFixa
                if (arquivo.isFile && jarDosModulos.matches(arquivo.name)) {
                    val bytes = arquivo.readBytes()
                    entrada.method = ZipEntry.STORED
                    entrada.size = bytes.size.toLong()
                    entrada.compressedSize = bytes.size.toLong()
                    entrada.crc = CRC32().apply { update(bytes) }.value
                    zip.putNextEntry(entrada)
                    zip.write(bytes)
                } else {
                    zip.putNextEntry(entrada)
                    if (arquivo.isFile) arquivo.inputStream().use { it.copyTo(zip) }
                }
                zip.closeEntry()
                if (arquivo.isDirectory) {
                    arquivo.listFiles().orEmpty().sortedBy { it.name }.forEach { incluir(it, "$nome/${it.name}") }
                }
            }
            imagem.get().asFile.listFiles().orEmpty().sortedBy { it.name }.forEach { incluir(it, it.name) }
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.astra.desktop.MainKt"
        jvmArgs += "-Dastra.version=$astraVersion"
        jvmArgs += "-Dastra.canal=${providers.gradleProperty("astra.canal").orNull ?: "dev"}"
        jvmArgs += "-Dsun.java2d.d3d=false"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        val gcProfile = providers.gradleProperty("astra.gc").orNull ?: "g1"
        if (gcProfile == "zgc") {
            jvmArgs += "-XX:+UseZGC"
        } else {
            jvmArgs += "-XX:+UseG1GC"
            jvmArgs += "-XX:MaxGCPauseMillis=8"
            jvmArgs += "-XX:+ExplicitGCInvokesConcurrent"
        }

        val empacotando = gradle.startParameter.taskNames.any { alvo ->
            listOf("distributable", "package", "dmg", "msi", "deb").any { it in alvo.lowercase() }
        } || providers.gradleProperty("astra.distDir").isPresent
        if (empacotando) {
            jvmArgs += "-XX:+AutoCreateSharedArchive"
            jvmArgs += "-XX:SharedArchiveFile=\$APPDIR/astra-cds.jsa"
            jvmArgs += "-XX:ErrorFile=\$APPDIR/falha-jvm-%p.log"
        }

        if (providers.gradleProperty("astra.multi").isPresent) {
            jvmArgs += "-Dastra.multi=${providers.gradleProperty("astra.multi").get().ifBlank { "1" }}"
        }

        if (providers.gradleProperty("astra.medir").isPresent) {
            jvmArgs += "-Dastra.medir=${providers.gradleProperty("astra.medir").get()}"
        }

        if (providers.gradleProperty("astra.diag").isPresent) {
            jvmArgs += "-Dskiko.fps.enabled=true"
            jvmArgs += "-Dskiko.fps.longFrames.show=true"
            jvmArgs += "-Dskiko.fps.longFrames.millis=17"
        }
        jvmArgs += "-Xmx1g"
        jvmArgs += "-XX:MinHeapFreeRatio=10"
        jvmArgs += "-XX:MaxHeapFreeRatio=25"
        jvmArgs += "-XX:G1PeriodicGCInterval=20000"
        jvmArgs += "-XX:GCTimeRatio=4"
        jvmArgs += "-XX:MaxMetaspaceSize=256m"
        if (providers.gradleProperty("jfr").isPresent) {
            jvmArgs += "-XX:StartFlightRecording=duration=120s,filename=astra-profile.jfr,settings=profile,dumponexit=true"
        }
        if (providers.gradleProperty("astra.nmt").isPresent) {
            jvmArgs += "-XX:NativeMemoryTracking=summary"
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Astra"
            packageVersion = astraVersion
            vendor = "Astra"
            description = "Astra"
            copyright = "Astra"
            modules(
                "jdk.httpserver",
                "java.management",
                "jdk.management",
                "jdk.accessibility",
                "jdk.localedata",
                "jdk.jfr",
            )
            appResourcesRootDir.set(recursosDoPacote)
            windows {
                iconFile.set(project.file("icons/astra.ico"))
            }
        }
    }
}
