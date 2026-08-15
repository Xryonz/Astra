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

    // ESTE RECURSO NAO PODE CUSTAR NADA, e o motivo e onde ele roda: a pessoa esta
    // JOGANDO. Um engasgo de 8ms a cada 5s nao aparece em medicao de media e aparece
    // como quadro perdido na tela dela. Entao a conta e feita UMA vez por programa,
    // e depois disso o laco custa duas chamadas de Win32 que respondem em nanosegundos.
    //
    // Onde estava o custo (e era real):
    //  - `ProcessHandle.info()` conversa com o sistema pra achar o caminho do exe;
    //  - ler a assinatura do executavel e LEITURA DE DISCO.
    // Os dois aconteciam a cada 5s, sempre sobre o mesmo programa, sempre com a
    // mesma resposta. Um jogo aberto por duas horas fazia isso 1440 vezes pra
    // descobrir 1440 vezes que ainda era o mesmo jogo.
    private var ultimoPid = -1L
    private var ultimaResposta: String? = null
    // Chave = caminho do executavel. Fica pequeno sozinho: e a quantidade de
    // programas DIFERENTES que ficaram na frente desde que o app abriu.
    private val nomePorCaminho = HashMap<String, String?>()

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

        // MESMO PROCESSO DE ANTES: a resposta ja e conhecida e nada abaixo daqui
        // precisa rodar. E o caso esmagadoramente comum — ninguem troca de programa
        // a cada cinco segundos.
        //
        // Reaproveitar pid tem um risco conhecido: o Windows reusa numero de processo
        // depois que o antigo morre. O estrago possivel e mostrar o programa errado
        // por ate 5s, ate a proxima espiada de um pid diferente. Trocar isso por uma
        // consulta a mais a cada volta seria pagar sempre pra evitar um engano raro e
        // que se corrige sozinho.
        if (pid == ultimoPid) return ultimaResposta

        // ProcessHandle e Java puro: nao precisa de OpenProcess nem de fechar
        // handle, e ja resolve o caminho do executavel pra processos do mesmo
        // usuario — que sao todos os que interessam aqui.
        val caminho = runCatching { ProcessHandle.of(pid).orElse(null)?.info()?.command()?.orElse(null) }
            .getOrNull() ?: return null

        val resposta = nomeDoCaminho(caminho)
        ultimoPid = pid
        ultimaResposta = resposta
        return resposta
    }

    // A parte cara, isolada e memoizada por caminho. Um jogo fechado e reaberto ganha
    // pid novo mas continua no mesmo caminho — e ai nem a leitura de disco se repete.
    private fun nomeDoCaminho(caminho: String): String? = nomePorCaminho.getOrPut(caminho) {
        val arquivo = File(caminho)
        val exe = arquivo.name.lowercase(Locale.ROOT)
        when {
            exe in DO_SISTEMA -> null
            exe in NAVEGADORES -> "Navegando"
            // O proprio Astra por outro caminho (multi-conta, versao antiga aberta).
            exe == "astra.exe" -> null
            else -> nomeBonito(arquivo) ?: nomeCru(arquivo.name)
        }
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
