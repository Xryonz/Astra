package app.astra.desktop.voice

import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

// Converte o arquivo que a pessoa escolheu para WAV, no momento de subir um som da
// soundboard. Roda UMA vez por som cadastrado — a reprodução depois só baixa o WAV
// pronto e toca com o JavaSound.
//
// ISTO CUSTAVA 137,8 MB.
//
// Antes a conversão era um `ffmpeg.exe` completo, empacotado dentro do Astra. Ele
// tinha entrado para capturar tela; quando a transmissão saiu, sobrou como o único
// binário de 137,8 MB do instalador — **quase metade do app** — para atender a uma
// ação que um administrador faz de vez em quando. E cada atualização automática
// baixava ele de novo, para todo mundo.
//
// Hoje quem decodifica são dois provedores do JavaSound (~300 KB somados). Eles não
// têm API para chamar: registram-se sozinhos no `AudioSystem`, e a partir daí abrir
// um MP3 é igual a abrir um WAV. É por isso que não há nenhum import deles aqui.
//
// POR QUE CONVERTER, se o pedido foi "não perder qualidade": decodificar um MP3 é
// uma operação EXATA — o WAV guarda exatamente o que saiu do decodificador, amostra
// por amostra. Perda só existe ao RE-ENCODAR (MP3 → MP3), e não é o que acontece
// aqui. O arquivo fica maior, só isso.
object ConversorDeSom {

    // TETO DE DURAÇÃO, e ele não é preciosismo.
    //
    // O áudio decodificado vai inteiro para a memória antes de virar arquivo, porque
    // escrever WAV exige saber a contagem de quadros de antemão e um MP3 não a
    // declara. Sem teto, escolher um álbum de uma hora por engano viraria ~600 MB de
    // PCM num heap com teto de 1 GB — ou seja, o app fechando na cara da pessoa.
    //
    // Cinco minutos é generoso para soundboard (o uso real é de dois a dez segundos)
    // e ainda deixa o pior caso em ~50 MB.
    private const val SEGUNDOS_MAXIMOS = 300

    // Já é WAV? Devolve o próprio arquivo — reconverter não acrescentaria nada.
    fun paraWav(entrada: File): File? {
        if (entrada.extension.equals("wav", ignoreCase = true)) return entrada

        return runCatching {
            AudioSystem.getAudioInputStream(entrada).use { origem ->
                // PCM de 16 bits: é o que placa de som toca sem conversão extra, e o
                // que o JavaSound abre sem depender de codec do sistema.
                //
                // O formato de origem de um MP3 vem com taxa de quadros
                // desconhecida; a taxa de AMOSTRAGEM é o campo confiável, e é dela
                // que o formato de destino é montado.
                val canais = origem.format.channels.coerceAtLeast(1)
                val taxa = origem.format.sampleRate
                val alvo = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    taxa,
                    16,
                    canais,
                    canais * 2,
                    taxa,
                    false, // little-endian, como manda o WAV
                )

                AudioSystem.getAudioInputStream(alvo, origem).use { pcm ->
                    val tetoDeBytes = (taxa * canais * 2 * SEGUNDOS_MAXIMOS).toLong()
                    val bruto = pcm.readNBytes((tetoDeBytes + 1).toInt())
                    if (bruto.size > tetoDeBytes) {
                        error("som acima de ${SEGUNDOS_MAXIMOS / 60} minutos")
                    }

                    val quadros = bruto.size.toLong() / alvo.frameSize
                    if (quadros <= 0) error("arquivo sem áudio")

                    val saida = File.createTempFile("astra-som-", ".wav")
                    // A contagem de quadros é passada explícita: sem ela o escritor
                    // de WAV grava um cabeçalho com tamanho desconhecido, e o
                    // arquivo abre mudo em metade dos tocadores.
                    AudioInputStream(bruto.inputStream(), alvo, quadros).use {
                        AudioSystem.write(it, AudioFileFormat.Type.WAVE, saida)
                    }
                    saida
                }
            }
        }.getOrNull()
    }

    // Duracao lida UMA vez, no cadastro. Depois disso ela viaja no DTO — abrir o
    // arquivo do bucket so pra saber quanto dura seria uma requisicao por som toda
    // vez que alguem abre o painel.
    fun duracaoMs(wav: File): Int = runCatching {
        AudioSystem.getAudioInputStream(wav).use { s ->
            val quadros = s.frameLength
            val taxa = s.format.frameRate
            if (quadros <= 0 || taxa <= 0f) 0 else (quadros / taxa * 1000f).toInt()
        }
    }.getOrDefault(0)
}
