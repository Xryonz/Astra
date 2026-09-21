package app.astra.mobile.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.astra.mobile.ui.LocalAppPrefs

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalCena = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalPalco = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

object Viagem {
    fun fotoDoSussurro(conversa: String) = "foto-sussurro-$conversa"
    fun nomeDoSussurro(conversa: String) = "nome-sussurro-$conversa"
    fun nomeDoCanal(canal: String) = "nome-canal-$canal"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.viajante(chave: String, ehTexto: Boolean = false): Modifier {
    val cena = LocalCena.current ?: return this
    val palco = LocalPalco.current ?: return this
    val prefs = LocalAppPrefs.current
    if (prefs.reduceMotion || !prefs.transitionsOn) return this
    return with(cena) {
        val estado = rememberSharedContentState(chave)
        if (ehTexto) {
            this@viajante.sharedBounds(
                sharedContentState = estado,
                animatedVisibilityScope = palco,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            )
        } else {
            this@viajante.sharedElement(sharedContentState = estado, animatedVisibilityScope = palco)
        }
    }
}
