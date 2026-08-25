package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary

object WindowsAppId {
    const val AUMID = "Xryonz.Astra"

    private interface Shell32 : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int
    }

    fun aplicar() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        runCatching {
            Native.load("shell32", Shell32::class.java)
                .SetCurrentProcessExplicitAppUserModelID(WString(AUMID))
        }
    }
}
