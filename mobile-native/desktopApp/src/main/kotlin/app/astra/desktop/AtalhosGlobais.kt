package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.util.concurrent.Executors

// TECLA DE ATALHO QUE FUNCIONA COM O ASTRA NO FUNDO.
//
// Existe por um motivo só: apertar-para-falar e mudo/ensurdecer não valem nada se
// exigirem a janela do Astra em foco. Quem está no meio de uma partida não vai
// alt-tab pra se calar — ou a tecla chega de fora, ou a funcionalidade é enfeite.
//
// ---------------------------------------------------------------------------
// O QUE ISTO É, DITO SEM RODEIO: um gancho de teclado de baixo nível
// (`WH_KEYBOARD_LL`), que é a mesma peça de Win32 que um keylogger usaria. É o
// único jeito de o Windows entregar tecla a um processo sem foco, e é o que
// Discord e TeamSpeak fazem. Por isso ele é deliberadamente estreito:
//
//  • só compara `vkCode` contra as teclas que VOCÊ ligou nas configurações;
//  • não guarda, não conta e não escreve tecla nenhuma em lugar nenhum;
//  • não passa nada pela rede — o Astra não tem para onde mandar isto;
//  • SEMPRE chama `CallNextHookEx`, ou seja, NÃO engole a tecla: a mesma tecla
//    continua chegando no jogo, no navegador e em quem mais estiver ouvindo;
//  • só liga quando existe pelo menos uma tecla ligada (ou durante a captura de
//    uma). Sem atalho configurado, este arquivo não instala nada.
//
// Se um dia isto precisar de mais do que "esta tecla desceu/subiu", o pedido está
// errado, não o código.
// ---------------------------------------------------------------------------
//
// DOIS DETALHES DE WIN32 QUE DECIDEM SE FUNCIONA:
//
// 1. O gancho vive na THREAD que o instalou, e essa thread precisa de uma bomba
//    de mensagens (`GetMessage`) rodando. Sem o laço, o Windows nunca entrega o
//    callback e o gancho fica instalado e mudo — que é o formato de "não faz
//    nada e não dá erro".
//
// 2. O callback tem ORÇAMENTO DE TEMPO (`LowLevelHooksTimeout`, 300ms por
//    padrão). Estourar não dá exceção: o Windows desinstala o gancho em silêncio
//    e o atalho para de funcionar no meio da sessão. Por isso aqui dentro só
//    acontece uma comparação de inteiro, e o trabalho de verdade sai para outra
//    thread.
object AtalhosGlobais {

    private const val WH_KEYBOARD_LL = 13
    private const val WM_KEYDOWN = 0x0100
    private const val WM_KEYUP = 0x0101
    private const val WM_SYSKEYDOWN = 0x0104
    private const val WM_SYSKEYUP = 0x0105
    private const val WM_QUIT = 0x0012
    private const val VK_ESCAPE = 0x1B

