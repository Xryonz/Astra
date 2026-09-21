package app.astra.mobile.core.update

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.voice.CallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

sealed interface AvisoDeAtualizacao {
    data object PedirPermissao : AvisoDeAtualizacao
    data class Pronta(val versao: String) : AvisoDeAtualizacao
    data class Falhou(val versao: String, val pagina: String) : AvisoDeAtualizacao
}

sealed interface EstadoDaVersao {
    data class EmDia(val conferidoEm: Long) : EstadoDaVersao
    data object SemConexao : EstadoDaVersao
    data class Baixando(val versao: String, val progresso: Float) : EstadoDaVersao
    data class Pronta(val versao: String) : EstadoDaVersao
    data class Interrompida(val versao: String, val pagina: String) : EstadoDaVersao
}

object CuidadorDeAtualizacao {

    private const val UMA_HORA = 60 * 60 * 1000L
    private const val VINTE_MINUTOS = 20 * 60 * 1000L
    private const val UM_MINUTO = 60 * 1000L
    private const val LIMITE_DE_FALHAS = 2

    private const val ULTIMA_CONSULTA = "ultima_consulta"
    private const val ADIADO_EM = "adiado_em"
    private const val PENDENTE_VERSAO = "pendente_versao"
    private const val PENDENTE_ENDERECO = "pendente_endereco"
    private const val PENDENTE_PAGINA = "pendente_pagina"
    private const val PRECISA_DE_TOQUE = "precisa_de_toque"
    private const val FALHA_VERSAO = "falha_versao"
    private const val FALHAS = "falhas"

    @Volatile
    var visivel = false
        private set

    @Volatile
    private var falhaDeRede = false

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()
    private val baixando = Mutex()
    private val _aviso = MutableStateFlow<AvisoDeAtualizacao?>(null)
    val aviso: StateFlow<AvisoDeAtualizacao?> = _aviso.asStateFlow()
    private val _estado = MutableStateFlow<EstadoDaVersao>(EstadoDaVersao.EmDia(0L))
    val estado: StateFlow<EstadoDaVersao> = _estado.asStateFlow()

    fun aoVoltarAoApp(contexto: Context) {
        val app = contexto.applicationContext
        visivel = true
        TrabalhoDeInstalar.cancelar(app)
        escopo.launch { cuidar(app) }
    }

    suspend fun procurarAgora(contexto: Context) {
        val app = contexto.applicationContext
        escopo.launch {
            trava.withLock { consultar(memoria(app), UM_MINUTO) }
            atualizarEstado(app)
        }.join()
        escopo.launch { baixarSePendente(app) }
    }

    fun aoSairDoApp(contexto: Context) {
        visivel = false
        agendarSePronta(contexto.applicationContext)
    }

    fun adiar(contexto: Context) {
        memoria(contexto).edit { putLong(ADIADO_EM, System.currentTimeMillis()) }
        _aviso.value = null
    }

    fun instalarAgora(contexto: Context) {
        val app = contexto.applicationContext
        escopo.launch {
            val m = memoria(app)
            val versao = pendente(m) ?: return@launch
            instalar(app, m, versao)
        }
    }

    fun aoTerminarChamada(contexto: Context) {
        if (!visivel) agendarSePronta(contexto.applicationContext)
    }

    fun instalarSeForHora(contexto: Context) {
        if (visivel || CallService.emAndamento) return
        val m = memoria(contexto)
        val versao = pendente(m) ?: return
        if (desistiu(m, versao) || !apk(contexto, versao).exists()) return
        instalar(contexto, m, versao)
    }

    fun pedirToque(contexto: Context) {
        memoria(contexto).edit { putBoolean(PRECISA_DE_TOQUE, true) }
        atualizarTela(contexto)
    }

    fun falhou(contexto: Context, versao: String) {
        registrarFalha(memoria(contexto), versao)
        atualizarTela(contexto)
    }

    private suspend fun cuidar(app: Context) {
        trava.withLock {
            val m = memoria(app)
            faxina(app, m)
            if (System.currentTimeMillis() - m.getLong(ADIADO_EM, 0) >= UMA_HORA) m.edit { remove(ADIADO_EM) }
            consultar(m, VINTE_MINUTOS)
        }
        baixarSePendente(app)
    }

    private fun consultar(m: SharedPreferences, intervalo: Long) {
        val agora = System.currentTimeMillis()
        if (agora - m.getLong(ULTIMA_CONSULTA, 0) < intervalo) return
        runCatching { Atualizador.procurar(BuildConfig.VERSION_NAME) }
            .onSuccess { nova ->
                falhaDeRede = false
                m.edit {
                    putLong(ULTIMA_CONSULTA, agora)
                    if (nova != null) {
                        putString(PENDENTE_VERSAO, nova.versao)
                        putString(PENDENTE_ENDERECO, nova.endereco)
                        putString(PENDENTE_PAGINA, nova.pagina)
                    }
                }
            }
            .onFailure { falhaDeRede = true }
    }

