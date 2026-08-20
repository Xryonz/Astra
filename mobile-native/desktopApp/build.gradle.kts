import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :desktopApp â€” cliente desktop do Astra (Compose Multiplatform / JVM).
// D0: so abre uma janela obsidiana. O codigo compartilhado (dominio/dados/UI)
// entra num :shared em D1+; por ora este modulo e standalone e NAO toca no :app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)   // compiler Compose (ship junto do Kotlin)
    alias(libs.plugins.jetbrains.compose) // Compose Multiplatform (compose.desktop)
    // OBRIGATORIO, e a falta dele nao aparece no build: `@Serializable` sozinho e so
    // uma anotacao. Quem escreve o serializador e ESTE plugin, em tempo de
    // compilacao. Sem ele o codigo compila igual e quebra na primeira linha de JSON,
    // ja rodando -- foi o que deixou a call presa em "conectando" (o `pronto` do
    // processo de voz chegava e nao decodificava) e as notificacoes sem remetente
    // (o payload virava um objeto vazio). Dois sintomas sem nada em comum, uma causa.
    alias(libs.plugins.kotlin.serialization)
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
val astraVersion = "0.2.96"

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
    // Icones Lucide (mesma lib/versao do :app Android) â€” variante -jvm. Os ~1.7k
    // ImageVectors ficam sob com.composables.icons.lucide.* (igual ao mobile).
    implementation(libs.lucide.icons.jvm)
    // Realtime: socket.io-client e Java puro (mesma lib do Android, mesmo backend).
    implementation(libs.socketio.client)
    // RikkaUI e Compose Multiplatform (foundation-only) -> componentes do mobile
    // (Input, Dialog, ...) rodam identicos no desktop.
    implementation(libs.rikkaui.foundation)
    implementation(libs.rikkaui.components)
    // DPAPI (CryptProtectData) pro SessionStore â€” tokens cifrados em repouso.
    implementation(libs.jna.platform)
    // Vidro/blur real (backdrop) â€” haze e CMP, mesma lib do Android.
    implementation(libs.haze)
    // A VOZ NAO MORA MAIS NA JVM. Ela vive no sidecar em Go (sidecar-voz/), que fala
    // WebRTC pelo pion e captura o audio pelo WASAPI direto. Por isso sairam daqui:
    //
    //   webrtc-java  8,0 MB de nativo do Windows por classifier
    //   gst-java     bindings do GStreamer
    //
    // Os dois so eram usados pelo motor antigo, que virou ilha fechada quando a voz
    // migrou e foi removido inteiro. A captura de tela segue o mesmo caminho: DXGI
    // Desktop Duplication dentro do Go, sem passar por aqui.
    //
    // O SIGNALING DO LIVEKIT FOI JUNTO, e demorou a sair. Ficaram para tras 5,4 MB de
    // Java gerado (src/main/java/livekit + logger) mais os .proto que os geraram e o
    // runtime protobuf-java que so eles usavam. Nao havia UM import: as unicas
    // mencoes a LiveKit no codigo Kotlin eram comentarios explicando por que ele nao
    // existe mais. Codigo morto grande e caro em silencio -- entra no jar, no arquivo
    // do CDS e no tempo de compilar, e ninguem o ve porque ninguem o chama.
    // Som da soundboard: MP3 e OGG entram direto pelo JavaSound. Ver ConversorDeSom
    // -- trocaram um binario de 137,8 MB por ~300 KB de jar.
    implementation(libs.mp3spi)
    implementation(libs.vorbisspi)
}

