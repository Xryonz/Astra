package app.astra.desktop.voice

import app.astra.desktop.WindowsAppId
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import dev.onvoid.webrtc.media.MediaDevices
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

// "O Windows está deixando o Astra usar isto?"
//
// No Windows não existe janelinha de permissão como no navegador: o app tenta
// usar o microfone e, se a privacidade estiver fechada, ele simplesmente recebe
// SILÊNCIO — sem erro, sem aviso, sem nada. Do lado de quem usa isso vira "meu
// mic não funciona no Astra", e não há como adivinhar de fora.
//
// Por isso a checagem aqui TENTA DE VERDADE em vez de perguntar ao sistema: abre
// o microfone e escuta um pedaço. É a única resposta que vale, porque é
// exatamente o que a call vai fazer depois.
//
// Consequência disso, e o motivo de o botão da interface se chamar "permitir"
// sem nunca abrir uma janelinha: aplicativo de área de trabalho não tem API pra
// PEDIR permissão. Quem decide é um interruptor global do Windows ("deixar
// aplicativos da área de trabalho acessarem o microfone"). Tudo o que dá pra
// fazer é levar a pessoa até o interruptor certo e ficar conferindo até virar.

enum class Acesso {
    /** Funciona agora. */
    OK,

    /** O Windows está negando na cara. */
    BLOQUEADO,

    /** Não existe aparelho pra usar. */
    SEM_APARELHO,

    /** Abriu, mas não chegou nada — o sintoma clássico de privacidade fechada. */
    MUDO,

    /**
     * Ninguém negou; o Windows só ainda não decidiu. Firewall antes da primeira
     * call, avisos antes do primeiro aviso. NÃO é defeito — e por isso não pinta
     * de vermelho nem de amarelo: mandar consertar o que não está quebrado é o
     * jeito mais rápido de ensinar alguém a ignorar a tela inteira.
     */
    PENDENTE,
}

// O que cada permissão É, em português de gente. Fica AQUI e não na tela porque
// as três telas que mostram isto (boas-vindas, configurações e o aviso da
// primeira abertura) precisam do mesmo texto — e texto duplicado é texto que
// diverge.
enum class Permissao(val titulo: String, val oQueE: String) {
    MICROFONE(
        "Microfone",
        "É por onde sua voz entra na call. Sem ele você ouve todo mundo e ninguém ouve você.",
    ),
    SOM(
        "Som",
        "A saída de áudio — é por onde você escuta as outras pessoas.",
    ),
    CAMERA(
        "Câmera",
        "Só é usada quando você liga o vídeo. A luz dela acende nessa hora, nunca antes.",
    ),
    TELA(
        "Transmitir a tela",
        "Mostrar o que está na sua tela pra quem está na call.",
    ),
    REDE(
        "Rede",
        "O firewall do Windows decide se o Astra pode falar com a internet. É por aí que passam as mensagens e a call.",
    ),
    AVISOS(
        "Avisos",
        "Deixa o Astra te chamar quando chega mensagem com o app fechado ou atrás de outra janela.",
    ),
}

data class Checagem(
    val permissao: Permissao,
    val acesso: Acesso,
    /** O estado AGORA — muda a cada conferida. Diferente do `oQueE`, que é fixo. */
    val explica: String,
    /** Página exata das Configurações do Windows, quando existe uma pra consertar. */
    val ajustes: String? = null,
)

object PermissoesWindows {

    // Ordem: primeiro o que a call precisa (na ordem em que ela usa), depois o
    // que o sistema controla por fora.
    fun todas(): List<Checagem> =
        listOf(microfone(), saida(), camera(), tela(), rede(), notificacoes())

    fun uma(p: Permissao): Checagem = when (p) {
        Permissao.MICROFONE -> microfone()
        Permissao.SOM -> saida()
        Permissao.CAMERA -> camera()
        Permissao.TELA -> tela()
        Permissao.REDE -> rede()
        Permissao.AVISOS -> notificacoes()
    }

