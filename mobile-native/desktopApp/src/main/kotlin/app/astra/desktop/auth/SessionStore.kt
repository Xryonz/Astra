package app.astra.desktop.auth

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val displayName: String,
)

// Sessao persistida em %APPDATA%/Astra (pasta por-usuario do Windows; ~/.astra
// no resto). Tokens cifrados em repouso com DPAPI (CryptProtectData amarra o
// segredo a conta do Windows — mesmo esquema de senha do Chrome/Edge); fora do
// Windows cai no arquivo plano. session.properties legado (texto plano) migra
// pro cifrado no primeiro load e o arquivo antigo morre.
class SessionStore {
    private val dir: File = run {
        val appData = System.getenv("APPDATA")
        if (appData != null) File(appData, "Astra") else File(System.getProperty("user.home"), ".astra")
    }
    private val file = File(dir, "session.bin")
    private val legacyFile = File(dir, "session.properties")

    fun load(): Session? {
        migrateLegacy()
        if (!file.exists()) return null
        return runCatching {
            val raw = file.readBytes()
            val plain = if (Platform.isWindows()) Crypt32Util.cryptUnprotectData(raw) else raw
            val p = Properties().apply { load(ByteArrayInputStream(plain)) }
            Session(
                accessToken = p.getProperty("accessToken") ?: return null,
                refreshToken = p.getProperty("refreshToken") ?: return null,
                userId = p.getProperty("userId") ?: return null,
                displayName = p.getProperty("displayName") ?: "",
            )
        }.getOrNull()
    }

    fun save(s: Session) {
        dir.mkdirs()
        val p = Properties()
        p.setProperty("accessToken", s.accessToken)
        p.setProperty("refreshToken", s.refreshToken)
        p.setProperty("userId", s.userId)
        p.setProperty("displayName", s.displayName)
        val out = ByteArrayOutputStream()
        p.store(out, "Astra session")
        val plain = out.toByteArray()
        file.writeBytes(if (Platform.isWindows()) Crypt32Util.cryptProtectData(plain) else plain)
    }

    fun clear() {
        file.delete()
        legacyFile.delete()
    }

    // Sessao antiga em texto plano vira cifrada (sem re-login) e o plano some.
    private fun migrateLegacy() {
        if (!legacyFile.exists()) return
        if (!file.exists()) {
            runCatching {
                val p = Properties().apply { legacyFile.inputStream().use { load(it) } }
                save(
                    Session(
                        accessToken = p.getProperty("accessToken") ?: return@runCatching,
                        refreshToken = p.getProperty("refreshToken") ?: return@runCatching,
                        userId = p.getProperty("userId") ?: return@runCatching,
                        displayName = p.getProperty("displayName") ?: "",
                    ),
                )
            }
        }
        legacyFile.delete()
    }

    // Prefs de UI (ex: ultima constelacao/orbita aberta) — arquivo separado da
    // sessao pra sobreviver a logout/refresh de token.
    private val uiFile = File(dir, "ui.properties")

    fun uiPref(key: String): String? {
        if (!uiFile.exists()) return null
        return runCatching {
            Properties().apply { uiFile.inputStream().use { load(it) } }.getProperty(key)
        }.getOrNull()
    }

    fun setUiPref(key: String, value: String?) {
        dir.mkdirs()
        val p = Properties().apply { if (uiFile.exists()) runCatching { uiFile.inputStream().use { load(it) } } }
        if (value == null) p.remove(key) else p.setProperty(key, value)
        runCatching { uiFile.outputStream().use { p.store(it, "Astra ui prefs") } }
    }
}
