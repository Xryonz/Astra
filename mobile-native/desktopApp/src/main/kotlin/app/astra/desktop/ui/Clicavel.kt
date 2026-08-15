package app.astra.desktop.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable as clicavelDoCompose

// O CURSOR DE MÃO EM TUDO QUE CLICA — resolvido na raiz, não em 203 lugares.
//
// O app tinha 203 alvos clicáveis e cursor de mão em SEIS. Num app de janela, a
// seta parada sobre um botão é a interface dizendo "aqui não tem nada": o cursor é
// o único retorno que existe ANTES do clique, e sem ele a pessoa descobre o que é
// clicável tentando.
//
// POR QUE ISTO E NÃO 203 EDIÇÕES: um `.pointerHoverIcon(Hand)` colado em cada
// chamada resolveria hoje e apodreceria amanhã — o próximo botão escrito nasceria
// sem cursor de novo, e ninguém lembraria. Aqui a propriedade passa a valer por
// construção: todo `.clickable` do pacote `ui` ganha o cursor, inclusive os que
// ainda não existem.
//
// COMO FUNCIONA (e por que não é mágica): estas funções têm a MESMA assinatura das
// do Compose e vivem no mesmo pacote dos 39 arquivos de tela. Em Kotlin, um import
// explícito ganha de uma função do próprio pacote — então bastou APAGAR a linha
// `import androidx.compose.foundation.clickable` desses arquivos pra que estas
// aqui passassem a ser as escolhidas. Nenhuma chamada mudou. A original continua
// acessível pelo apelido `clicavelDoCompose`, e é ela que faz o trabalho.
//
// O CUSTO HONESTO: quem abrir uma tela e ler `.clickable {}` não vê, ali, que
// passa por aqui. É o preço de a regra valer sozinha, e ele se paga porque o
// comportamento é o esperado — cursor de mão em botão é o que qualquer um assume.
// Se um dia alguém precisar do Compose puro, `clicavelDoCompose` está exportado.

// Desabilitado NÃO ganha mão. Um cursor de mão sobre um botão que não responde
// promete uma ação que não vai acontecer — mentira pequena, mas é mentira, e a
// pessoa clica duas vezes achando que errou a mira.
private fun Modifier.cursorDeClique(enabled: Boolean): Modifier =
    pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = this
    .cursorDeClique(enabled)
    // CINCO ARGUMENTOS POSICIONAIS, e o `null` do meio não é enfeite: o Compose
    // 1.11 tem TRÊS `clickable`, e as duas primeiras (4 e 5 parâmetros) ficam
    // ambíguas quando a fonte de interação é omitida. Passar ela explicitamente
    // deixa só uma candidata com essa aridade. Nulo aqui quer dizer "cria a sua",
    // que é exatamente o que a de 4 parâmetros fazia — a indicação continua vindo
    // do LocalIndication, então o brilho de toque não muda em lugar nenhum.
    .clicavelDoCompose(enabled, onClickLabel, role, null, onClick)

fun Modifier.clickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = this
    .cursorDeClique(enabled)
    .clicavelDoCompose(interactionSource, indication, enabled, onClickLabel, role, onClick)

// SUPERFÍCIE QUE FECHA, e não botão: o fundo escurecido atrás de um modal.
//
// Ele é clicável de verdade (clicar fecha), mas não é um alvo — é a tela inteira.
// Mão sobre a janela toda transformaria "não estou apontando nada" em "tudo aqui é
// botão", que é o oposto do que o cursor serve pra dizer. A seta continua sendo a
// resposta certa; quem quiser fechar acha o X ou aperta Esc.
// Aplicado DEPOIS do `.clickable`, porque o último da corrente é o mais interno —
// e o mais interno ganha. Uma palavra por lugar, sem import: estes arquivos já
// estão neste pacote.
fun Modifier.semCursorDeClique(): Modifier = pointerHoverIcon(PointerIcon.Default)
