package main

// RECEBER A TELA DE OUTRA PESSOA — de pacote RTP a quadro pronto para desenhar.
//
// O CAMINHO INTEIRO, e cada etapa existe por um motivo:
//
//	pacotes RTP  ->  remontador  ->  H.264 de um quadro  ->  descompressor  ->  NV12
//
// O REMONTADOR NÃO É LUXO. Um quadro-chave de 720p tem uns 60 KB e o caminho da rede
// aceita ~1200 bytes por pacote: são cinquenta pacotes para UM quadro. Eles chegam fora
// de ordem, um pode se perder, e entregar isso ao descompressor na ordem em que chega
// produz lixo. O `samplebuilder` do pion junta pelos números de sequência e só solta
// quando o quadro está completo — e descarta o que ficou incompleto, que é o
// comportamento certo: meio quadro não é meio de imagem, é imagem quebrada.
//
// UMA THREAD PRESA POR PESSOA, e é exigência de COM. O descompressor é um objeto do
// Media Foundation, criado nesta goroutine, e usá-lo de outra não dá erro claro: dá
// comportamento indefinido. Como cada pessoa tem o próprio descompressor (é obrigatório
// — o decodificador guarda estado entre quadros, igual ao Opus), cada uma tem a própria
// thread. Custa uma thread por pessoa transmitindo, que é o preço de assistir.
//
// O CUSTO MEDIDO de decodificar 720p30 é 1,03 ms por quadro — 3,1% de um núcleo (ver
// `TestVoltaCompletaDaTela`). Três pessoas transmitindo ao mesmo tempo custam ~9%, o
// que cabe folgado até na máquina fraca que o modo econômico atende.

import (
	"fmt"
	"os"
	"runtime"
	"time"

	"github.com/pion/rtp/codecs"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media/samplebuilder"
)

// Quantos pacotes o remontador pode segurar esperando os que faltam.
//
// ESTE NÚMERO JÁ ESTEVE EM 50 E QUEBRAVA A TRANSMISSÃO INTEIRA — de um jeito que parecia
// qualquer outra coisa. Vale contar, porque o sintoma não aponta para cá.
//
// O raciocínio errado era: "um quadro-chave ocupa uns cinquenta pacotes, então cinquenta
// bastam". Mas o remontador não guarda UM quadro: ele guarda uma JANELA de números de
// sequência, e joga fora o mais antigo quando ela estoura. Com a janela do tamanho exato
// de um quadro-chave, o primeiro pacote dele já tinha sido despejado quando o último
// chegava — e o quadro-chave era o único que NUNCA se completava.
//
// O que se via: os quadros normais (poucos pacotes) passavam todos, quinze por segundo,
// e mesmo assim a imagem nunca abria. Sem erro em lugar nenhum, porque nada tinha
// falhado — só o quadro que ANCORA a imagem é que não chegava. Um decodificador de
// H.264 não abre nada sem ele: os outros quadros descrevem a DIFERENÇA em relação ao
// anterior, e sem ponto de partida não há o que diferenciar.
//
// MEDIDO, e não deduzido: com 50, três de seis execuções do teste de ponta a ponta
// ficavam trinta segundos sem receber nada; com 512, oito de oito passam em ~3s.
//
// Quinhentos e doze é folgado de propósito: cabe um quadro-chave de 1080p (~150 pacotes)
// com espaço para os que chegam enquanto ele se completa. O custo é uma lista de
// ponteiros — nada perto de uma transmissão que não abre.
const pacotesQueEsperam = 512

// receberTela lê a faixa de vídeo desta pessoa e entrega os quadros ao Astra.
//
// A goroutine morre sozinha quando `ReadRTP` devolve erro, o que acontece quando o par
// fecha — mesma regra da faixa de voz, e por isso também não precisa de contexto.
func (p *Par) receberTela(remota *webrtc.TrackRemote) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		fmt.Fprintf(os.Stderr, "COM para a tela de %s: %v\n", p.id, err)
		return
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		fmt.Fprintf(os.Stderr, "Media Foundation para a tela de %s: %v\n", p.id, err)
		return
	}
	defer fecharMF()

	// O DESCOMPRESSOR NASCE AQUI E NÃO NA ABERTURA DO PAR, porque abrir um custa
	// procurar e amarrar um objeto do Windows — trabalho que não faz sentido pagar por
	// quem entrou na call para conversar e nunca vai receber tela nenhuma. Esta
	// goroutine só existe quando uma faixa de vídeo chegou de verdade.
	//
	// O tamanho é palpite; o de verdade vem no fluxo (ver `Descompressor`).
	d, err := AbrirDescompressor(1280, 720)
	if err != nil {
		fmt.Fprintf(os.Stderr, "sem descompressor para a tela de %s: %v\n", p.id, err)
		p.saida.Manda(Evento{Ev: EvErro, Par: p.id, Msg: "não consigo mostrar a tela: " + err.Error()})
		return
	}
	defer d.Fechar()

	remontador := samplebuilder.New(pacotesQueEsperam, &codecs.H264Packet{}, 90000)

	p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: d.Nome})
	defer p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "0"})

	comeco := time.Now()
	var quadros, pacotes, amostras int
	relatorio := time.Now()

	for {
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			return
		}
		pacotes++
		remontador.Push(pacote)

		for {
			amostra := remontador.Pop()
			if amostra == nil {
				break
			}
			amostras++
			// ERRO DE UM QUADRO NÃO DERRUBA A FAIXA. Perda de pacote produz quadro
			// quebrado, e o decodificador reclama dele — derrubar por isso trocaria um
			// engasgo na imagem pela tela do outro sumindo para sempre.
			err := d.Decodificar(amostra.Data, time.Since(comeco), func(q Quadro) {
				quadros++
				p.entrega.Mandar(p.id, q)
			})
			if err != nil {
				fmt.Fprintf(os.Stderr, "quadro estragado de %s: %v\n", p.id, err)
			}
		}

		// O RELATÓRIO CONTA AS TRÊS ETAPAS, e não só a última.
		//
		// "0 fps" sozinho não diz nada: pode ser rede que não chega, remontador que
		// nunca fecha um quadro, ou descompressor que recusa tudo. São três defeitos
		// diferentes com o mesmo sintoma, e separá-los aqui é o que transformou uma
		// caçada em uma leitura — foi este contador que apontou o remontador acima.
		if desde := time.Since(relatorio); desde >= time.Second {
			p.saida.Manda(Evento{
				Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "ritmo",
				Msg: fmt.Sprintf("%d fps · %d pacotes · %d remontados",
					int(float64(quadros)/desde.Seconds()), pacotes, amostras),
			})
			relatorio = time.Now()
			quadros, pacotes, amostras = 0, 0, 0
		}
	}
}
