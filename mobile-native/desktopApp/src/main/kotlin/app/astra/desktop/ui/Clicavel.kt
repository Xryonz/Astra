package app.astra.desktop.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable as clicavelDoCompose

private fun Modifier.cursorDeClique(enabled: Boolean): Modifier =
    pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = this
    .cursorDeClique(enabled)
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

fun Modifier.semCursorDeClique(): Modifier = pointerHoverIcon(PointerIcon.Default)
