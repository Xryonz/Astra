package app.astra.desktop.voice

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
                "O Windows registra o Astra no primeiro aviso. Clique em permitir para mandar um agora.",
                "ms-settings:notifications",
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

    fun rede(): Checagem {
        val exe = System.getProperty("jpackage.app-path")?.lowercase()
            ?: return Checagem(
                Permissao.REDE, Acesso.PENDENTE,
                "Rodando pelo Gradle — não há executável do Astra para procurar no firewall.",
            )

        val minhas = runCatching {
            Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, REGRAS_FIREWALL)
                .values.filterIsInstance<String>()
                .map { it.lowercase() }
                .filter { it.contains("|app=$exe|") || it.endsWith("|app=$exe") }
        }.getOrNull() ?: return Checagem(
            Permissao.REDE, Acesso.PENDENTE,
            "Não foi possível ler as regras do firewall. Se a call não conectar, confira o Astra na lista de aplicativos permitidos.",
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

    fun liberarNoFirewall(): Boolean {
        val exe = System.getProperty("jpackage.app-path") ?: return false
        val roteiro = java.io.File(System.getProperty("java.io.tmpdir"), "astra-liberar-firewall.ps1")
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
        val comando = "try { Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','\"${roteiro.absolutePath}\"' " +
            "-ErrorAction Stop } catch { exit 1 }"
        val ok = runCatching {
            val p = ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", comando)
                .redirectErrorStream(true)
                .start()
            if (p.waitFor(2, java.util.concurrent.TimeUnit.MINUTES)) p.exitValue() == 0
            else { p.destroy(); false }
        }.getOrDefault(false)
        roteiro.delete()
        return ok
    }

    fun abrirAjustes(uri: String) {
        runCatching { ProcessBuilder("cmd", "/c", "start", "", uri).start() }
    }
}
