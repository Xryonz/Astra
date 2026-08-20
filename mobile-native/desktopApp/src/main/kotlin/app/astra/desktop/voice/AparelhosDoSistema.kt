package app.astra.desktop.voice

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

// OS APARELHOS DE ÁUDIO, para o Diagnóstico e para a checagem de permissões.
//
// Isto vivia dentro do motor de voz antigo, e a listagem de SAÍDAS era feita pelo
// `AudioDeviceModule` do webrtc-java. Quando a voz migrou para o processo em Go, esse
// motor virou ilha morta e foi removido — e com ele iria a única razão de a biblioteca
// (8 MB de nativo do Windows) continuar no pacote. Reescrever a listagem em JavaSound
// foi o que permitiu a biblioteca sair.
//
// NÃO CONFUNDIR COM A LISTA DA CALL. Quem escolhe microfone e saída durante uma
// conversa é o processo de voz, que enumera pelo WASAPI e devolve id estável além do
// nome (ver `AparelhoDeAudio` em SidecarDeVoz.kt) — é a lista certa para ESCOLHER, e é
// a que as Configurações usam. Esta aqui é para PERGUNTAR "o que existe nesta
// máquina?" fora de uma call, quando o processo de voz nem está rodando: o Diagnóstico
// e o painel de permissões abrem sem conversa nenhuma acontecendo.
//
// O JavaSound cobre esse caso sem custo, e a diferença de qualidade entre as duas
// listas não atrapalha aqui: nome repetido ou driver com apelido esquisito é ruim para
// escolher, e irrelevante para responder "existe microfone instalado?".
object AudioDevices {

    // Mixers que oferecem uma linha de ENTRADA. `targetLine` é entrada na nomenclatura
    // do JavaSound (o alvo é o programa, não o alto-falante), e é uma das trocas de
    // nome mais fáceis de errar nessa API.
    fun inputs(): List<String> = nomesComLinha(TargetDataLine::class.java)

    // E `sourceLine` é SAÍDA, pelo mesmo raciocínio invertido: a fonte do som é o
    // programa e o destino é o alto-falante.
    fun outputs(): List<String> = nomesComLinha(SourceDataLine::class.java)

    // O `runCatching` por mixer, e não em volta do laço inteiro, é deliberado: driver
    // quebrado costuma explodir ao ser consultado, e um só derrubaria a lista toda se
    // a proteção fosse externa. Perder um aparelho é melhor que perder todos.
    private fun nomesComLinha(classe: Class<*>): List<String> = runCatching {
        AudioSystem.getMixerInfo().filter { info ->
            runCatching {
                val mixer = AudioSystem.getMixer(info)
                val linhas = if (classe == TargetDataLine::class.java) mixer.targetLineInfo else mixer.sourceLineInfo
                linhas.any { it.lineClass == classe }
            }.getOrDefault(false)
        }.map { it.name }.distinct()
    }.getOrDefault(emptyList())
}
