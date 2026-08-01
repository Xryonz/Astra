package app.astra.desktop.voice

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
// Uma coisa que NÃO tem checagem: transmissão de tela. No Windows ela não pede
// permissão nenhuma (diferente do macOS) — inventar um cadeado aqui seria teatro.
// O que pode faltar é o ffmpeg do pacote, e disso a gente sabe olhando o arquivo.

enum class Acesso { OK, BLOQUEADO, SEM_APARELHO, MUDO }

data class Checagem(
    val titulo: String,
    val acesso: Acesso,
    val explica: String,
    /** Página exata das Configurações do Windows, quando existe uma pra consertar. */
    val ajustes: String? = null,
)

object PermissoesWindows {

    fun todas(): List<Checagem> = listOf(microfone(), camera(), saida(), tela())

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
                "Microfone", Acesso.SEM_APARELHO,
                "Nenhum microfone encontrado. Conecte um e clique em conferir de novo.",
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
                "Microfone", Acesso.BLOQUEADO,
                "O Windows não deixou o Astra abrir o microfone. Ligue o acesso pra aplicativos da área de trabalho.",
                "ms-settings:privacy-microphone",
            )
            soZeros -> Checagem(
                "Microfone", Acesso.MUDO,
                "O microfone abriu, mas não chegou som nenhum. Costuma ser a privacidade do Windows fechada — ou o mic mudo no botão do aparelho.",
                "ms-settings:privacy-microphone",
            )
            else -> Checagem("Microfone", Acesso.OK, "Ouvindo normalmente (${nomes.first()}).")
        }
    }

    // Só enumera: abrir a webcam acenderia a luz dela, e piscar a luz de alguém
    // numa tela de boas-vindas assusta com razão.
    fun camera(): Checagem {
        val cams = runCatching { MediaDevices.getVideoCaptureDevices() }.getOrDefault(emptyList())
        return if (cams.isEmpty()) {
            Checagem(
                "Câmera", Acesso.SEM_APARELHO,
                "Nenhuma câmera encontrada. Se você tem uma, o acesso pode estar fechado no Windows.",
                "ms-settings:privacy-webcam",
            )
        } else {
            Checagem("Câmera", Acesso.OK, "${cams.size} encontrada(s). A luz só acende quando você transmitir.")
        }
    }

    fun saida(): Checagem {
        val saidas = AudioDevices.outputs()
        return if (saidas.isEmpty()) {
            Checagem("Som", Acesso.SEM_APARELHO, "Nenhuma saída de áudio encontrada — você não ouviria a call.", "ms-settings:sound")
        } else {
            Checagem("Som", Acesso.OK, "${saidas.size} saída(s) disponível(is).")
        }
    }

    fun tela(): Checagem {
        val ff = FfmpegLocator.path
        return if (ff == null) {
            Checagem(
                "Transmitir a tela", Acesso.SEM_APARELHO,
                "O componente de captura não veio no pacote. Reinstale o Astra.",
            )
        } else {
            Checagem("Transmitir a tela", Acesso.OK, "Pronto. O Windows não pede permissão pra isto.")
        }
    }

    // `start` do cmd é o que entende ms-settings: — Desktop.browse() só lida com
    // http/https e recusa esse esquema.
    fun abrirAjustes(uri: String) {
        runCatching { ProcessBuilder("cmd", "/c", "start", "", uri).start() }
    }
}
