package app.astra.desktop.auth

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val displayName: String,
)

// Sessao persistida em %APPDATA%/Astra (pasta por-usuario do Windows; ~/.astra
// no resto). Tokens cifrados em repouso com DPAPI (CryptProtectData amarra o
// segredo a conta do Windows); fora do Windows cai no arquivo plano.
//
// Concorrencia: o AuthInterceptor chama load() em TODA request e o
// authenticator chama save() no refresh — com cifra, leitura no meio de uma
// escrita corrompe o decrypt. Por isso: cache em memoria (disco+DPAPI so uma
// vez), lock nas mutacoes e escrita atomica (tmp + move).
class SessionStore {
    private val dir: File = run {
        val appData = System.getenv("APPDATA")
        if (appData != null) File(appData, "Astra") else File(System.getProperty("user.home"), ".astra")
    }
    private val file = File(dir, "session.bin")
    private val legacyFile = File(dir, "session.properties")

    private val lock = Any()

    @Volatile
    private var cache: Session? = null

    @Volatile
    private var loaded = false

    fun load(): Session? {
        if (loaded) return cache
        synchronized(lock) {
            if (loaded) return cache
            migrateLegacy()
            cache = readDisk()
            loaded = true
            return cache
        }
    }

    fun save(s: Session) {
        synchronized(lock) {
            cache = s
            loaded = true
            // Disco e best-effort: se a cifra/escrita falhar, a sessao da
            // execucao atual segue viva no cache (re-login so no proximo boot).
            runCatching {
                dir.mkdirs()
                val p = Properties()
                p.setProperty("accessToken", s.accessToken)
                p.setProperty("refreshToken", s.refreshToken)
                p.setProperty("userId", s.userId)
                p.setProperty("displayName", s.displayName)
                val out = ByteArrayOutputStream()
                p.store(out, "Astra session")
                val plain = out.toByteArray()
                val cipher = if (Platform.isWindows()) Crypt32Util.cryptProtectData(plain) else plain
                val tmp = File(dir, "session.bin.tmp")
                tmp.writeBytes(cipher)
                Files.move(
                    tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            cache = null
            loaded = true
            file.delete()
            legacyFile.delete()
        }
    }

    private fun readDisk(): Session? {
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

    // Sessao antiga em texto plano vira cifrada (sem re-login). O arquivo plano
    // SO morre depois do cifrado confirmado no disco — senao segura a sessao.
    private fun migrateLegacy() {
        if (!legacyFile.exists()) return
        if (file.exists()) {
            legacyFile.delete()
            return
        }
        val s = runCatching {
            val p = Properties().apply { legacyFile.inputStream().use { load(it) } }
            Session(
                accessToken = p.getProperty("accessToken") ?: return,
                refreshToken = p.getProperty("refreshToken") ?: return,
                userId = p.getProperty("userId") ?: return,
                displayName = p.getProperty("displayName") ?: "",
            )
        }.getOrNull() ?: return
        save(s)
        if (file.exists()) legacyFile.delete()
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
