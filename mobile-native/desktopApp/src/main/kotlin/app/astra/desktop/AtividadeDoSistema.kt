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

object AtividadeDoSistema {

    private interface U32 : StdCallLibrary {
        fun GetForegroundWindow(): Pointer?
        fun GetWindowThreadProcessId(janela: Pointer?, pid: IntByReference): Int
        companion object {
            val I: U32? = runCatching {
                Native.load("user32", U32::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

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

    private val DO_SISTEMA = setOf(
        "explorer.exe", "dwm.exe", "applicationframehost.exe", "searchhost.exe",
        "searchapp.exe", "shellexperiencehost.exe", "startmenuexperiencehost.exe",
        "textinputhost.exe", "lockapp.exe", "sihost.exe", "taskmgr.exe",
    )

    private val NAVEGADORES = setOf(
        "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe",
        "opera_gx.exe", "vivaldi.exe", "zen.exe", "librewolf.exe", "arc.exe",
        "floorp.exe", "waterfox.exe", "thorium.exe", "chromium.exe",
    )

    private var ultimoPid = -1L
    private var ultimaResposta: String? = null
    private val nomePorCaminho = HashMap<String, String?>()

    fun emPrimeiroPlano(): String? {
        val u = U32.I ?: return null
        val janela = u.GetForegroundWindow() ?: return null
        val ref = IntByReference()
        u.GetWindowThreadProcessId(janela, ref)
        val pid = ref.value.toLong()
        if (pid <= 0L || pid == meuPid) return null

        if (pid == ultimoPid) return ultimaResposta

        val caminho = runCatching { ProcessHandle.of(pid).orElse(null)?.info()?.command()?.orElse(null) }
            .getOrNull() ?: return null

        val resposta = nomeDoCaminho(caminho)
        ultimoPid = pid
        ultimaResposta = resposta
        return resposta
    }

    private fun nomeDoCaminho(caminho: String): String? = nomePorCaminho.getOrPut(caminho) {
        val arquivo = File(caminho)
        val exe = arquivo.name.lowercase(Locale.ROOT)
        when {
            exe in DO_SISTEMA -> null
            exe in NAVEGADORES -> "Navegando"
            exe == "astra.exe" -> null
            else -> nomeBonito(arquivo) ?: nomeCru(arquivo.name)
        }
    }

    private fun nomeBonito(arquivo: File): String? {
        val v = Ver.I ?: return null
        val caminho = arquivo.absolutePath
        val tamanho = runCatching { v.GetFileVersionInfoSize(caminho, IntByReference()) }.getOrDefault(0)
        if (tamanho <= 0) return null

        val bloco = Memory(tamanho.toLong())
        if (!runCatching { v.GetFileVersionInfo(caminho, 0, tamanho, bloco) }.getOrDefault(false)) return null

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

    private fun nomeCru(nomeDoArquivo: String): String? {
        var s = nomeDoArquivo.removeSuffix(".exe").removeSuffix(".EXE")
        for (sufixo in listOf("-Win64-Shipping", "-Win32-Shipping", "-Shipping", "_x64", "-x64", "64")) {
            if (s.endsWith(sufixo, ignoreCase = true)) s = s.dropLast(sufixo.length)
        }
        s = s.replace('_', ' ').replace('-', ' ').trim()
        return s.ifBlank { null }?.take(48)
    }
}
