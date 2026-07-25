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

        // /callback recebe o token na QUERY, captura, e REDIRECIONA (302) pra /done.
        // Assim o token some da barra/historico: a aba final para numa URL limpa
        // (/done?s=ok) que so diz o que aconteceu.
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
                    complete(deferred, Result.failure(Exception("Esse Google ainda nao tem conta no Astra — crie uma conta primeiro.")))
                    "unreg"
                }
                else -> {
                    complete(deferred, Result.failure(Exception("Nao deu pra entrar com o Google")))
                    "err"
                }
            }
            redirect(ex, "/done?s=$status")
        }
        // Tela final, sem token na URL: editorial, so a mensagem do que rolou.
        server.createContext("/done") { ex ->
            val s = parseQuery(ex.requestURI.rawQuery)["s"]
            val msg = when (s) {
                "ok" -> "Conectado ao Astra. Pode fechar esta aba e voltar ao app."
                "unreg" -> "Esse Google ainda nao tem conta no Astra — crie uma conta primeiro."
                "nonce" -> "Nao foi possivel confirmar o login. Tente de novo."
                else -> "Nao deu pra entrar com o Google."
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

    // Redireciona (302) sem corpo — usado pra tirar o token da URL final.
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

    // Tela editorial minima (obsidian + ambar). ok = login concluido (mensagem
    // em verde); senao a mensagem sai neutra.
    private fun page(msg: String, ok: Boolean): String {
        val msgColor = if (ok) "#6ec98a" else "#8c8c94"
        return """
            <!doctype html><html lang="pt-br"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Astra</title>
            <style>
            :root{color-scheme:dark}
            *{margin:0;box-sizing:border-box}
            body{background:#06060e;color:#e8e6e3;
            font-family:system-ui,-apple-system,"Segoe UI",sans-serif;
            display:flex;align-items:center;justify-content:center;min-height:100vh}
            .card{text-align:center;padding:44px 52px}
            .glyph{font-size:46px;color:#c9a96e;line-height:1;margin-bottom:22px}
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
