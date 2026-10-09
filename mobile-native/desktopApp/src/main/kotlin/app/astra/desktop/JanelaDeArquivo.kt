package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object JanelaDeArquivo {
    private val CLSID_FILE_OPEN_DIALOG = Guid.CLSID("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}")
    private val IID_FILE_OPEN_DIALOG = Guid.IID("{d57c7288-d4ad-4768-be02-9d969532d960}")

    private const val MOSTRAR = 3
    private const val DEFINIR_OPCOES = 9
    private const val LER_OPCOES = 10
    private const val DEFINIR_TITULO = 17
    private const val LER_RESULTADO = 20
    private const val LER_RESULTADOS = 27
    private const val CONTAR_ITENS = 7
    private const val ITEM_NA_POSICAO = 8
    private const val NOME_DE_EXIBICAO = 5

    private const val SO_DO_DISCO = 0x40
    private const val VARIOS = 0x200
    private const val CAMINHO_PRECISA_EXISTIR = 0x800
    private const val ARQUIVO_PRECISA_EXISTIR = 0x1000
    private const val CAMINHO_COMPLETO = 0x80058000.toInt()
    private const val APARTAMENTO_UNICO = 0x2
    private const val CANCELADA = 0x800704C7.toInt()

    private const val VEZES_PARA_SAIR = 40
    private const val PAUSA_PARA_SAIR_MS = 25L

    fun escolher(titulo: String, varios: Boolean): List<File> {
        val dona = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?.let { runCatching { Native.getWindowPointer(it) }.getOrNull() }
        val escolhidos = AtomicReference<List<File>?>()
        val naInterface = EventQueue.isDispatchThread()
        val laco = if (naInterface) Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop() else null
        val trabalho = thread(name = "janela-de-arquivo", isDaemon = true) {
            escolhidos.set(abrirNaThreadPropria(titulo, varios, dona))
            if (laco != null) repeat(VEZES_PARA_SAIR) {
                if (laco.exit()) return@thread
                Thread.sleep(PAUSA_PARA_SAIR_MS)
            }
        }
        if (laco != null) laco.enter() else trabalho.join()
        return escolhidos.get() ?: pelaJanelaAntiga(titulo, varios)
    }

    private fun abrirNaThreadPropria(titulo: String, varios: Boolean, dona: Pointer?): List<File>? {
        val iniciou = Ole32.INSTANCE.CoInitializeEx(null, APARTAMENTO_UNICO)
        if (iniciou.toInt() < 0) return null
        try {
            return runCatching { abrir(titulo, varios, dona) }.getOrNull()
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun abrir(titulo: String, varios: Boolean, dona: Pointer?): List<File>? {
        val criado = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            CLSID_FILE_OPEN_DIALOG, null, WTypes.CLSCTX_INPROC_SERVER, IID_FILE_OPEN_DIALOG, criado,
        )
        if (hr.toInt() < 0) return null
        val janela = Com(criado.value)
        try {
            val opcoes = IntByReference()
            janela.chamar(LER_OPCOES, opcoes)
            val novas = opcoes.value or SO_DO_DISCO or CAMINHO_PRECISA_EXISTIR or ARQUIVO_PRECISA_EXISTIR or
                (if (varios) VARIOS else 0)
            janela.chamar(DEFINIR_OPCOES, novas)
            janela.chamar(DEFINIR_TITULO, WString(titulo))
            val mostrou = janela.chamar(MOSTRAR, dona)
            if (mostrou == CANCELADA) return emptyList()
            if (mostrou < 0) return null
            return if (varios) lerVarios(janela) else listOfNotNull(lerUm(janela))
        } finally {
            janela.Release()
        }
    }

    private fun lerUm(janela: Com): File? {
        val item = PointerByReference()
        if (janela.chamar(LER_RESULTADO, item) < 0) return null
        return Com(item.value).caminhoESolta()
    }

    private fun lerVarios(janela: Com): List<File> {
        val lista = PointerByReference()
        if (janela.chamar(LER_RESULTADOS, lista) < 0) return emptyList()
        val itens = Com(lista.value)
        try {
            val quantos = IntByReference()
            if (itens.chamar(CONTAR_ITENS, quantos) < 0) return emptyList()
            return (0 until quantos.value).mapNotNull { i ->
                val item = PointerByReference()
                if (itens.chamar(ITEM_NA_POSICAO, i, item) < 0) null else Com(item.value).caminhoESolta()
            }
        } finally {
            itens.Release()
        }
    }

    private fun Com.caminhoESolta(): File? {
        try {
            val nome = PointerByReference()
            if (chamar(NOME_DE_EXIBICAO, CAMINHO_COMPLETO, nome) < 0) return null
            val texto = nome.value.getWideString(0)
            Ole32.INSTANCE.CoTaskMemFree(nome.value)
            return File(texto)
        } finally {
            Release()
        }
    }

    private fun pelaJanelaAntiga(titulo: String, varios: Boolean): List<File> {
        val dlg = FileDialog(null as Frame?, titulo, FileDialog.LOAD)
        dlg.isMultipleMode = varios
        dlg.isVisible = true
        return dlg.files?.toList().orEmpty()
    }

    private class Com(ponteiro: Pointer) : Unknown(ponteiro) {
        fun chamar(indice: Int, vararg argumentos: Any?): Int =
            _invokeNativeInt(indice, arrayOf(pointer, *argumentos))
    }
}
