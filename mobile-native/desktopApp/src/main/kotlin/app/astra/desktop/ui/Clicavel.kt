package app.astra.desktop.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable as clicavelDoCompose

private fun Modifier.cursorDeClique(enabled: Boolean): Modifier =
    pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

fun Modifier.clickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .cursorDeClique(enabled)
    .clicavelDoCompose(interactionSource, indication, enabled, onClickLabel, role, onClick)

fun Modifier.cliqueDeFundo(onClick: () -> Unit): Modifier = this then CliqueDeFundo(onClick)

private data class CliqueDeFundo(val onClick: () -> Unit) : ModifierNodeElement<NoDoCliqueDeFundo>() {
    override fun create() = NoDoCliqueDeFundo(onClick)

    override fun update(node: NoDoCliqueDeFundo) {
        node.onClick = onClick
    }
}

private class NoDoCliqueDeFundo(var onClick: () -> Unit) : DelegatingNode() {
    init {
        delegate(SuspendingPointerInputModifierNode { awaitEachGesture { if (cliquePrincipalCompleto()) onClick() } })
    }
}

private suspend fun AwaitPointerEventScope.cliquePrincipalCompleto(): Boolean {
    val toque = awaitFirstDown()
    if (!currentEvent.buttons.isPrimaryPressed) return false
    toque.consume()
    val soltura = waitForUpOrCancellation() ?: return false
    soltura.consume()
    return true
}
