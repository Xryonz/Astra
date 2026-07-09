package app.astra.desktop.auth

import java.io.File
import java.util.Properties

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val displayName: String,
)

// Sessao persistida em %APPDATA%/Astra (pasta por-usuario do Windows; ~/.astra
// no resto). Arquivo simples por ora — endurecer com DPAPI/keychain na passada
// de seguranca (tokens em texto plano no perfil do usuario, mesmo modelo que
// muitos apps desktop usam, mas da pra melhorar).
class SessionStore {
    private val dir: File = run {
        val appData = System.getenv("APPDATA")
        if (appData != null) File(appData, "Astra") else File(System.getProperty("user.home"), ".astra")
    }
    private val file = File(dir, "session.properties")

    fun load(): Session? {
        if (!file.exists()) return null
        return runCatching {
            val p = Properties().apply { file.inputStream().use { load(it) } }
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
        file.outputStream().use { p.store(it, "Astra session") }
    }

    fun clear() {
        file.delete()
    }
}