// O FFMPEG SAIU DO PACOTE, e a conta explica sozinha por quÃª.
//
// Ele pesava 137,8 MB num instalador de 299 MB â€” quase metade do app, baixada por
// todo mundo a cada atualizaÃ§Ã£o automÃ¡tica. Entrou para capturar tela; quando a
// transmissÃ£o saiu do ar, sobrou com uma Ãºnica funÃ§Ã£o viva: converter o arquivo
// que um administrador escolhe ao subir um som de soundboard.
//
// Hoje isso Ã© feito por dois provedores do JavaSound, ~300 KB somados, dentro do
// prÃ³prio processo (ver ConversorDeSom).
//
// Quando o vÃ­deo voltar, ele volta em Go â€” nÃ£o por aqui. Se um dia for preciso
// ressuscitar esta tarefa, ela estÃ¡ no histÃ³rico do git.
// Compila o sidecar de voz (Go) pro appResources. O binario e gerado, nao
// versionado â€” quem clona o repo compila junto do empacote.
//
// O SIDECAR NAO PODE FALTAR NO PACOTE: sem ele nao ha voz nenhuma. Por isso esta
// task NAO tem `onlyIf { !existe }` â€” ela recompila sempre que o
// fonte muda, senao um pacote sairia com a voz da semana passada dentro.
//
// Se o Go nao estiver instalado, falha com mensagem clara em vez de gerar um zip
// mutilado que so daria erro na maquina do usuario. Os runners do GitHub para
// Windows ja trazem Go.
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

// `prepareAppResources` ENTRA NA LISTA, e nÃ£o Ã© detalhe de arrumaÃ§Ã£o.
//
// As duas tarefas acima escrevem dentro de `appResources/windows/`, que Ã©
// justamente a pasta que o `prepareAppResources` LÃŠ para montar o pacote. Amarrar
// sÃ³ o `createDistributable` deixava a ordem entre elas ao acaso: o Gradle podia
// copiar os recursos antes de o Go ter compilado, e o zip sairia sem o componente
// de voz â€” um app que instala, abre, e nÃ£o tem call, sem nada no build indicando o
// porquÃª.
//
// O Gradle 9 recusa isso na cara em vez de deixar passar ("uses this output without
// declaring an explicit dependency"), e foi assim que apareceu.
tasks.matching {
    it.name == "createDistributable" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name == "prepareAppResources"
}.configureEach { dependsOn(compilarSidecarVoz) }

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

