package app.astra.mobile.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.realtime.SocketManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SonsDaConstelacao @Inject constructor(
    @ApplicationContext private val contexto: Context,
    socketManager: SocketManager,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tocando = ArrayDeque<MediaPlayer>()

    init {
        escopo.launch { socketManager.soundboardPlay.collect { tocar(it) } }
    }

    fun tocar(url: String) {
        if (url.isBlank()) return
        val endereco = if (url.startsWith("http")) url else BuildConfig.BASE_URL.trimEnd('/') + url
        runCatching {
            val tocador = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(endereco)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { soltar(it) }
                setOnErrorListener { tocador, _, _ -> soltar(tocador); true }
                prepareAsync()
            }
            if (tocando.size >= QUANTOS_DE_UMA_VEZ) soltar(tocando.first())
            tocando.addLast(tocador)
        }.onFailure { Log.w(TAG, "som não tocou: ${it.message}") }
    }

    private fun soltar(tocador: MediaPlayer) {
        tocando.remove(tocador)
        runCatching { tocador.release() }
    }

    private companion object {
        const val TAG = "SonsDaConstelacao"
        const val QUANTOS_DE_UMA_VEZ = 4
    }
}
