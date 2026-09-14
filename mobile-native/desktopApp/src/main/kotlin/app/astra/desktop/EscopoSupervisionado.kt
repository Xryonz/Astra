package app.astra.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job

class EscopoSupervisionado private constructor(
    private val delegado: CoroutineScope,
) : CoroutineScope by delegado {

    companion object {
        fun sob(pai: CoroutineScope): EscopoSupervisionado = EscopoSupervisionado(
            CoroutineScope(pai.coroutineContext + SupervisorJob(pai.coroutineContext.job)),
        )
    }
}