// A tarefa `ensaioGst` saiu junto com o EnsaioGst.kt, como o comentario dela mesma
// previa. Ela era o banco de testes do transporte por webrtcbin, e o equivalente hoje
// e `go test` no sidecar-voz: as sondas de la (eco, aparelhos, tela) provam as pecas
// fora de uma call pelo mesmo motivo -- descobrir que uma nao encaixa DENTRO de uma
// conversa seria descobrir com a voz de alguem no meio.

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
        // CADA mapeamento no working set â€” o "vazamento de 2.7GB" era contabilidade
        // inflada, nÃ£o memoria de verdade. Como o objetivo aqui e custo minimo de RAM,
        // o padrao e G1. Pra voltar ao ZGC (pausas < 1ms, se um dia o engasgo importar
        // mais que a memoria): ./gradlew ... -Pastra.gc=zgc
        val gcProfile = providers.gradleProperty("astra.gc").orNull ?: "g1"
        if (gcProfile == "zgc") {
            jvmArgs += "-XX:+UseZGC"
            jvmArgs += "-XX:+ZGenerational"
            // No ZGC todo ciclo ja e concorrente â€” inclusive o System.gc() do skiko.
        } else {
            // G1 com alvo de pausa curto (default e 200ms â€” uma eternidade a 60fps).
            jvmArgs += "-XX:MaxGCPauseMillis=8"
            // System.gc() do skiko vira ciclo concorrente em vez de full stop-the-world.
            jvmArgs += "-XX:+ExplicitGCInvokesConcurrent"
        }

        // AppCDS automatico (JDK 19+): a JVM guarda as classes ja "digeridas" num
        // arquivo e reusa na proxima abertura -> abre mais rapido e o metaspace fica
        // menor (memoria compartilhada em vez de recriada). Cria sozinho no 1o run;
        // se o caminho nÃ£o for gravavel, a JVM so avisa e segue (nÃ£o quebra).
        // $APPDIR e substituido pelo jpackage pela pasta app/ da instalacao.
        // So no build EMPACOTADO: `$APPDIR` e substituido pelo jpackage. Rodando pelo
        // Gradle (:run) o token nÃ£o resolve e a JVM cospe um erro feio de cds (inofensivo,
        // sai com 0 â€” verificado), entao isto so entra quando se esta EMPACOTANDO.
        //
        // O GATE ESTAVA ERRADO E NADA DISTO CHEGAVA NO APP PUBLICADO.
        //
        // Era `astra.distDir`, que significa "jogue a saida do build noutro lugar" â€”
        // uma gambiarra que existe so porque o repo do dono mora num caminho com
        // acento e o jpackage nÃ£o engole. O workflow de release NAO passa essa flag
        // (de proposito: no runner o caminho e limpo). Ou seja, o unico build que
        // chega em alguem era exatamente o que ficava SEM AppCDS (abertura mais
        // lenta pra todo mundo) e SEM ErrorFile (o laudo de crash nativo caindo como
        // hs_err_pid<n>.log na raiz, enquanto o diagnostico mandava procurar
        // falha-jvm-*.log â€” um arquivo que nunca existiu).
        //
        // Agora o gate pergunta a coisa certa: "a tarefa pedida e de empacotamento?".
        // Vale no CI e na maquina do dono, com ou sem a gambiarra do caminho.
        val empacotando = gradle.startParameter.taskNames.any { alvo ->
            listOf("distributable", "package", "dmg", "msi", "deb").any { it in alvo.lowercase() }
        } || providers.gradleProperty("astra.distDir").isPresent
        if (empacotando) {
            jvmArgs += "-XX:+AutoCreateSharedArchive"
            jvmArgs += "-XX:SharedArchiveFile=\$APPDIR/astra-cds.jsa"
            // Crash NATIVO (webrtc/skia derrubando a JVM inteira) nÃ£o passa pelo
            // CrashLog â€” a JVM morre antes de rodar codigo Java. Neste caso ela
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
        // aparece com duas pontas â€” com uma conta so, quem cria o canal sempre ve o
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
        // (num PC de 16GB isso e ~4GB) antes de um GC maior â€” como nÃ£o ha pressao, o GC
        // fica preguicoso e o RSS so sobe ("em call, de 2 em 2MB a mais, sem parar"). O
        // churn de getStats do audio (5x/s por participante) + protobuf + UI alimenta
        // isso. Capar em 768MB forca o heap a ficar enxuto (uso real fica ~150-300MB),
        // entao o RSS para de escalar. NAO afeta a transmissÃ£o: bitmaps de video sao
        // memoria NATIVA (fora do heap), presos pelo RasterRecycler, nÃ£o pelo -Xmx.
        //
        // 512m (0.1.35) foi longe demais: teto baixo nÃ£o "economiza" RAM quando o app
        // realmente precisa dela â€” vira OutOfMemoryError, que mata o processo na hora
        // e sem aviso. Como MaxHeapFreeRatio devolve as paginas ao Windows depois do
        // pico, o teto mais alto NAO custa memoria parado; so evita a morte no pico
        // (call cheia + transmissÃ£o). 1GB e o teto pedido pelo dono.
        //
        // ATENCAO ao ler o Gerenciador de Tarefas: isto limita o HEAP (objetos Java),
        // que nÃ£o e o total do processo. Os quadros de video vivem em memoria NATIVA,
        // fora do heap â€” por isso "3GB transmitindo" NAO e resolvido por este numero.
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
        // O QUE DE FATO SEGURA O HEAP, e nao e o -Xmx. Medido com um churn de 12s
        // (aloca muito, retem pouco â€” o perfil de um app de UI), tres repeticoes:
        //
        //   GCTimeRatio=12 (padrao)   639 MB commitados   referencia
        //   GCTimeRatio=4             397 MB              -2,5% de vazao
        //   GCTimeRatio=2             334 MB              -4,3% de vazao
        //   Xmx768m (com o padrao)    592 MB              quase nada
        //   Xmx512m (com o padrao)    512 MB
        //
        // Ou seja: BAIXAR O TETO QUASE NAO AJUDA. Com GCTimeRatio=12 o G1 aceita
        // gastar ~8% do tempo coletando, e com essa folga ele prefere crescer o heap
        // a trabalhar â€” commita 639 MB para usar 200. Apertar a razao inverte a
        // escolha: ele coleta mais e cresce menos. 242 MB a menos por 2,5% de vazao,
        // e o -Xmx continua sendo o que sempre foi, a valvula contra OutOfMemory.
        //
        // TESTADO E DESCARTADO no caminho: `-XX:SoftMaxHeapSize`. O G1 do JDK 21
        // ACEITA o flag sem reclamar e simplesmente o ignora â€” 644 MB com ele, 644 MB
        // sem. Flag que nao da erro e nao faz nada e a pior especie.
        //
        // Escolhido 4 e nao 2: `MaxGCPauseMillis=8` continua limitando cada pausa, mas
        // apertar a razao aumenta a FREQUENCIA delas, e este app anima a 60fps. 4
        // pega quase toda a economia; 2 cobra o dobro de vazao por 63 MB a mais.
        jvmArgs += "-XX:GCTimeRatio=4"
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
        // ONDE A RAM ESTA, por categoria (NAO vai no pacote):
        //   .\gradlew :desktopApp:run -Pastra.nmt
        //   jcmd <pid> VM.native_memory summary
        //
        // Existe porque "o app usa 900MB" nao e acionavel: heap, metaspace, cache de
        // codigo, pilhas de thread e buffers diretos sao cinco donos diferentes com
        // cinco remedios diferentes, e o Gerenciador de Tarefas soma os cinco num
        // numero so. O NMT separa â€” e separar e o que transforma um numero ruim numa
        // linha de codigo pra mudar.
        //
        // Custa ~5% de desempenho e um pouco da propria RAM que mede, entao fica atras
        // da flag em vez de ligado sempre.
        if (providers.gradleProperty("astra.nmt").isPresent) {
            jvmArgs += "-XX:NativeMemoryTracking=summary"
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
            // enxuto do jlink â€” era o "GC : ?" do diagnostico (a leitura do coletor
            // falhava calada). Tambem e o que habilita monitoramento/JFR no pacote.
            // jdk.accessibility: no Windows, o leitor de tela so enxerga o app
            // atraves da Java Access Bridge, e a ponte mora NESTE modulo. Sem ele
            // no pacote, todo o trabalho de rotular botao e invisivel â€” nem quem
            // ligasse o Access Bridge no proprio Windows (jabswitch /enable)
            // conseguiria usar o Astra por leitor de tela.
            // jdk.management: o com.sun.management.OperatingSystemMXBean (quanto
            // processador ESTE processo gastou) mora nele, e nao no java.management.
            // Sem o modulo, a medicao de custo da transmissao compila e explode so no
            // app empacotado â€” a mesma pegadinha que ja custou o jdk.httpserver.
            modules("jdk.httpserver", "java.management", "jdk.management", "jdk.accessibility")
            // Recursos por-SO empacotados no app-image. Hoje sao dois:
            // `astra-voz.exe` (o processo de voz, compilado do Go pelo
            // compilarSidecarVoz) e `opus-0.dll` (o codec que ele carrega). Em
            // runtime saem em System.getProperty("compose.application.resources.dir").
            appResourcesRootDir.set(project.file("appResources"))
            windows {
                // Logo do Astra (mesmo favicon.ico do site) no Astra.exe.
                iconFile.set(project.file("icons/astra.ico"))
            }
        }
    }
}
