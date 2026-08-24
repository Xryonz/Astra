package app.astra.desktop.net

import app.astra.desktop.CrashLog
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// O QUE DE FATO FALHOU, e nao o que a tela teve coragem de dizer.
//
// A tela diz "o servidor está acordando" pra qualquer falha que possa melhorar
// esperando: tempo esgotado, 5xx, resposta ilegivel, conexao recusada. Do lado de
// quem olha, as quatro sao a mesma frase — e foi assim que uma conversa que nao
// carregava passou por "a hospedagem dormiu" durante dias, sem ninguem poder
// distinguir de um 500 na consulta ou de um campo novo que o app nao sabe ler.
//
// Aqui fica o motivo cru. So classe e mensagem: nada de cabecalho, nada de token,
// nada de corpo de resposta — este arquivo pode ser mandado pra alguem olhar.
object RedeLog {
    private val hora = DateTimeFormatter.ofPattern("HH:mm:ss")
    private const val TETO_BYTES = 128 * 1024

    private val arquivo: File by lazy { File(CrashLog.dataDir(), "rede.txt") }

    fun falhou(oQue: String, tentativa: Int, erro: Throwable) {
        runCatching {
            // Teto simples: passou do limite, recomeca. Um log de rede que cresce sem
            // fim vira um arquivo de dezenas de MB que ninguem abre.
            if (arquivo.length() > TETO_BYTES) arquivo.writeText("")
            val causa = erro.message?.take(160).orEmpty()
            arquivo.appendText(
                "${LocalTime.now().format(hora)}  $oQue  tentativa $tentativa  " +
                    "${erro::class.simpleName}${if (causa.isBlank()) "" else ": $causa"}\n",
            )
        }
    }

    // IMAGEM QUE NAO CARREGOU — avatar, banner, icone, anexo.
    //
    // O app ja sabia disso e guardava so na memoria, para nao repetir a requisicao
    // condenada (ver `urlsMortas` em Bits.kt). Guardar sem registrar resolve o desperdicio
    // e esconde a causa: de fora, "a foto nao aparece" e uma tela sem foto, e nada
    // distingue arquivo que sumiu do servidor de rede que caiu de URL malformada.
    //
    // A URL VAI INTEIRA, e e o ponto: e ela que diz de onde a imagem deveria ter vindo.
    // `/uploads/...` significa arquivo no disco da instancia — que no Render nao sobrevive
    // a um reinicio. Um endereco do bucket significa outra coisa. Sao dois defeitos com o
    // mesmo sintoma, e o prefixo separa os dois sem precisar de mais nada.
    //
    // URL de imagem do proprio app nao carrega segredo: nao ha token nela, o nome do
    // arquivo e aleatorio, e o arquivo e publico por natureza. Este registro continua
    // podendo ser mandado para alguem olhar.
    fun imagemMorreu(url: String) {
        runCatching {
            if (arquivo.length() > TETO_BYTES) arquivo.writeText("")
            arquivo.appendText("${LocalTime.now().format(hora)}  imagem nao carregou  ${url.take(200)}\n")
        }
    }
}