    // Avisos do Windows.
    //
    // Aqui NAO ha permissao pra pedir: app de area de trabalho nao tem janelinha
    // de "permitir notificacoes". O que da pra conferir sao duas coisas de
    // verdade: se os avisos estao ligados no sistema, e se o Windows JA CONHECE o
    // Astra (a entrada so nasce depois do primeiro aviso, e so se o processo tiver
    // identidade — ver WindowsAppId).
    //
    // "Ainda nao conhece" NAO e defeito: e o estado normal antes do primeiro
    // aviso. Por isso o botao "permitir" desta linha DISPARA UM AVISO em vez de
    // abrir configuracao — e o unico jeito de fazer o Windows registrar o app.
    fun notificacoes(): Checagem {
        val ligado = runCatching {
            Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\PushNotifications",
                "ToastEnabled",
            )
        }.getOrDefault(1)
        if (ligado == 0) {
            return Checagem(
                Permissao.AVISOS, Acesso.BLOQUEADO,
                "As notificações estão desligadas no Windows — nenhum app consegue avisar você.",
                "ms-settings:notifications",
            )
        }
        val conhecido = runCatching {
            Advapi32Util.registryKeyExists(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\${WindowsAppId.AUMID}",
            )
        }.getOrDefault(false)
        return if (conhecido) {
            Checagem(Permissao.AVISOS, Acesso.OK, "O Windows conhece o Astra e deixa ele avisar você.")
        } else {
            Checagem(
                Permissao.AVISOS, Acesso.PENDENTE,
                "O Windows registra o Astra no primeiro aviso. Clique em permitir pra mandar um agora.",
                "ms-settings:notifications",
            )
        }
    }

    // Abre o mic e escuta ~400ms.
    //
    // O sinal de bloqueio é o silêncio EXATO: privacidade fechada entrega zeros
    // perfeitos, enquanto microfone de verdade sempre traz um chiadinho de fundo,
    // nem que seja de 1 bit. Não dá pra afirmar 100% (mic mudo no botão físico dá
    // o mesmo resultado), então o texto fala das duas causas em vez de cravar uma.
    fun microfone(): Checagem {
        val nomes = AudioDevices.inputs()
        if (nomes.isEmpty()) {
            return Checagem(
                Permissao.MICROFONE, Acesso.SEM_APARELHO,
                "Nenhum microfone encontrado. Conecte um e confira de novo.",
                "ms-settings:sound",
            )
        }

        val formato = AudioFormat(48_000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, formato)
        var abriu = false
        var soZeros = true

        runCatching {
            val linha = AudioSystem.getLine(info) as TargetDataLine
            linha.open(formato)
            abriu = true
            linha.start()
            val buffer = ByteArray(4096)
            val ate = System.currentTimeMillis() + 400
            while (System.currentTimeMillis() < ate) {
                val lidos = linha.read(buffer, 0, buffer.size)
                if (lidos <= 0) break
                for (i in 0 until lidos) if (buffer[i].toInt() != 0) { soZeros = false; break }
                if (!soZeros) break
            }
            linha.stop()
            linha.close()
        }

        return when {
            !abriu -> Checagem(
                Permissao.MICROFONE, Acesso.BLOQUEADO,
                "O Windows não deixou o Astra abrir o microfone. Ligue o acesso pra aplicativos da área de trabalho.",
                "ms-settings:privacy-microphone",
            )
            soZeros -> Checagem(
                Permissao.MICROFONE, Acesso.MUDO,
                "O microfone abriu, mas não chegou som nenhum. Costuma ser a privacidade do Windows fechada — ou o mic mudo no botão do aparelho.",
                "ms-settings:privacy-microphone",
            )
            else -> Checagem(Permissao.MICROFONE, Acesso.OK, "Ouvindo normalmente (${nomes.first()}).")
        }
    }

    // Só enumera: abrir a webcam acenderia a luz dela, e piscar a luz de alguém
    // numa tela de boas-vindas assusta com razão.
    fun camera(): Checagem {
        val cams = runCatching { MediaDevices.getVideoCaptureDevices() }.getOrDefault(emptyList())
        return if (cams.isEmpty()) {
            Checagem(
                Permissao.CAMERA, Acesso.SEM_APARELHO,
                "Nenhuma câmera encontrada. Se você tem uma, o acesso pode estar fechado no Windows.",
                "ms-settings:privacy-webcam",
            )
        } else {
            Checagem(Permissao.CAMERA, Acesso.OK, "${cams.size} encontrada(s).")
        }
    }

    fun saida(): Checagem {
        val saidas = AudioDevices.outputs()
        return if (saidas.isEmpty()) {
            Checagem(
                Permissao.SOM, Acesso.SEM_APARELHO,
                "Nenhuma saída de áudio encontrada — você não ouviria a call.",
                "ms-settings:sound",
            )
        } else {
            Checagem(Permissao.SOM, Acesso.OK, "${saidas.size} saída(s) disponível(is).")
        }
    }

    fun tela(): Checagem {
        val ff = FfmpegLocator.path
        return if (ff == null) {
            Checagem(
                Permissao.TELA, Acesso.SEM_APARELHO,
                "O componente de captura não veio no pacote. Reinstale o Astra.",
            )
        } else {
            Checagem(Permissao.TELA, Acesso.OK, "Pronto — o Windows não pede permissão pra isto.")
        }
    }

    // Firewall.
    //
    // Esta é a permissão que o Windows REALMENTE pergunta ("Permitir acesso?"),
    // uma vez só, na primeira vez que a call abre uma porta. Quem clica em
    // Cancelar naquele susto ganha uma regra de BLOQUEIO permanente e nunca mais
    // vê o aviso — e a partir daí a call falha calada, igual ao microfone.
    //
    // A leitura é do registro em vez de `netsh` de propósito: netsh demora
    // ~1s e pisca uma janela de console. Aqui são ~750 valores de texto no
    // formato "…|Action=Allow|Active=TRUE|Dir=In|App=C:\…\Astra.exe|…".
    private const val REGRAS_FIREWALL =
        "SYSTEM\\CurrentControlSet\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\FirewallRules"

    fun rede(): Checagem {
        // Sem jpackage estamos no Gradle: o executável é o java.exe do runtime, e
        // regra de firewall pra ele não diz nada sobre o Astra empacotado.
        val exe = System.getProperty("jpackage.app-path")?.lowercase()
            ?: return Checagem(
                Permissao.REDE, Acesso.PENDENTE,
                "Rodando pelo Gradle — não há executável do Astra pra procurar no firewall.",
            )

        val minhas = runCatching {
            Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, REGRAS_FIREWALL)
                .values.filterIsInstance<String>()
                .map { it.lowercase() }
                .filter { it.contains("|app=$exe|") || it.endsWith("|app=$exe") }
        }.getOrNull() ?: return Checagem(
            Permissao.REDE, Acesso.PENDENTE,
            "Não deu pra ler as regras do firewall. Se a call não conectar, confira o Astra na lista de aplicativos permitidos.",
            "windowsdefender://network/",
        )

        val ativas = minhas.filter { it.contains("|active=true|") }
        return when {
            ativas.any { it.contains("|action=block|") } -> Checagem(
                Permissao.REDE, Acesso.BLOQUEADO,
                "Existe uma regra bloqueando o Astra no firewall — provavelmente de um \"Cancelar\" no aviso do Windows. A call não conecta assim. Liberar remove o bloqueio.",
                "windowsdefender://network/",
            )
            ativas.any { it.contains("|action=allow|") } -> Checagem(
                Permissao.REDE, Acesso.OK, "Liberado no firewall do Windows.",
            )
            else -> Checagem(
                Permissao.REDE, Acesso.PENDENTE,
                "O Windows vai perguntar na sua primeira call. Liberar agora resolve antes, e evita o susto de cancelar o aviso sem querer.",
                "windowsdefender://network/",
            )
        }
    }

    // Cria a regra de liberação no firewall. Pede elevação — uma janela do UAC.
    //
    // Esta é a única linha do painel com uma ação DE VERDADE; as outras só
    // conseguem levar a pessoa até o interruptor certo do Windows, porque quem
    // decide ali é uma chave global do sistema. Aqui não: regra de firewall
    // qualquer administrador escreve.
    //
    // O roteiro APAGA todas as regras deste executável antes de criar as duas
    // novas, e isso é o ponto inteiro. Regra de bloqueio vence regra de liberação,
    // então só acrescentar um "permitir" não conserta o caso mais comum de call
    // que não conecta: quem clicou Cancelar naquele aviso do Windows ganhou um
    // bloqueio permanente. Achar essa regra na mão, no meio de ~750 outras, não é
    // coisa que se peça a ninguém.
    //
    // Vai por arquivo .ps1, e não por linha de comando, por causa das aspas: o
    // caminho do executável entra dentro de um argumento do netsh que já é
    // `program="..."`, e escapar isso através de cmd -> powershell -> netsh dá
    // exatamente o tipo de erro que só aparece na máquina de outra pessoa. O BOM
    // é obrigatório: sem ele o Windows PowerShell lê o arquivo como ANSI e um
    // caminho acentuado (C:\Users\João\...) chega corrompido no netsh.
    //
    // false = a pessoa recusou o UAC, ou não há executável (rodando pelo Gradle).
    fun liberarNoFirewall(): Boolean {
        val exe = System.getProperty("jpackage.app-path") ?: return false
        val roteiro = java.io.File(System.getProperty("java.io.tmpdir"), "astra-liberar-firewall.ps1")
        // Aspa simples dobrada: um nome de usuário com apóstrofo quebraria a
        // string do PowerShell no meio.
        val caminho = exe.replace("'", "''")
        val cifrao = '$'
        roteiro.writeText(
            "\uFEFF" + """
            ${cifrao}exe = '$caminho'
            netsh advfirewall firewall delete rule name=all program="${cifrao}exe" | Out-Null
            netsh advfirewall firewall add rule name="Astra" dir=in  action=allow program="${cifrao}exe" enable=yes profile=any | Out-Null
            netsh advfirewall firewall add rule name="Astra" dir=out action=allow program="${cifrao}exe" enable=yes profile=any | Out-Null
            """.trimIndent(),
            Charsets.UTF_8,
        )
        // -ErrorAction Stop + catch: sem isso, recusar o UAC vira um erro NÃO
        // terminante e o powershell de fora sai com código 0 — o app diria
        // "liberado" para quem acabou de clicar em Não.
        val comando = "try { Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','\"${roteiro.absolutePath}\"' " +
            "-ErrorAction Stop } catch { exit 1 }"
        val ok = runCatching {
            val p = ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", comando)
                .redirectErrorStream(true)
                .start()
            // Teto: a janela do UAC pode ficar aberta pra sempre se ninguém
            // responder, e uma corrotina presa nisso seguraria o painel.
            if (p.waitFor(2, java.util.concurrent.TimeUnit.MINUTES)) p.exitValue() == 0
            else { p.destroy(); false }
        }.getOrDefault(false)
        roteiro.delete()
        return ok
    }

    // `start` do cmd é o que entende ms-settings: e windowsdefender: — Desktop.browse()
    // só lida com http/https e recusa esses esquemas.
    fun abrirAjustes(uri: String) {
        runCatching { ProcessBuilder("cmd", "/c", "start", "", uri).start() }
    }
}
