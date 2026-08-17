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

// O TEMA SEGUE A PESSOA, e não a máquina.
//
// O site já guardava `{ accent, bg }` em /api/profile/preferences desde sempre; o
// desktop nunca leu nem escreveu. Resultado: escolher Nebulosa no site e abrir o
// Astra dava Obsidiana, e máquina nova nascia no padrão de fábrica mesmo com a
// conta tendo um tema há meses.
//
// A CONTA MANDA AO ENTRAR (escolha do dono). Trocar de tema aqui sobe pra conta;
// entrar adota o que a conta tem. É o mesmo contrato do site, então os dois
// convergem sozinhos em vez de brigar.
//
// O QUE **NÃO** VIAJA, e é decisão e não esquecimento: tamanho de fonte,
// densidade, GPU, teto de FPS, voz. Essas dependem do monitor e da máquina — o
// que é confortável num notebook de 13" é grande demais numa TV, e sincronizar
// pioraria as duas pontas.
class TemaDaConta(
    private val api: UserApi,
    private val prefs: DesktopPrefs,
) {
    // A sacola INTEIRA como veio. O servidor substitui o registro todo a cada
    // PATCH (não faz merge), então reenviar só accent/bg apagaria qualquer chave
    // que o site guarde e que este app não conheça.
    private var sacola: JsonObject = JsonObject(emptyMap())

    // Roda enquanto houver sessão; cai junto com a tela que a lançou.
    suspend fun sincronizar() {
        adotar()
        // A ORDEM AQUI É O QUE FAZ A COISA FUNCIONAR. Só se observa mudança
        // DEPOIS de adotar: um coletor ligado antes veria o tema local de
        // partida, empurraria ele pra conta e apagaria a escolha feita no site —
        // exatamente o contrário do que "a conta manda" quer dizer.
        prefs.state
            .map { it.accentId to it.bgId }
            .distinctUntilChanged()
            // O primeiro valor é o que `adotar` acabou de aplicar. Mandá-lo de
            // volta seria um PATCH que não muda nada, a cada abertura do app.
            .drop(1)
            .collect { (accent, bg) -> enviar(accent, bg) }
    }

    private suspend fun adotar() {
        val resp = runCatching { api.preferencias().data?.preferences }.getOrNull() ?: return
        sacola = resp
        val accent = resp["accent"]?.jsonPrimitive?.contentOrNull
        val bg = resp["bg"]?.jsonPrimitive?.contentOrNull
        if (accent.isNullOrBlank() || bg.isNullOrBlank()) {
            // Conta ainda sem tema (quem só usou o desktop até hoje): SEMEIA com o
            // que está nesta máquina, em vez de deixar a sacola vazia. Sem isto, a
            // segunda máquina continuaria nascendo no padrão de fábrica.
            enviar(prefs.state.value.accentId, prefs.state.value.bgId)
            return
        }
        val atual = prefs.state.value
        if (accent != atual.accentId || bg != atual.bgId) prefs.setTheme(accent, bg)
    }

    private suspend fun enviar(accent: String, bg: String) {
        val nova = JsonObject(
            sacola + mapOf("accent" to JsonPrimitive(accent), "bg" to JsonPrimitive(bg)),
        )
        // Falha de rede é silêncio de propósito: isto é conveniência, não um
        // salvamento que a pessoa pediu. O tema JÁ está aplicado e guardado
        // localmente — um erro na tela por causa da cópia remota seria alarme
        // sobre algo que não quebrou nada.
        runCatching { api.salvarPreferencias(PreferenciasRequest(nova)) }
            .onSuccess { sacola = nova }
    }
}
