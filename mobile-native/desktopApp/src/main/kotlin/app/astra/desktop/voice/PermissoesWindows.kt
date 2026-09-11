package app.astra.desktop.voice

import app.astra.desktop.Instalacao
import app.astra.desktop.WindowsAppId
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

enum class Acesso {
    OK,

    BLOQUEADO,

    SEM_APARELHO,

    MUDO,

    PENDENTE,
}

enum class Permissao(val titulo: String, val oQueE: String) {
    MICROFONE(
        "Microfone",
        "É por onde sua voz entra na call. Sem ele você ouve todo mundo e ninguém ouve você.",
    ),
    SOM(
        "Som",
        "A saída de áudio — é por onde você escuta as outras pessoas.",
    ),
    TELA(
        "Transmitir a tela",
        "Mostrar o que está na sua tela para quem está na call.",
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
    val explica: String,
    val ajustes: String? = null,
    val podeDesfazer: Boolean = false,
)

object PermissoesWindows {

    fun todas(): List<Checagem> =
        listOf(microfone(), saida(), tela(), rede(), notificacoes())

    fun uma(p: Permissao): Checagem = when (p) {
        Permissao.MICROFONE -> microfone()
        Permissao.SOM -> saida()
        Permissao.TELA -> tela()
        Permissao.REDE -> rede()
        Permissao.AVISOS -> notificacoes()
    }

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
        val doAstra = runCatching {
            Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\${WindowsAppId.AUMID}",
                "Enabled",
            )
        }.getOrDefault(1)
        return if (doAstra == 0) {
            Checagem(
                Permissao.AVISOS, Acesso.BLOQUEADO,
                "Os avisos do Astra estão desligados nas notificações do Windows.",
                "ms-settings:notifications",
            )
        } else {
            Checagem(
                Permissao.AVISOS, Acesso.OK,
                "Nada bloqueia os avisos do Astra. O Windows registra o app sozinho no primeiro que ele mandar.",
            )
        }
    }

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
                "O Windows não deixou o Astra abrir o microfone. Ligue o acesso para aplicativos da área de trabalho.",
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

    fun tela(): Checagem = Checagem(
        Permissao.TELA, Acesso.SEM_APARELHO,
        "Transmitir tela está fora do ar enquanto a voz migra para o componente novo. " +
            "O Windows não pede permissão para isto — não há nada para você liberar.",
    )

    private const val REGRAS_FIREWALL =
        "SYSTEM\\CurrentControlSet\\Services\\SharedAccess\\Parameters\\FirewallPolicy\\FirewallRules"

    private const val NOME_DA_REGRA = "Astra"

    private const val SIDECAR_NA_IMAGEM = "app\\resources\\astra-voz.exe"

    private fun quemFalaNaRede(): List<java.io.File> = buildList {
        Instalacao.exe?.let(::add)
        LocalizadorDoSidecar.caminho?.let(::add)
    }.distinctBy { it.absolutePath.lowercase() }

    private fun enderecoQueNaoMuda(): List<java.io.File> = buildList {
        Instalacao.exeFixo?.let(::add)
        Instalacao.fixa?.let { add(java.io.File(it, SIDECAR_NA_IMAGEM)) }
    }.distinctBy { it.absolutePath.lowercase() }

    private fun regrasGravadas(): List<String>? = runCatching {
        Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, REGRAS_FIREWALL)
            .values.filterIsInstance<String>()
            .map { it.lowercase() }
    }.getOrNull()

    private fun regrasDe(todas: List<String>, binario: java.io.File): List<String> {
        val alvo = binario.absolutePath.lowercase()
        return todas
            .filter { it.contains("|app=$alvo|") || it.endsWith("|app=$alvo") }
            .filter { it.contains("|active=true|") }
    }

    private fun liberado(todas: List<String>, binario: java.io.File): Boolean =
        regrasDe(todas, binario).any { it.contains("|action=allow|") }

    private fun apelido(binario: java.io.File): String =
        if (binario.name.contains("voz", ignoreCase = true)) "a voz" else "o Astra"

    fun rede(): Checagem {
        if (System.getProperty("jpackage.app-path") == null) {
            return Checagem(
                Permissao.REDE, Acesso.PENDENTE,
                "Rodando pelo Gradle — não há executável do Astra para procurar no firewall.",
            )
        }
        val correndo = quemFalaNaRede()
        if (correndo.isEmpty()) {
            return Checagem(
                Permissao.REDE, Acesso.PENDENTE,
                "Não encontrei os executáveis do Astra para conferir no firewall.",
                "windowsdefender://network/",
            )
        }
        val todas = regrasGravadas() ?: return Checagem(
            Permissao.REDE, Acesso.PENDENTE,
            "Não foi possível ler as regras do firewall. Se a call não conectar, confira o Astra na lista de aplicativos permitidos.",
            "windowsdefender://network/",
        )

        val bloqueados = correndo.filter { alvo ->
            regrasDe(todas, alvo).any { it.contains("|action=block|") }
        }
        val semRegra = correndo.filter { !liberado(todas, it) }
        val fixoCoberto = enderecoQueNaoMuda().let { fixos ->
            fixos.isNotEmpty() && fixos.all { liberado(todas, it) }
        }

        return when {
            bloqueados.isNotEmpty() -> Checagem(
                Permissao.REDE, Acesso.BLOQUEADO,
                "Existe uma regra bloqueando ${bloqueados.joinToString(" e ", transform = ::apelido)} " +
                    "no firewall — provavelmente de um \"Cancelar\" no aviso do Windows. A call não " +
                    "conecta assim. Liberar remove o bloqueio.",
                "windowsdefender://network/",
            )
            semRegra.isNotEmpty() -> Checagem(
                Permissao.REDE, Acesso.PENDENTE,
                "O Windows ainda vai perguntar por ${semRegra.joinToString(" e ", transform = ::apelido)} " +
                    "na primeira call. Liberar agora resolve antes — e vale também para as versões que vierem.",
                "windowsdefender://network/",
            )
            !fixoCoberto -> Checagem(
                Permissao.REDE, Acesso.OK,
                "Liberado, mas só nesta versão: a liberação está presa ao endereço de agora e a " +
                    "próxima atualização faria o Windows perguntar de novo. Liberar estende para o " +
                    "endereço fixo e encerra isso.",
                "windowsdefender://network/",
                podeDesfazer = true,
            )
            else -> Checagem(
                Permissao.REDE, Acesso.OK,
                "Liberado no endereço fixo do Astra — atualizar não derruba mais a liberação.",
                podeDesfazer = true,
            )
        }
    }

    private val NOSSOS_BINARIOS = setOf("astra.exe", "astra-voz.exe")

    private fun caminhosComRegra(): List<String> {
        val marca = "|app="
        return (regrasGravadas() ?: emptyList()).mapNotNull { regra ->
            val comeco = regra.indexOf(marca)
            if (comeco < 0) return@mapNotNull null
            val resto = regra.substring(comeco + marca.length)
            val fim = resto.indexOf('|')
            val caminho = if (fim < 0) resto else resto.substring(0, fim)
            caminho.takeIf { it.substringAfterLast('\\') in NOSSOS_BINARIOS }
        }.distinct()
    }

    fun liberarNoFirewall(): Boolean {
        val alvos = (quemFalaNaRede() + enderecoQueNaoMuda())
            .distinctBy { it.absolutePath.lowercase() }
        if (alvos.isEmpty()) return false
        val mortas = caminhosComRegra().filter { !java.io.File(it).exists() }
        return elevado(apagar(mortas) + liberacao(alvos))
    }

    fun revogarNoFirewall(): Boolean {
        val todas = caminhosComRegra()
        if (todas.isEmpty()) return true
        return elevado(apagar(todas))
    }

    private fun apagar(caminhos: List<String>): String = caminhos.joinToString("") { caminho ->
        val p = caminho.replace("'", "''")
        "netsh advfirewall firewall delete rule name=all program='$p' | Out-Null\n"
    }

    private fun liberacao(alvos: List<java.io.File>): String = alvos.joinToString("") { alvo ->
        val p = alvo.absolutePath.replace("'", "''")
        "netsh advfirewall firewall delete rule name=all program='$p' | Out-Null\n" +
            "netsh advfirewall firewall add rule name='$NOME_DA_REGRA' dir=in action=allow " +
            "program='$p' enable=yes profile=any | Out-Null\n" +
            "netsh advfirewall firewall add rule name='$NOME_DA_REGRA' dir=out action=allow " +
            "program='$p' enable=yes profile=any | Out-Null\n"
    }

    private fun elevado(roteiro: String): Boolean {
        val embrulhado = java.util.Base64.getEncoder()
            .encodeToString(roteiro.toByteArray(Charsets.UTF_16LE))
        val comando = "try { Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile','-EncodedCommand','" + embrulhado + "' -ErrorAction Stop } " +
            "catch { exit 1 }"
        return runCatching {
            val p = ProcessBuilder("powershell", "-NoProfile", "-Command", comando)
                .redirectErrorStream(true)
                .start()
            if (p.waitFor(2, java.util.concurrent.TimeUnit.MINUTES)) p.exitValue() == 0
            else { p.destroy(); false }
        }.getOrDefault(false)
    }

    fun abrirAjustes(uri: String) {
        runCatching { ProcessBuilder("cmd", "/c", "start", "", uri).start() }
    }
}
