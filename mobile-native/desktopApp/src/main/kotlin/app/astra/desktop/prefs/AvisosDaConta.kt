package app.astra.desktop.prefs

import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.dto.AvisosDaContaDto
import app.astra.mobile.core.network.dto.AvisosDaContaRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

// OS AVISOS QUE PERTENCEM À CONTA, não a este computador.
//
// Por que isto existe como objeto único em vez de viver dentro da tela de
// configurações: o horário de descanso e o "não perturbe" precisam ser consultados
// no instante em que um sussurro chega — e nessa hora a tela de configurações não
// está aberta. Guardar num `single` do Koin é o que permite o balão da bandeja
// perguntar "posso tocar?" sem depender de nenhuma tela estar composta.
//
// O QUE ESTE ARQUIVO CONSERTA, e é um bug que estava calado: o servidor manda
// `silent: true` no evento `notification` quando você está em não-perturbe ou
// dentro do descanso, e **o desktop nunca leu esse campo**. Resultado: você punha
// o status em "não perturbe" e o balão do Windows pulava do mesmo jeito, às três
// da manhã inclusive. O recurso existia inteiro no servidor e não tinha ninguém
// escutando do lado de cá.
//
// Só que o `silent` viaja no evento ERRADO pra resolver isso: o balão da bandeja
// nasce de `new_dm`/`channel_activity`, não de `notification`. Esperar o
// `notification` pra decidir se o balão toca criaria dependência de ordem entre
// dois eventos que o servidor emite em caminhos diferentes. Por isso a decisão é
// LOCAL: a regra do descanso é reescrita aqui e comparada com o relógio da
// máquina.
class AvisosDaConta(private val api: NotificationApi) {

    private val _estado = MutableStateFlow(AvisosDaContaDto())
    val estado: StateFlow<AvisosDaContaDto> = _estado.asStateFlow()

    suspend fun carregar() {
        runCatching { api.avisosDaConta().data?.prefs }
            .getOrNull()
            ?.let { _estado.value = it }
    }

    // Devolve o estado que o servidor confirmou, e não o que se pediu: se o
    // servidor recusar um campo, a tela mostra a verdade em vez do palpite.
    suspend fun salvar(novo: AvisosDaContaDto): Result<Unit> = runCatching {
        val resp = api.salvarAvisosDaConta(AvisosDaContaRequest.de(novo))
        _estado.value = resp.data?.prefs ?: novo
    }

    // ESPELHO EXATO do `isInQuietHours` em apps/api/src/lib/notifications.ts.
    // Divergir aqui produz o pior tipo de defeito: o servidor cala o push e o
    // desktop continua tocando, ou o contrário — e nos dois casos a pessoa vê o
    // app desobedecer uma configuração que ela mesma ligou.
    //
    // O caso que atravessa a meia-noite (23h → 7h) é o NORMAL, não a exceção: é a
    // madrugada, que é justamente o que alguém quer calar. Por isso o `s > e` não
    // é tratado como entrada inválida.
    fun emDescanso(agora: LocalTime = LocalTime.now()): Boolean {
        val s = _estado.value.quietStart ?: return false
        val e = _estado.value.quietEnd ?: return false
        val h = agora.hour
        return if (s < e) h in s until e else h >= s || h < e
    }

    // A pergunta única que o caminho do balão faz. `status` é o meu status
    // publicado (ONLINE/IDLE/DND/INVISIBLE) — o servidor usa exatamente ele.
    fun devoCalar(status: String?): Boolean = status == "DND" || emDescanso()
}