    private fun baixarSePendente(app: Context) {
        if (!baixando.tryLock()) return
        try {
            val m = memoria(app)
            val versao = pendente(m)
            if (versao != null && !desistiu(m, versao) && !apk(app, versao).exists()) {
                val nova = VersaoNova(
                    versao = versao,
                    endereco = m.getString(PENDENTE_ENDERECO, null).orEmpty(),
                    pagina = m.getString(PENDENTE_PAGINA, null).orEmpty(),
                )
                runCatching {
                    Atualizador.baixar(nova, pasta(app)) { _estado.value = EstadoDaVersao.Baixando(versao, it) }
                }
                    .onSuccess { falhaDeRede = false }
                    .onFailure { if (it is PacoteCorrompido) registrarFalha(m, versao) else falhaDeRede = true }
            }
        } finally {
            baixando.unlock()
        }
        atualizarTela(app)
        if (!visivel) agendarSePronta(app)
    }

    private fun faxina(app: Context, m: SharedPreferences) {
        val versao = m.getString(PENDENTE_VERSAO, null)
        if (versao == null || !Atualizador.ehMaisNova(versao, BuildConfig.VERSION_NAME)) {
            m.edit {
                remove(PENDENTE_VERSAO)
                remove(PENDENTE_ENDERECO)
                remove(PENDENTE_PAGINA)
                remove(PRECISA_DE_TOQUE)
                remove(FALHA_VERSAO)
                remove(FALHAS)
            }
            pasta(app).deleteRecursively()
        } else if (desistiu(m, versao)) {
            pasta(app).deleteRecursively()
        } else {
            pasta(app).listFiles()?.filterNot { it.name.startsWith("$versao.") }?.forEach { it.delete() }
        }
    }

    private fun agendarSePronta(app: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val m = memoria(app)
        val versao = pendente(m) ?: return
        if (desistiu(m, versao) || m.getBoolean(PRECISA_DE_TOQUE, false)) return
        if (!apk(app, versao).exists() || !app.packageManager.canRequestPackageInstalls()) return
        TrabalhoDeInstalar.agendar(app)
    }

    private fun instalar(app: Context, m: SharedPreferences, versao: String) {
        try {
            Instalador.instalar(app, apk(app, versao), versao)
        } catch (e: Exception) {
            registrarFalha(m, versao)
            atualizarTela(app)
        }
    }

    private fun atualizarTela(app: Context) {
        atualizarAviso(app)
        atualizarEstado(app)
    }

    private fun atualizarEstado(app: Context) {
        val m = memoria(app)
        val versao = pendente(m)
        _estado.value = when {
            versao != null && desistiu(m, versao) ->
                EstadoDaVersao.Interrompida(versao, m.getString(PENDENTE_PAGINA, null).orEmpty())
            versao != null && apk(app, versao).exists() -> EstadoDaVersao.Pronta(versao)
            falhaDeRede -> EstadoDaVersao.SemConexao
            versao != null -> (_estado.value as? EstadoDaVersao.Baixando)?.takeIf { it.versao == versao }
                ?: EstadoDaVersao.Baixando(versao, 0f)
            else -> EstadoDaVersao.EmDia(m.getLong(ULTIMA_CONSULTA, 0L))
        }
    }

    private fun atualizarAviso(app: Context) {
        val m = memoria(app)
        val versao = pendente(m)
        _aviso.value = when {
            m.contains(ADIADO_EM) -> null
            versao != null && desistiu(m, versao) ->
                AvisoDeAtualizacao.Falhou(versao, m.getString(PENDENTE_PAGINA, null).orEmpty())
            versao != null && apk(app, versao).exists() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || m.getBoolean(PRECISA_DE_TOQUE, false)) ->
                AvisoDeAtualizacao.Pronta(versao)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls() ->
                AvisoDeAtualizacao.PedirPermissao
            else -> null
        }
    }

    private fun pendente(m: SharedPreferences): String? =
        m.getString(PENDENTE_VERSAO, null)?.takeIf { Atualizador.ehMaisNova(it, BuildConfig.VERSION_NAME) }

    private fun registrarFalha(m: SharedPreferences, versao: String) {
        val falhas = if (m.getString(FALHA_VERSAO, null) == versao) m.getInt(FALHAS, 0) + 1 else 1
        m.edit {
            putString(FALHA_VERSAO, versao)
            putInt(FALHAS, falhas)
        }
    }

    private fun desistiu(m: SharedPreferences, versao: String): Boolean =
        m.getString(FALHA_VERSAO, null) == versao && m.getInt(FALHAS, 0) >= LIMITE_DE_FALHAS

    private fun memoria(contexto: Context): SharedPreferences =
        contexto.getSharedPreferences("atualizacao", Context.MODE_PRIVATE)

    private fun pasta(contexto: Context) = File(contexto.cacheDir, "atualizacao")

    private fun apk(contexto: Context, versao: String) = File(pasta(contexto), "$versao.apk")
}