    // Nome legível da tecla. Não é tabela na mão de propósito: o `GetKeyNameText`
    // devolve o nome que está IMPRESSO na tecla do teclado da pessoa, então um
    // ABNT2 diz "Ç" onde um teclado americano diria outra coisa.
    private interface U32Extra : StdCallLibrary {
        fun GetKeyNameTextW(lParam: Int, buffer: CharArray, tamanho: Int): Int
        fun MapVirtualKeyW(codigo: Int, tipo: Int): Int
        companion object {
            val I: U32Extra? = runCatching {
                Native.load("user32", U32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

    // Teclas observadas -> quem avisar. `true` = desceu, `false` = subiu.
    @Volatile private var observadas: Map<Int, (Boolean) -> Unit> = emptyMap()
    @Volatile private var capturando: ((Int) -> Unit)? = null

    @Volatile private var thread: Thread? = null
    @Volatile private var idDaThread = 0
    // Referência forte OBRIGATÓRIA: sem ela o coletor de lixo do Java recolhe o
    // callback enquanto o Windows ainda tem o ponteiro, e o processo morre com
    // uma falha nativa em vez de uma exceção.
    private var proc: WinUser.LowLevelKeyboardProc? = null
    private var gancho: WinUser.HHOOK? = null

    // Fila de UMA thread pro trabalho real. O callback só empurra pra cá.
    private val entrega = Executors.newSingleThreadExecutor { r ->
        Thread(r, "astra-atalhos").apply { isDaemon = true }
    }

    @Synchronized
    fun observar(mapa: Map<Int, (Boolean) -> Unit>) {
        observadas = mapa
        ajustar()
    }

    // Próxima tecla apertada vira o resultado. Escape = desistiu (chega 0).
    @Synchronized
    fun capturarProxima(aoEscolher: (Int) -> Unit) {
        capturando = aoEscolher
        ajustar()
    }

    @Synchronized
    fun cancelarCaptura() {
        capturando = null
        ajustar()
    }

    // Nome pra mostrar na tela. 0 = nenhuma.
    fun nomeDaTecla(vk: Int): String {
        if (vk == 0) return "nenhuma"
        NOMES_FIXOS[vk]?.let { return it }
        val api = U32Extra.I ?: return "tecla $vk"
        val scan = runCatching { api.MapVirtualKeyW(vk, 0) }.getOrDefault(0)
        if (scan == 0) return "tecla $vk"
        val estendida = vk in ESTENDIDAS
        val lParam = (scan shl 16) or (if (estendida) 1 shl 24 else 0)
        val buffer = CharArray(64)
        val n = runCatching { api.GetKeyNameTextW(lParam, buffer, buffer.size) }.getOrDefault(0)
        if (n <= 0) return "tecla $vk"
        return String(buffer, 0, n).trim().ifBlank { "tecla $vk" }.lowercase()
    }

    private fun ajustar() {
        if (observadas.isNotEmpty() || capturando != null) ligar() else desligar()
    }

    private fun ligar() {
        if (thread != null) return
        val t = Thread({ laco() }, "astra-gancho-teclado").apply { isDaemon = true }
        thread = t
        t.start()
    }

    private fun desligar() {
        val id = idDaThread
        thread = null
        idDaThread = 0
        if (id == 0) return
        // Acorda o `GetMessage` pra ele sair do laço; o desinstalar acontece lá,
        // na MESMA thread que instalou — que é a única que pode.
        runCatching {
            User32.INSTANCE.PostThreadMessage(id, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }
    }

    private fun laco() {
        val u = User32.INSTANCE
        idDaThread = runCatching { Kernel32.INSTANCE.GetCurrentThreadId() }.getOrDefault(0)
        val instancia = runCatching {
            WinDef.HINSTANCE().apply { pointer = Kernel32.INSTANCE.GetModuleHandle(null).pointer }
        }.getOrNull()

        val p = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
            if (nCode >= 0) despachar(wParam.toInt(), info.vkCode)
            u.CallNextHookEx(null, nCode, wParam, WinDef.LPARAM(Pointer.nativeValue(info.pointer)))
        }
        proc = p
        gancho = runCatching { u.SetWindowsHookEx(WH_KEYBOARD_LL, p, instancia, 0) }.getOrNull()
        if (gancho == null) {
            // Sem gancho não há atalho global — e é melhor não fingir que há. O app
            // segue inteiro; só as teclas de fora deixam de responder.
            proc = null
            idDaThread = 0
            thread = null
            return
        }

        val msg = WinUser.MSG()
        // > 0 encerra tanto no WM_QUIT (0) quanto em erro (-1).
        while (u.GetMessage(msg, null, 0, 0) > 0) {
            // Nada a fazer com a mensagem: o laço existe só pra manter o gancho vivo.
        }

        runCatching { u.UnhookWindowsHookEx(gancho) }
        gancho = null
        proc = null
    }

    // Roda DENTRO do callback do Windows: só comparação e um empurrão pra fila.
    private fun despachar(mensagem: Int, vk: Int) {
        val desceu = mensagem == WM_KEYDOWN || mensagem == WM_SYSKEYDOWN
        val subiu = mensagem == WM_KEYUP || mensagem == WM_SYSKEYUP
        if (!desceu && !subiu) return

        val captura = capturando
        if (captura != null) {
            if (!desceu) return
            capturando = null
            val escolhida = if (vk == VK_ESCAPE) 0 else vk
            // `cancelarCaptura` e nao `ajustar` direto: ela pega o mesmo cadeado das
            // outras entradas. Reavaliar se o gancho ainda faz falta fora do cadeado,
            // da thread da fila, corre contra quem esteja ligando outra tecla.
            entrega.execute {
                captura(escolhida)
                cancelarCaptura()
            }
            return
        }

        val aviso = observadas[vk] ?: return
        entrega.execute { aviso(desceu) }
    }

    // O `GetKeyNameText` erra justamente nas teclas que mais aparecem como atalho:
    // devolve "ctrl" pros dois lados, e nomes longos onde cabe pouco espaço.
    private val NOMES_FIXOS = mapOf(
        0x10 to "shift", 0x11 to "ctrl", 0x12 to "alt",
        0xA0 to "shift esq", 0xA1 to "shift dir",
        0xA2 to "ctrl esq", 0xA3 to "ctrl dir",
        0xA4 to "alt esq", 0xA5 to "alt dir",
        0x20 to "espaço", 0x09 to "tab", 0x14 to "caps lock",
        0x5B to "windows esq", 0x5C to "windows dir",
    )

    // Teclas que precisam do bit de "estendida" no lParam pra o Windows nomear
    // certo (senão as setas viram nomes do teclado numérico).
    private val ESTENDIDAS = setOf(
        0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x2D, 0x2E, 0x90, 0x6F,
    )
}
