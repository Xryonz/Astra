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

// Estabilidade + relatorios do compilador Compose.
composeCompiler {
    // SEMPRE: trata os DTOs imutaveis do :shared (e data classes de item de lista)
    // como estaveis -> Compose pula por equals estrutural, menos recomposicao em
    // listas. Ver compose_stability.conf. Zero mudanca de runtime.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))

    // Profiler de recomposicao (build-time), gated pra nao pesar o build normal:
    //   ./gradlew :desktopApp:compileKotlin -PcomposeReports --rerun-tasks
    // Le em build/compose_reports/*-composables.txt e *-classes.txt.
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
}

// jpackage/jlink quebram com caminho non-ASCII no Windows (o repo mora em
// ".../Codigos e Loucuras/..."), entao o empacote sai do repo e vai pra um
// caminho limpo. Tudo do Astra mora em C:/Astra:
//
//   C:/Astra/build/      <- saida do empacote (era a pasta astra-dist solta)
//   C:/Astra/versions/   <- as versoes instaladas
//   C:/Astra/multi/      <- copia pra abrir como OUTRA pessoa (testar call)
//
// Pra empacotar: ./gradlew :desktopApp:zipDistributable -Pastra.dist
// (o valor e opcional; da pra mandar outro caminho com -Pastra.dist=D:/foo)
// Sem a flag, nada muda: build normal em build/, dentro do repo.
providers.gradleProperty("astra.dist").orNull?.let {
    layout.buildDirectory.set(file(it.ifBlank { "C:/Astra/build" }))
}
// Nome antigo, mantido pra nao quebrar comando ja anotado em outro lugar.
providers.gradleProperty("astra.distDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Versao unica do desktop: alimenta o packageVersion do jpackage E entra no app
// via -Dastra.version -> o auto-update compara com a ultima release do GitHub.
// Bumpar aqui (1 lugar) a cada release.
//
// A LINHA 0.1.x MORREU NA 0.1.114. Passamos de cem versoes de patch dentro de um
// unico minor, o que fazia o numero perder a funcao: "0.1.113 -> 0.1.114" nao dizia
// nada sobre o tamanho da mudanca. Daqui pra frente o minor sobe.
//
// A troca e segura pro auto-update: o isNewer do UpdateService compara campo a campo
// como inteiro, entao [0,2,0] > [0,1,114] pelo segundo campo. Comparacao de texto
// diria a mesma coisa por acaso, mas e o campo a campo que vale.
val astraVersion = "0.2.74"

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
    // GStreamer: SO os bindings (JNA), ~1MB. O runtime nativo (62MB) e baixado sob
    // demanda pelo GStreamerPack e vive em %LOCALAPPDATA%, fora do app — ver la o
    // porque. Sem o pacote em disco, estes bindings simplesmente nao sao usados.
    implementation(libs.gst.java)
    // Signaling do LiveKit: runtime do protobuf. As classes Java ficam
    // COMMITADAS em src/main/java/livekit (geradas 1x na mao) porque o protoc,
    // como o jpackage, nao engole o path com acento do repo. Pra regenerar
    // (quando os .proto em src/main/proto mudarem): copiar os protos pra um
    // dir ASCII (ex: C:/Astra/build/proto-tmp, com google/protobuf/timestamp
    // e descriptor extraidos do jar do protobuf-java) e rodar:
    //   protoc --proto_path=C:/Astra/build/proto-tmp --java_out=<saida> \
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

// Zipa o app-image (pasta Astra/) pro asset do GitHub Release que o auto-update
// baixa. Rodar junto do empacote (mesmo path ASCII do jpackage):
//   ./gradlew :desktopApp:zipDistributable -Pastra.distDir=C:/Astra/build
// Saida: <buildDir>/Astra-<versao>-win-x64.zip
tasks.register<Zip>("zipDistributable") {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("Astra-$astraVersion-win-x64.zip")
    destinationDirectory.set(layout.buildDirectory)
}

// Banco de testes do transporte novo (webrtcbin). TEMPORARIO -- sai junto com o
// EnsaioGst.kt quando as pecas estiverem provadas. Existe pra rodar negociacao WebRTC
// de verdade fora do app: descobrir que o msid nao casa, ou que o get-stats nao
// responde, DENTRO de uma call seria descobrir com a voz de alguem no meio.
tasks.register<JavaExec>("ensaioGst") {
    group = "verification"
    mainClass.set("app.astra.desktop.voice.EnsaioGstKt")
    classpath = sourceSets["main"].runtimeClasspath
    // Pra investigar uma categoria a fundo sem republicar nada:
    //   ./gradlew :desktopApp:ensaioGst -Pgstdebug=2,d3d11*:5
    providers.gradleProperty("gstdebug").orNull?.let { systemProperty("astra.gstdebug", it) }
    // Um contador por elo do ramo de video, pra achar QUEM segura o cano:
    //   ./gradlew :desktopApp:ensaioGst -Pcontarelos
    providers.gradleProperty("contarelos").orNull?.let { systemProperty("astra.contarelos", "1") }
    // Forca um encoder especifico, pra comparar dois na mesma medicao:
    //   ./gradlew :desktopApp:ensaioGst -Pencoder=nvd3d11h264enc
    providers.gradleProperty("encoder").orNull?.let { systemProperty("astra.encoder", it) }
}

compose.desktop {
    application {
        mainClass = "app.astra.desktop.MainKt"
        // Versao embutida pro auto-update ler em runtime (System.getProperty).
        jvmArgs += "-Dastra.version=$astraVersion"
        // O skiko (FrameWatcher) chama System.gc() a cada ~40s pra liberar memoria
        // nativa do Skia. Com G1 isso vira um full GC stop-the-world -> pausa de ms
        // que ENGASGA a aurora animando 60fps ("corte do nada", achado no JFR). Este
        // flag transforma o System.gc() explicito num ciclo CONCORRENTE: a memoria
        // nativa ainda e liberada, mas sem travar as threads de render. Ship pra todos.
        // --- Fase 1 de desempenho ---
        // GC: o coletor e a fonte classica de engasgo numa UI 60fps (uma pausa de 30ms
        // = 2 frames perdidos), mas TAMBEM pesa na memoria. Medimos os dois no app real
        // (tools/medir-desempenho.ps1), mesma maquina, mesmas 3 fases:
        //
        //                    ZGC        G1
        //   parado          582MB      433MB
        //   em call         609MB      155MB
        //   transmitindo   2768MB      500MB   <- 5.5x menos
        //
        // O ZGC mapeia a mesma memoria fisica em varios enderecos e o Windows conta
        // CADA mapeamento no working set — o "vazamento de 2.7GB" era contabilidade
        // inflada, não memoria de verdade. Como o objetivo aqui e custo minimo de RAM,
        // o padrao e G1. Pra voltar ao ZGC (pausas < 1ms, se um dia o engasgo importar
        // mais que a memoria): ./gradlew ... -Pastra.gc=zgc
        val gcProfile = providers.gradleProperty("astra.gc").orNull ?: "g1"
        if (gcProfile == "zgc") {
            jvmArgs += "-XX:+UseZGC"
            jvmArgs += "-XX:+ZGenerational"
            // No ZGC todo ciclo ja e concorrente — inclusive o System.gc() do skiko.
        } else {
            // G1 com alvo de pausa curto (default e 200ms — uma eternidade a 60fps).
            jvmArgs += "-XX:MaxGCPauseMillis=8"
            // System.gc() do skiko vira ciclo concorrente em vez de full stop-the-world.
            jvmArgs += "-XX:+ExplicitGCInvokesConcurrent"
        }

        // AppCDS automatico (JDK 19+): a JVM guarda as classes ja "digeridas" num
        // arquivo e reusa na proxima abertura -> abre mais rapido e o metaspace fica
        // menor (memoria compartilhada em vez de recriada). Cria sozinho no 1o run;
        // se o caminho não for gravavel, a JVM so avisa e segue (não quebra).
        // $APPDIR e substituido pelo jpackage pela pasta app/ da instalacao.
        // So no build EMPACOTADO: `$APPDIR` e substituido pelo jpackage. Rodando pelo
        // Gradle (:run) o token não resolve e a JVM cospe um erro feio de cds (inofensivo,
        // sai com 0 — verificado), entao isto so entra quando se esta EMPACOTANDO.
        //
        // O GATE ESTAVA ERRADO E NADA DISTO CHEGAVA NO APP PUBLICADO.
        //
        // Era `astra.distDir`, que significa "jogue a saida do build noutro lugar" —
        // uma gambiarra que existe so porque o repo do dono mora num caminho com
        // acento e o jpackage não engole. O workflow de release NAO passa essa flag
        // (de proposito: no runner o caminho e limpo). Ou seja, o unico build que
        // chega em alguem era exatamente o que ficava SEM AppCDS (abertura mais
        // lenta pra todo mundo) e SEM ErrorFile (o laudo de crash nativo caindo como
        // hs_err_pid<n>.log na raiz, enquanto o diagnostico mandava procurar
        // falha-jvm-*.log — um arquivo que nunca existiu).
        //
        // Agora o gate pergunta a coisa certa: "a tarefa pedida e de empacotamento?".
        // Vale no CI e na maquina do dono, com ou sem a gambiarra do caminho.
        val empacotando = gradle.startParameter.taskNames.any { alvo ->
            listOf("distributable", "package", "dmg", "msi", "deb").any { it in alvo.lowercase() }
        } || providers.gradleProperty("astra.distDir").isPresent
        if (empacotando) {
            jvmArgs += "-XX:+AutoCreateSharedArchive"
            jvmArgs += "-XX:SharedArchiveFile=\$APPDIR/astra-cds.jsa"
            // Crash NATIVO (webrtc/skia derrubando a JVM inteira) não passa pelo
            // CrashLog — a JVM morre antes de rodar codigo Java. Neste caso ela
            // escreve o hs_err aqui, ao lado do app, em vez de num diretorio
            // aleatorio onde ninguem acha. Junto com falhas.txt, cobre os dois
            // tipos de "fecha do nada": excecao Java e morte nativa.
            jvmArgs += "-XX:ErrorFile=\$APPDIR/falha-jvm-%p.log"
        }

        // SEGUNDA JANELA pra testar com DUAS contas ao mesmo tempo:
        //   ./gradlew :desktopApp:run -Pastra.multi
        // Pula o bloqueio de instancia unica E usa uma pasta de sessao propria
        // (%APPDATA%\Astra-teste1), entao da pra logar com outra conta e ver ao vivo
        // o que uma faz aparecer na outra. A maioria dos bugs de tempo real so
        // aparece com duas pontas — com uma conta so, quem cria o canal sempre ve o
        // canal. NAO vai no pacote: e gateado pela flag, como o -Pjfr.
        if (providers.gradleProperty("astra.multi").isPresent) {
            jvmArgs += "-Dastra.multi=${providers.gradleProperty("astra.multi").get().ifBlank { "1" }}"
        }

        // Diagnostico de engasgo (NAO vai no pacote normal):
        //   ./gradlew :desktopApp:run -Pastra.diag
        // Loga fps e AVISA cada frame que passou de 17ms (= perdeu o vsync de 60fps).
        // E assim que se acha travamento de verdade em vez de adivinhar.
        if (providers.gradleProperty("astra.diag").isPresent) {
            jvmArgs += "-Dskiko.fps.enabled=true"
            jvmArgs += "-Dskiko.fps.longFrames.show=true"
            jvmArgs += "-Dskiko.fps.longFrames.millis=17"
        }
        // Teto de HEAP. Sem -Xmx o HotSpot deixa o heap crescer ate 1/4 da RAM FISICA
        // (num PC de 16GB isso e ~4GB) antes de um GC maior — como não ha pressao, o GC
        // fica preguicoso e o RSS so sobe ("em call, de 2 em 2MB a mais, sem parar"). O
        // churn de getStats do audio (5x/s por participante) + protobuf + UI alimenta
        // isso. Capar em 768MB forca o heap a ficar enxuto (uso real fica ~150-300MB),
        // entao o RSS para de escalar. NAO afeta a transmissão: bitmaps de video sao
        // memoria NATIVA (fora do heap), presos pelo RasterRecycler, não pelo -Xmx.
        //
        // 512m (0.1.35) foi longe demais: teto baixo não "economiza" RAM quando o app
        // realmente precisa dela — vira OutOfMemoryError, que mata o processo na hora
        // e sem aviso. Como MaxHeapFreeRatio devolve as paginas ao Windows depois do
        // pico, o teto mais alto NAO custa memoria parado; so evita a morte no pico
        // (call cheia + transmissão). 1GB e o teto pedido pelo dono.
        //
        // ATENCAO ao ler o Gerenciador de Tarefas: isto limita o HEAP (objetos Java),
        // que não e o total do processo. Os quadros de video vivem em memoria NATIVA,
        // fora do heap — por isso "3GB transmitindo" NAO e resolvido por este numero.
        // O que segura aquilo e o lado nativo (ver ScreenCaptureFfmpeg).
        jvmArgs += "-Xmx1g"
        // Devolver RAM ao SISTEMA. Por padrao a JVM segura o que ja cresceu: mesmo
        // depois de coletar, o heap continua reservado e o Gerenciador de Tarefas
        // segue mostrando o pico. Com estas tres a JVM ENCOLHE o heap e devolve as
        // paginas ao Windows, entao a memoria CAI depois de uma call/transmissao em
        // vez de ficar no topo. O periodico so roda quando o app esta ocioso.
        jvmArgs += "-XX:MinHeapFreeRatio=10"
        jvmArgs += "-XX:MaxHeapFreeRatio=25"
        jvmArgs += "-XX:G1PeriodicGCInterval=20000"
        // Teto do metaspace (classes). Sem limite ele so cresce; o teto evita
        // crescimento silencioso ao longo de horas. 192m era apertado demais pro que
        // este app carrega (Compose + Koin + Retrofit + protobuf + webrtc, mais as
        // classes que o Compose GERA em runtime): estourar o metaspace tambem e um
        // OutOfMemoryError, ou seja, mais uma forma de "fecha do nada".
        jvmArgs += "-XX:MaxMetaspaceSize=256m"
        // Profiler RUNTIME (JFR), gated pra nunca vazar pro pacote: rodar
        //   ./gradlew :desktopApp:run -Pjfr
        // Usar o app ~2min (aurora, rolar chat, entrar em call, transmitir) e fechar;
        // gera astra-profile.jfr (dumponexit) na pasta do modulo. Analiso com o
        // `jfr` CLI (ExecutionSample = CPU; ObjectAllocation = alocacao).
        if (providers.gradleProperty("jfr").isPresent) {
            jvmArgs += "-XX:StartFlightRecording=duration=120s,filename=astra-profile.jfr,settings=profile,dumponexit=true"
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Astra"
            packageVersion = astraVersion
            // Modulos do JDK que o jlink NAO inclui por padrao mas o app usa em
            // runtime. jdk.httpserver = com.sun.net.httpserver (loopback do login
            // Google, GoogleAuthFlow). Sem ele o .exe empacotado joga
            // NoClassDefFoundError -> "Nao consegui abrir a porta local". No dev
            // (JDK completo) o modulo existe, por isso so quebrava no pacote.
            // java.management: sem ele o ManagementFactory nem existe no runtime
            // enxuto do jlink — era o "GC : ?" do diagnostico (a leitura do coletor
            // falhava calada). Tambem e o que habilita monitoramento/JFR no pacote.
            // jdk.accessibility: no Windows, o leitor de tela so enxerga o app
            // atraves da Java Access Bridge, e a ponte mora NESTE modulo. Sem ele
            // no pacote, todo o trabalho de rotular botao e invisivel — nem quem
            // ligasse o Access Bridge no proprio Windows (jabswitch /enable)
            // conseguiria usar o Astra por leitor de tela.
            // jdk.management: o com.sun.management.OperatingSystemMXBean (quanto
            // processador ESTE processo gastou) mora nele, e nao no java.management.
            // Sem o modulo, a medicao de custo da transmissao compila e explode so no
            // app empacotado — a mesma pegadinha que ja custou o jdk.httpserver.
            modules("jdk.httpserver", "java.management", "jdk.management", "jdk.accessibility")
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
