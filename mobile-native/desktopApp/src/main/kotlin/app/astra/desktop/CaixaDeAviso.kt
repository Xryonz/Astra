package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

object CaixaDeAviso {
    private const val MB_OK = 0x00000000
    private const val MB_ICONWARNING = 0x00000030
    private const val MB_ICONERROR = 0x00000010
    private const val MB_SETFOREGROUND = 0x00010000
    private const val MB_TOPMOST = 0x00040000

    private interface U32 : StdCallLibrary {
        fun MessageBoxW(dono: Pointer?, texto: String, titulo: String, tipo: Int): Int
        companion object {
            val I: U32? = runCatching {
                Native.load("user32", U32::class.java, W32APIOptions.UNICODE_OPTIONS)
            }.getOrNull()
        }
    }

    fun aviso(texto: String) = mostrar(texto, MB_ICONWARNING, javax.swing.JOptionPane.WARNING_MESSAGE)

    fun erro(texto: String) = mostrar(texto, MB_ICONERROR, javax.swing.JOptionPane.ERROR_MESSAGE)

    private fun mostrar(texto: String, simboloNativo: Int, simboloDoSwing: Int) {
        val nativo = U32.I
        if (nativo != null) {
            nativo.MessageBoxW(
                null, texto, "Astra",
                MB_OK or simboloNativo or MB_SETFOREGROUND or MB_TOPMOST,
            )
            return
        }
        runCatching {
            javax.swing.JOptionPane.showMessageDialog(null, texto, "Astra", simboloDoSwing)
        }
    }
}
