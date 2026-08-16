package app.astra.desktop

import app.astra.desktop.prefs.DesktopPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MODO TRANSMISSÃO.
//
// Ligado, o Astra para de colocar coisa sua na tela enquanto ela está sendo vista
// por outras pessoas:
//
//   1. o aviso da bandeja perde nome e texto (só o tipo);
//   2. o som de aviso não toca — som entra no áudio da transmissão igual;
//   3. o e-mail some da aba Conta.
//
// Os três são o mesmo problema: coisas que aparecem POR CIMA do que está sendo
// gravado, ou dentro dele, sem você ter mandado aparecer naquele instante.
//
// ---------------------------------------------------------------------------
// A DETECÇÃO É OPCIONAL E OPT-IN, e por um motivo que vale escrever: para saber
// que o OBS está aberto é preciso olhar a LISTA DE PROCESSOS da máquina — uma
// leitura mais ampla que a do "o que estou usando", que só olha a janela da
// frente. Então ela só acontece se você pedir, e o que se faz com ela é
// estritamente comparar nomes de executável com a lista fixa abaixo.
//
// Nada disso sai da máquina: o resultado é um booleano que nem o servidor vê. E
// vale a regra de sempre do Astra — TÍTULO DE JANELA NÃO É LIDO, nem aqui.
// ---------------------------------------------------------------------------
object ModoTransmissao {

    // Nome de executável, minúsculo. Lista curta de propósito: cada nome aqui é um
    // programa que a pessoa abre para transmitir, e não um que só *pode* gravar.
    // Incluir gravador genérico faria o modo ligar sozinho no meio de um dia
    // comum, que é o jeito mais rápido de alguém desligar o recurso para sempre.
    private val PROGRAMAS = setOf(
        "obs64.exe", "obs32.exe", "obs.exe",
        "streamlabs obs.exe", "streamlabs desktop.exe",
        "xsplit.core.exe", "xsplit.broadcaster.exe",
        "twitchstudio.exe",
    )

    private const val INTERVALO_MS = 12_000L

    private val _ativo = MutableStateFlow(false)

    /** Resposta única: ligado à mão OU detectado, quando a detecção está ligada. */
    val ativo = _ativo.asStateFlow()

    private val _detectado = MutableStateFlow(false)
    val detectado = _detectado.asStateFlow()

    fun vigiar(scope: CoroutineScope, prefs: DesktopPrefs) {
        // Um laço para o estado e outro para a varredura. Separados porque o
        // primeiro precisa reagir NA HORA ao interruptor (ligar o modo à mão não
        // pode esperar os 12s da próxima varredura), e o segundo é lento de
        // propósito.
        scope.launch {
            prefs.state.collect { p ->
                if (!p.modoTransmissaoAuto) _detectado.value = false
                _ativo.value = p.modoTransmissao || (p.modoTransmissaoAuto && _detectado.value)
            }
        }
        scope.launch {
            while (isActive) {
                if (prefs.state.value.modoTransmissaoAuto) {
                    val achou = withContext(Dispatchers.IO) { algumProgramaAberto() }
                    if (_detectado.value != achou) {
                        _detectado.value = achou
                        val p = prefs.state.value
                        _ativo.value = p.modoTransmissao || (p.modoTransmissaoAuto && achou)
                    }
                }
                delay(INTERVALO_MS)
            }
        }
    }

    // ProcessHandle e não JNA: a JVM já expõe a lista, e o que se lê aqui é só o
    // caminho do executável. Falha (permissão, processo que morreu no meio da
    // volta) vira `false` — a detecção é uma conveniência, e conveniência não
    // derruba nada quando não funciona.
    private fun algumProgramaAberto(): Boolean = runCatching {
        ProcessHandle.allProcesses().anyMatch { p ->
            val nome = p.info().command().orElse(null)
                ?.substringAfterLast('\\')
                ?.substringAfterLast('/')
                ?.lowercase()
            nome != null && nome in PROGRAMAS
        }
    }.getOrDefault(false)
}
