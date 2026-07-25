package app.astra.desktop.auth

import app.astra.shared.AstraShared
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

// Login com Google no desktop via LOOPBACK (o padrao OAuth pra apps nativos, sem
// navegador embutido): sobe um HttpServer em 127.0.0.1:porta-efemera, abre o
// navegador do sistema na rota /api/auth/google (passando porta+nonce no `state`),
// e espera o backend redirecionar de volta pra 127.0.0.1/callback com o refresh
// token na QUERY (o fragment # nao chega ao servidor). O nonce casa a volta com
// ESTE pedido; a porta so e nossa porque foi aberta antes de abrir o navegador.
object GoogleAuthFlow {

    private const val TIMEOUT_MS = 120_000L

    // Retorna o refresh token capturado (o AuthRepository troca por uma sessao).
    suspend fun captureRefreshToken(): Result<String> = withContext(Dispatchers.IO) {
        val nonce = randomNonce()
        val deferred = CompletableDeferred<Result<String>>()

        val server = runCatching { HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0) }
            .getOrElse { return@withContext Result.failure(Exception("Nao consegui abrir a porta local")) }
        val port = server.address.port

        server.createContext("/callback") { ex ->
            val params = parseQuery(ex.requestURI.rawQuery)
            val body: String
            if (params["nonce"] != nonce) {
                complete(deferred, Result.failure(Exception("Falha de seguranca (nonce)")))
                body = page("Nao foi possivel confirmar o login. Tente de novo.")
            } else if (params["refresh"] != null) {
                complete(deferred, Result.success(params["refresh"]!!))
                body = page("Login concluido! Pode fechar esta aba e voltar ao Astra.")
            } else if (params["error"] == "google_email_unregistered") {
                complete(deferred, Result.failure(Exception("Esse Google ainda nao tem conta no Astra — crie uma conta primeiro.")))
                body = page("Esse Google ainda nao tem conta no Astra.")
            } else {
                complete(deferred, Result.failure(Exception("Nao deu pra entrar com o Google")))
                body = page("Nao deu pra entrar com o Google.")
            }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            runCatching {
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }
        server.start()

        val opened = runCatching {
            val url = "${AstraShared.BASE_URL}api/auth/google?platform=desktop&port=$port&nonce=$nonce"
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url)); true
            } else false
        }.getOrDefault(false)

        if (!opened) {
            runCatching { server.stop(0) }
            return@withContext Result.failure(Exception("Nao consegui abrir o navegador"))
        }

        val out = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
            ?: Result.failure(Exception("Tempo esgotado — tente de novo"))
        runCatching { server.stop(0) }
        out
    }

    private fun complete(d: CompletableDeferred<Result<String>>, r: Result<String>) {
        if (!d.isCompleted) d.complete(r)
    }

    // Nonce alfanumerico (bate com o regex do backend: [A-Za-z0-9]{8,64}).
    private fun randomNonce(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        return buildString { repeat(24) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val k = runCatching { URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
            val v = runCatching { URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
            k to v
        }.toMap()
    }

    private fun page(msg: String): String = """
        <!doctype html><html><head><meta charset="utf-8"><title>Astra</title>
        <style>body{background:#06060e;color:#c9a96e;font-family:system-ui,-apple-system,sans-serif;
        display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
        div{text-align:center}h1{font-weight:300;letter-spacing:3px;margin:0 0 12px}
        p{color:#8c8c94;font-size:14px}</style></head>
        <body><div><h1>&#10022; Astra</h1><p>$msg</p></div></body></html>
    """.trimIndent()
}
