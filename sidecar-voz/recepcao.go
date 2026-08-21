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
// CINQUENTA PORQUE UM QUADRO-CHAVE OCUPA ISSO. Menos que um quadro inteiro e ele
// desistiria de quadros que estavam chegando bem; muito mais e um pacote perdido faria
// a imagem congelar enquanto ele espera o que nunca vem. É o tamanho de um quadro, não
// de uma fila.
const pacotesQueEsperam = 50

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
	var quadros int
	relatorio := time.Now()

	for {
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			return
		}
		remontador.Push(pacote)

		for {
			amostra := remontador.Pop()
			if amostra == nil {
				break
			}
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

		// O relatório serve para saber que a imagem está CHEGANDO, e a que ritmo, sem
		// depender de alguém estar olhando a tela. Uma vez por segundo.
		if desde := time.Since(relatorio); desde >= time.Second {
			p.saida.Manda(Evento{
				Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "ritmo",
				Msg: fmt.Sprintf("%d fps", int(float64(quadros)/desde.Seconds())),
			})
			relatorio = time.Now()
			quadros = 0
		}
	}
}
