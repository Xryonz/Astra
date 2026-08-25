package app.astra.desktop.auth

import app.astra.desktop.Multi
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

class SessionStore {
    private val dir: File = run {
        val appData = System.getenv("APPDATA")
        val base = if (appData != null) File(appData, "Astra") else File(System.getProperty("user.home"), ".astra")
        val slot = Multi.slot
        if (slot != null) File("${base.path}-teste$slot") else base
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

    fun deviceId(): String = synchronized(lock) {
        uiPref("deviceId") ?: java.util.UUID.randomUUID().toString().also { setUiPref("deviceId", it) }
    }
}
