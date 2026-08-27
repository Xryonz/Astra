import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
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

val astraVersion = "0.14.0"

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
    implementation(libs.haze)
    implementation(libs.mp3spi)
    implementation(libs.vorbisspi)
}

val sidecarFonte = project.file("../../sidecar-voz")
val sidecarSaida = project.file("appResources/windows/astra-voz.exe")
val compilarSidecarVoz = tasks.register("compilarSidecarVoz") {
    inputs.dir(sidecarFonte).withPropertyName("fonte")
    outputs.file(sidecarSaida)
    doLast {
        sidecarSaida.parentFile.mkdirs()
        logger.lifecycle("Compilando o sidecar de voz (Go) ...")
        val p = ProcessBuilder("go", "build", "-trimpath", "-ldflags=-s -w", "-o", sidecarSaida.absolutePath, ".")
            .directory(sidecarFonte)
            .redirectErrorStream(true)
            .start()
        val saida = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) {
            throw GradleException("go build falhou. Go instalado? Saida:\n$saida")
        }
        logger.lifecycle("astra-voz.exe -> ${sidecarSaida.length() / 1024} KB")
    }
}

tasks.matching {
    it.name == "createDistributable" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name == "prepareAppResources"
}.configureEach { dependsOn(compilarSidecarVoz) }

tasks.register<Zip>("zipDistributable") {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("Astra-$astraVersion-win-x64.zip")
    destinationDirectory.set(layout.buildDirectory)
}

compose.desktop {
    application {
        mainClass = "app.astra.desktop.MainKt"
        jvmArgs += "-Dastra.version=$astraVersion"
        val gcProfile = providers.gradleProperty("astra.gc").orNull ?: "g1"
        if (gcProfile == "zgc") {
            jvmArgs += "-XX:+UseZGC"
            jvmArgs += "-XX:+ZGenerational"
        } else {
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
            modules("jdk.httpserver", "java.management", "jdk.management", "jdk.accessibility")
            appResourcesRootDir.set(project.file("appResources"))
            windows {
                iconFile.set(project.file("icons/astra.ico"))
            }
        }
    }
}
