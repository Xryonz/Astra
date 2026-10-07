package app.astra.mobile.core.upload

import android.net.Uri
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.toModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AndamentoDoVideo(val fracao: Float, val posicao: Int, val total: Int) {
    val enviando: Boolean get() = fracao >= 1f
}

class FilaDeVideos(
    private val escopo: CoroutineScope,
    private val preparador: PreparadorDeVideo,
    private val uploader: ImageUploader,
    private val aoAndar: (AndamentoDoVideo?) -> Unit,
    private val aoAnexar: (List<Attachment>) -> Unit,
    private val aoFalhar: (String?) -> Unit,
) {
    private val espera = ArrayDeque<Uri>()
    private var trabalho: Job? = null
    private var atual: Job? = null
    private var posicaoAtual = 0

    fun adicionar(uris: List<Uri>) {
        espera.addAll(uris)
        if (trabalho?.isActive == true) return
        trabalho = escopo.launch {
            var posicao = 0
            try {
                while (true) {
                    val uri = espera.removeFirstOrNull() ?: break
                    posicao++
                    val esta = posicao
                    posicaoAtual = esta
                    atual = launch { prepararUm(uri, esta) }
                    atual?.join()
                }
            } finally {
                atual = null
                aoAndar(null)
            }
        }
    }

    fun cancelar(posicao: Int) {
        if (posicao == posicaoAtual) atual?.cancel()
    }

    private suspend fun prepararUm(uri: Uri, posicao: Int) {
        fun andar(fracao: Float) = aoAndar(AndamentoDoVideo(fracao, posicao, posicao + espera.size))
        andar(0f)
        when (val preparo = preparador.preparar(uri) { andar(it.coerceAtMost(0.99f)) }) {
            is PreparoDoVideo.Recusado -> aoFalhar(preparo.motivo)
            is PreparoDoVideo.Pronto -> {
                andar(1f)
                uploader.uploadMany(listOf(preparo.arquivo))
                    .onSuccess { dtos -> aoAnexar(dtos.map { preparo.completar(it.toModel()) }) }
                    .onFailure { e -> aoFalhar(e.message) }
            }
        }
    }
}
