package app.astra.desktop.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// "Estou transmitindo agora?" — sinal global, e o global aqui e proposital.
//
// O CEU (aurora + estrelas) mora no Main, acima de tudo; a sessao de voz nasce dentro do
// ShellScreen. Nao ha caminho de composicao de um pro outro, e criar um so pra descer um
// booleano custaria mais do que um objeto de uma linha.
//
// PRA QUE: transmitindo, o orcamento da placa pertence a captura e ao compressor. Num
// notebook hibrido a MESMA placa integrada desenha a tela, captura os quadros e ainda
// comprime — e a aurora e um shader de tela cheia rodando na taxa do monitor. Enquanto a
// transmissao esta no ar ela sai da frente. Quem esta assistindo esta olhando o conteudo
// compartilhado, nao o fundo do Astra.
object Transmitindo {
    private val _ativo = MutableStateFlow(false)
    val ativo: StateFlow<Boolean> = _ativo
    fun marcar(v: Boolean) { _ativo.value = v }
}
