package app.astra.desktop.prefs

import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.PreferenciasRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val MARCA_DO_TEMA_CONFERIDO = "temaConferido"

class TemaDaConta(
    private val api: UserApi,
    private val prefs: DesktopPrefs,
) {
    private var sacola: JsonObject = JsonObject(emptyMap())
    private var leuDaConta = false

    suspend fun sincronizar() {
        adotar()
        prefs.state
            .map { it.accentId to it.bgId }
            .distinctUntilChanged()
            .drop(1)
            .collect { (accent, bg) -> enviar(accent, bg) }
    }

    private suspend fun adotar() {
        sacola = JsonObject(emptyMap())
        leuDaConta = false
        val resp = runCatching { api.preferencias().data?.preferences }.getOrNull() ?: return
        sacola = resp
        leuDaConta = true
        val accent = resp["accent"]?.jsonPrimitive?.contentOrNull
        val bg = resp["bg"]?.jsonPrimitive?.contentOrNull
        if (accent.isNullOrBlank() || bg.isNullOrBlank() || padraoAntigoNuncaEscolhido(resp, accent, bg)) {
            enviar(prefs.state.value.accentId, prefs.state.value.bgId)
            return
        }
        val atual = prefs.state.value
        if (accent != atual.accentId || bg != atual.bgId) prefs.setTheme(accent, bg)
        if (MARCA_DO_TEMA_CONFERIDO !in resp) enviar(accent, bg)
    }

    private fun padraoAntigoNuncaEscolhido(resp: JsonObject, accent: String, bg: String): Boolean =
        MARCA_DO_TEMA_CONFERIDO !in resp && accent == "white" && bg == "void" && !prefs.temaGravadoNoAparelho()

    private suspend fun enviar(accent: String, bg: String) {
        if (!leuDaConta) {
            sacola = runCatching { api.preferencias().data?.preferences }.getOrNull() ?: return
            leuDaConta = true
        }
        val nova = JsonObject(
            sacola + mapOf(
                "accent" to JsonPrimitive(accent),
                "bg" to JsonPrimitive(bg),
                MARCA_DO_TEMA_CONFERIDO to JsonPrimitive(true),
            ),
        )
        runCatching { api.salvarPreferencias(PreferenciasRequest(nova)) }
            .onSuccess { sacola = nova }
    }
}
