package app.astra.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.W32APIOptions
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.util.Locale

// "O QUE A PESSOA ESTA USANDO AGORA" — a metade que olha o sistema.
//
// UMA LINHA QUE NAO SE CRUZA: O TITULO DA JANELA NUNCA E LIDO.
//
// Nao e configuracao, e limite. Titulo de janela e texto livre e entrega coisa
// que ninguem quis contar: nome de arquivo aberto, aba do navegador, endereco,
// termo de busca, nome de quem esta na conversa. E por isso que o Discord so
// mostra nome de jogo vindo de lista curada, e por isso que o Windows Timeline
// morreu em 2021 — ele passou desse limite.
//
// O que sai daqui e SO o nome do programa, tirado da propria assinatura do
// executavel (a mesma coisa que o Windows mostra no Gerenciador de Tarefas).
// Navegador vira "Navegando", sem excecao e sem detalhe.
object AtividadeDoSistema {

    // ---- Win32 ----

    private interface U32 : StdCallLibrary {
        fun GetForegroundWindow(): Pointer?
        fun GetWindowThreadProcessId(janela: Pointer?, pid: IntByReference): Int
        companion object {
            val I: U32? = runCatching {
                Native.load("user32", U32::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

    // version.dll: le o bloco de informacao que todo executavel assinado carrega
    // (o mesmo que aparece em Propriedades > Detalhes do arquivo).
    private interface Ver : StdCallLibrary {
        fun GetFileVersionInfoSize(arquivo: String, handle: IntByReference?): Int
        fun GetFileVersionInfo(arquivo: String, handle: Int, tamanho: Int, dados: Pointer): Boolean
        fun VerQueryValue(bloco: Pointer, subBloco: String, saida: PointerByReference, tam: IntByReference): Boolean
        companion object {
            val I: Ver? = runCatching {
                Native.load("version", Ver::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

    private val meuPid = runCatching { ProcessHandle.current().pid() }.getOrDefault(-1L)

    // Programas que SAO a area de trabalho, nao um app que a pessoa escolheu abrir.
    // Sem esta lista, minimizar tudo publicaria "Windows Explorer" — que nao e uma
    // atividade, e a ausencia de uma.
    private val DO_SISTEMA = setOf(
        "explorer.exe", "dwm.exe", "applicationframehost.exe", "searchhost.exe",
        "searchapp.exe", "shellexperiencehost.exe", "startmenuexperiencehost.exe",
        "textinputhost.exe", "lockapp.exe", "sihost.exe", "taskmgr.exe",
    )

    // Navegador nao diz o programa, diz o que voce esta LENDO — e e por isso que ele
    // vira uma palavra so. "Navegando" e o teto do que da pra contar sem contar
    // demais; o nome do navegador ja seria mais informacao do que a pessoa espera
    // estar dando, e a aba seria informacao que ela nunca daria.
    private val NAVEGADORES = setOf(
        "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe",
        "opera_gx.exe", "vivaldi.exe", "zen.exe", "librewolf.exe", "arc.exe",
        "floorp.exe", "waterfox.exe", "thorium.exe", "chromium.exe",
    )

    // Resultado: null = "nao ha nada pra contar agora" (janela do sistema, o proprio
    // Astra, ou nao deu pra descobrir). Diferente de "" — este objeto nunca apaga
    // nada, so responde o que ve; quem decide apagar e o publicador.
    fun emPrimeiroPlano(): String? {
        val u = U32.I ?: return null
        val janela = u.GetForegroundWindow() ?: return null
        val ref = IntByReference()
        u.GetWindowThreadProcessId(janela, ref)
        val pid = ref.value.toLong()
        if (pid <= 0L || pid == meuPid) return null

        // ProcessHandle e Java puro: nao precisa de OpenProcess nem de fechar
        // handle, e ja resolve o caminho do executavel pra processos do mesmo
        // usuario — que sao todos os que interessam aqui.
        val caminho = runCatching { ProcessHandle.of(pid).orElse(null)?.info()?.command()?.orElse(null) }
            .getOrNull() ?: return null

        val arquivo = File(caminho)
        val exe = arquivo.name.lowercase(Locale.ROOT)
        if (exe in DO_SISTEMA) return null
        if (exe in NAVEGADORES) return "Navegando"
        // O proprio Astra por outro caminho (multi-conta, versao antiga aberta).
        if (exe == "astra.exe") return null

        return nomeBonito(arquivo) ?: nomeCru(arquivo.name)
    }

    // ---- nome legivel ----

    // Le FileDescription e, se faltar, ProductName. Nesta ordem porque e a que
    // acerta mais: "Google Chrome" e FileDescription enquanto o ProductName e
    // "Google Chrome"; ja em jogo, o FileDescription costuma ser o nome comercial
    // e o ProductName as vezes vem com o nome da engine.
    private fun nomeBonito(arquivo: File): String? {
        val v = Ver.I ?: return null
        val caminho = arquivo.absolutePath
        val tamanho = runCatching { v.GetFileVersionInfoSize(caminho, IntByReference()) }.getOrDefault(0)
        if (tamanho <= 0) return null

        val bloco = Memory(tamanho.toLong())
        if (!runCatching { v.GetFileVersionInfo(caminho, 0, tamanho, bloco) }.getOrDefault(false)) return null

        // O idioma do bloco nao e fixo: um exe alemao guarda os textos sob outra
        // chave. Perguntar qual e antes de ler evita o "funciona na minha maquina".
        val saida = PointerByReference()
        val tam = IntByReference()
        if (!runCatching { v.VerQueryValue(bloco, "\\VarFileInfo\\Translation", saida, tam) }.getOrDefault(false)) return null
        val ptr = saida.value ?: return null
        val idioma = ptr.getShort(0).toInt() and 0xFFFF
        val pagina = ptr.getShort(2).toInt() and 0xFFFF

        for (chave in listOf("FileDescription", "ProductName")) {
            val sub = "\\StringFileInfo\\%04x%04x\\%s".format(idioma, pagina, chave)
            val ok = runCatching { v.VerQueryValue(bloco, sub, saida, tam) }.getOrDefault(false)
            if (!ok) continue
            val texto = runCatching { saida.value?.getWideString(0) }.getOrNull()?.trim()
            if (!texto.isNullOrBlank()) return texto.take(48)
        }
        return null
    }

    // Sem assinatura no executavel (comum em jogo indie e em coisa compilada em
    // casa): usa o nome do arquivo, tirando o que e ruido de build. Um
    // "Palworld-Win64-Shipping.exe" vira "Palworld" em vez de sair como esta.
    private fun nomeCru(nomeDoArquivo: String): String? {
        var s = nomeDoArquivo.removeSuffix(".exe").removeSuffix(".EXE")
        for (sufixo in listOf("-Win64-Shipping", "-Win32-Shipping", "-Shipping", "_x64", "-x64", "64")) {
            if (s.endsWith(sufixo, ignoreCase = true)) s = s.dropLast(sufixo.length)
        }
        s = s.replace('_', ' ').replace('-', ' ').trim()
        return s.ifBlank { null }?.take(48)
    }
}
