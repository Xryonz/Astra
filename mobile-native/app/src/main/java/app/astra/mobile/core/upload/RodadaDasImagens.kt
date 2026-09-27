package app.astra.mobile.core.upload

import androidx.compose.runtime.mutableIntStateOf

object RodadaDasImagens {
    private val atual = mutableIntStateOf(0)

    val valor: Int get() = atual.intValue

    fun proxima() {
        atual.intValue++
    }
}
