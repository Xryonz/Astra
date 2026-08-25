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

object AtalhosGlobais {

    private const val WH_KEYBOARD_LL = 13
    private const val WM_KEYDOWN = 0x0100
    private const val WM_KEYUP = 0x0101
    private const val WM_SYSKEYDOWN = 0x0104
    private const val WM_SYSKEYUP = 0x0105
    private const val WM_QUIT = 0x0012
    private const val VK_ESCAPE = 0x1B

    private interface U32Extra : StdCallLibrary {
        fun GetKeyNameTextW(lParam: Int, buffer: CharArray, tamanho: Int): Int
        fun MapVirtualKeyW(codigo: Int, tipo: Int): Int
        companion object {
            val I: U32Extra? = runCatching {
                Native.load("user32", U32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

    @Volatile private var observadas: Map<Int, (Boolean) -> Unit> = emptyMap()
    @Volatile private var capturando: ((Int) -> Unit)? = null

    @Volatile private var thread: Thread? = null
    @Volatile private var idDaThread = 0
    private var proc: WinUser.LowLevelKeyboardProc? = null
    private var gancho: WinUser.HHOOK? = null

    private val entrega = Executors.newSingleThreadExecutor { r ->
        Thread(r, "astra-atalhos").apply { isDaemon = true }
    }

    @Synchronized
    fun observar(mapa: Map<Int, (Boolean) -> Unit>) {
        observadas = mapa
        ajustar()
    }

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
            proc = null
            idDaThread = 0
            thread = null
            return
        }

        val msg = WinUser.MSG()
        while (u.GetMessage(msg, null, 0, 0) > 0) {
        }

        runCatching { u.UnhookWindowsHookEx(gancho) }
        gancho = null
        proc = null
    }

    private fun despachar(mensagem: Int, vk: Int) {
        val desceu = mensagem == WM_KEYDOWN || mensagem == WM_SYSKEYDOWN
        val subiu = mensagem == WM_KEYUP || mensagem == WM_SYSKEYUP
        if (!desceu && !subiu) return

        val captura = capturando
        if (captura != null) {
            if (!desceu) return
            capturando = null
            val escolhida = if (vk == VK_ESCAPE) 0 else vk
            entrega.execute {
                captura(escolhida)
                cancelarCaptura()
            }
            return
        }

        val aviso = observadas[vk] ?: return
        entrega.execute { aviso(desceu) }
    }

    private val NOMES_FIXOS = mapOf(
        0x10 to "shift", 0x11 to "ctrl", 0x12 to "alt",
        0xA0 to "shift esq", 0xA1 to "shift dir",
        0xA2 to "ctrl esq", 0xA3 to "ctrl dir",
        0xA4 to "alt esq", 0xA5 to "alt dir",
        0x20 to "espaço", 0x09 to "tab", 0x14 to "caps lock",
        0x5B to "windows esq", 0x5C to "windows dir",
    )

    private val ESTENDIDAS = setOf(
        0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x2D, 0x2E, 0x90, 0x6F,
    )
}
