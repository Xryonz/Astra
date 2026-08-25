package app.astra.desktop.auth

import app.astra.shared.AstraShared
import com.sun.net.httpserver.HttpExchange
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

object GoogleAuthFlow {

    private const val TIMEOUT_MS = 120_000L

    suspend fun captureRefreshToken(): Result<String> = withContext(Dispatchers.IO) {
        val nonce = randomNonce()
        val deferred = CompletableDeferred<Result<String>>()

        val server = runCatching { HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0) }
            .getOrElse { return@withContext Result.failure(Exception("Não consegui abrir a porta local")) }
        val port = server.address.port

        server.createContext("/callback") { ex ->
            val params = parseQuery(ex.requestURI.rawQuery)
            val status = when {
                params["nonce"] != nonce -> {
                    complete(deferred, Result.failure(Exception("Falha de seguranca (nonce)")))
                    "nonce"
                }
                params["refresh"] != null -> {
                    complete(deferred, Result.success(params["refresh"]!!))
                    "ok"
                }
                params["error"] == "google_email_unregistered" -> {
                    complete(deferred, Result.failure(Exception("Esse Google ainda não tem conta no Astra — crie uma conta primeiro.")))
                    "unreg"
                }
                else -> {
                    complete(deferred, Result.failure(Exception("Não foi possível entrar com o Google")))
                    "err"
                }
            }
            redirect(ex, "/done?s=$status")
        }
        server.createContext("/done") { ex ->
            val s = parseQuery(ex.requestURI.rawQuery)["s"]
            val msg = when (s) {
                "ok" -> "Conectado ao Astra. Pode fechar esta aba e voltar ao app."
                "unreg" -> "Esse Google ainda não tem conta no Astra — crie uma conta primeiro."
                "nonce" -> "Não foi possível confirmar o login. Tente de novo."
                else -> "Não foi possível entrar com o Google."
            }
            serveHtml(ex, page(msg, ok = s == "ok"))
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
            return@withContext Result.failure(Exception("Não consegui abrir o navegador"))
        }

        val out = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
            ?: Result.failure(Exception("Tempo esgotado — tente de novo"))
        Thread {
            try { Thread.sleep(3000); server.stop(2) } catch (_: Exception) { runCatching { server.stop(0) } }
        }.apply { isDaemon = true }.start()
        out
    }

    private fun complete(d: CompletableDeferred<Result<String>>, r: Result<String>) {
        if (!d.isCompleted) d.complete(r)
    }

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

    private fun redirect(ex: HttpExchange, location: String) {
        ex.responseHeaders.add("Location", location)
        runCatching {
            ex.sendResponseHeaders(302, -1)
            ex.responseBody.close()
        }
    }

    private fun serveHtml(ex: HttpExchange, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        runCatching {
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
    }

    private fun page(msg: String, ok: Boolean): String {
        val msgColor = if (ok) "#c0c0c6" else "#8c8c94"
        return """
            <!doctype html><html lang="pt-br"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Astra</title>
            <style>
            :root{color-scheme:dark}
            *{margin:0;box-sizing:border-box}
            body{background:#06060e;color:#e4e4eb;
            font-family:system-ui,-apple-system,"Segoe UI",sans-serif;
            display:flex;align-items:center;justify-content:center;min-height:100vh}
            .card{text-align:center;padding:44px 52px}
            .glyph{font-size:46px;color:#d4d8e0;line-height:1;margin-bottom:22px}
            h1{font-weight:300;letter-spacing:5px;font-size:22px;margin-bottom:14px}
            p{font-size:14px;line-height:1.5;max-width:300px;color:$msgColor}
            </style></head>
            <body><div class="card">
            <div class="glyph">&#10022;</div>
            <h1>ASTRA</h1>
            <p>$msg</p>
            </div></body></html>
        """.trimIndent()
    }
}
