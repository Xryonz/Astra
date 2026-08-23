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

	"github.com/pion/rtcp"
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
// SÓ DECODIFICA A TELA QUE ESTÁ NO PALCO, e essa é a diferença mais importante entre
// este laço e o que ele era. A leitura da rede continua acontecendo sempre — não é
// opcional, um `TrackRemote` sem leitor entope o buffer do pion enquanto a pessoa
// transmite —, mas o pacote de quem ninguém está olhando morre aqui mesmo, sem passar
// pelo remontador nem pelo descompressor.
//
// O QUE ISSO POUPA, em números medidos: 1,03 ms por quadro decodificado (ver
// `TestVoltaCompletaDaTela`). Com três pessoas transmitindo e uma no palco, a máquina
// deixa de pagar 2 ms a cada 33 ms — 6% de um núcleo que não estava comprando imagem
// nenhuma. E o caso que mais pesa nem tem palco: sair da sala de voz para uma conversa
// de texto sem largar a chamada desmonta a `VoiceView` inteira, e aí ninguém está
// olhando NADA enquanto a call continua.
//
// É a mesma regra que o Discord aplica ("will only relay video to a participant on the
// call if they are watching it"), com a diferença de que lá ela vale na origem, porque
// há um servidor de mídia no meio. Aqui a malha entrega a todos e o corte é no destino:
// economiza CPU, não banda. Cortar banda também exigiria avisar quem transmite, o que é
// outra fatia.
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

	// O AVISO DE "HÁ TELA CHEGANDO" É DA FAIXA, NÃO DO DESCOMPRESSOR — e essa separação
	// virou obrigatória agora.
	//
	// É este evento que acende o distintivo de "transmitindo" na faixa de participantes,
	// e é o distintivo que a pessoa clica para pôr aquela tela no palco. Se ele fosse
	// junto do descompressor, sair do palco apagaria o distintivo, e a tela que ninguém
	// está vendo viraria uma tela que ninguém CONSEGUE ver — o beco sem saída em que o
	// próprio remédio tranca a porta.
	p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "faixa"})
	defer p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "0"})

	// "PERDI A IMAGEM, MANDA UM QUADRO-CHAVE."
	//
	// Sem este pedido, entrar numa sala onde alguém JÁ está transmitindo significa
	// esperar o próximo quadro-chave natural — medido no compressor do outro lado, até
	// cinco segundos de vazio, e ele não aceita encurtar esse intervalo. O mesmo vale
	// depois de qualquer oscilação de rede que engula um quadro-chave, e agora também
	// toda vez que esta tela sobe ao palco: por definição não há em que ancorar a
	// imagem, porque os quadros de diferença dos últimos minutos foram descartados.
	pedirImagem := func() {
		err := p.pc.WriteRTCP([]rtcp.Packet{
			&rtcp.PictureLossIndication{MediaSSRC: uint32(remota.SSRC())},
		})
		if err != nil {
			fmt.Fprintf(os.Stderr, "não consegui pedir imagem a %s: %v\n", p.id, err)
		}
	}

	// O DESCOMPRESSOR NASCE QUANDO ALGUÉM OLHA, e morre quando param de olhar. Abrir um
	// custa procurar e amarrar um objeto do Windows, e ele segura vários megabytes de
	// buffer interno enquanto vive — pagar isso por uma tela fora do palco é gastar
	// memória para produzir pixel que vai direto para o lixo.
	var d *Descompressor
	var remontador *samplebuilder.SampleBuilder
	defer func() {
		if d != nil {
			d.Fechar()
		}
	}()

	// DESISTIU marca "esta máquina não tem descompressor de H.264".
	//
	// Sem ela, a falha viraria uma tentativa de abrir por PACOTE — milhares por segundo,
	// cada uma varrendo o registro de objetos do Windows. Zera quando a tela sai do
	// palco: se a pessoa insistir, tenta de novo, uma vez.
	desistiu := false

	// SEPARADA DO CONTADOR DO RELATÓRIO, e a distinção não é cosmética: `quadros` zera a
	// cada segundo para medir a taxa, então usá-la como "já tenho imagem?" faria o pedido
	// disparar de novo a cada relatório — uma enxurrada de pedidos com a imagem
	// perfeitamente no ar, e cada um custando ao outro lado um quadro-chave caro.
	jaTemImagem := false
	var ultimoPedido time.Time

	comeco := time.Now()
	var quadros, pacotes, amostras, ignorados int
	relatorio := time.Now()

	for {
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			return
		}
		pacotes++

		switch {
		case !p.queremVer():
			// FORA DO PALCO: o pacote foi lido (que era a obrigação) e agora é lixo.
			// Fechar o descompressor devolve os buffers dele na hora, em vez de deixá-los
			// parados enquanto a pessoa lê uma conversa de texto.
			if d != nil {
				d.Fechar()
				d, remontador = nil, nil
				jaTemImagem = false
			}
			desistiu = false
			ignorados++

		case d == nil && desistiu:
			// Já sabemos que não há como decodificar. Segue lendo e descartando.
			ignorados++

		default:
			if d == nil {
				// O tamanho é palpite; o de verdade vem no fluxo (ver `Descompressor`).
				novo, err := AbrirDescompressor(1280, 720)
				if err != nil {
					fmt.Fprintf(os.Stderr, "sem descompressor para a tela de %s: %v\n", p.id, err)
					p.saida.Manda(Evento{Ev: EvErro, Par: p.id, Msg: "não consigo mostrar a tela: " + err.Error()})
					// NÃO SAI DA FUNÇÃO, e isto mudou: sair pararia de ler a faixa, e faixa
					// sem leitor é buffer enchendo até o processo ficar sem memória. Falhar
					// em mostrar é ruim; falhar em mostrar E derrubar a call é pior.
					desistiu = true
					ignorados++
					continue
				}
				d = novo
				// REMONTADOR NOVO A CADA SUBIDA AO PALCO. O antigo guarda uma janela de
				// números de sequência de minutos atrás; reaproveitá-lo faria o primeiro
				// quadro bom cair fora da janela e ser descartado calado.
				remontador = samplebuilder.New(pacotesQueEsperam, &codecs.H264Packet{}, 90000)
				p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: d.Nome})
				pedirImagem()
				ultimoPedido = time.Now()
			}

			remontador.Push(pacote)

			// INSISTE ENQUANTO NÃO HOUVER IMAGEM, e para de insistir assim que houver.
			//
			// Chegar pacote e não sair quadro é exatamente o estado de quem entrou no meio
			// de um grupo de imagens: os quadros de diferença chegam, mas não há em que
			// aplicá-los. Um pedido por segundo é o suficiente para não desperdiçar o
			// intervalo e pouco o bastante para não virar enxurrada — cada pedido atendido
			// custa ao outro lado um quadro caro.
			if !jaTemImagem && time.Since(ultimoPedido) >= time.Second {
				pedirImagem()
				ultimoPedido = time.Now()
			}

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
					jaTemImagem = true
					p.entrega.Mandar(p.id, q)
				})
				if err != nil {
					fmt.Fprintf(os.Stderr, "quadro estragado de %s: %v\n", p.id, err)
				}
			}
		}

		// O RELATÓRIO CONTA AS QUATRO ETAPAS, e não só a última.
		//
		// "0 fps" sozinho não diz nada: pode ser rede que não chega, remontador que
		// nunca fecha um quadro, descompressor que recusa tudo — ou, agora, ninguém
		// olhando. São quatro estados diferentes com o mesmo sintoma, e separá-los aqui é
		// o que transformou uma caçada em uma leitura — foi este contador que apontou o
		// remontador acima.
		if desde := time.Since(relatorio); desde >= time.Second {
			msg := fmt.Sprintf("%d fps · %d pacotes · %d remontados",
				int(float64(quadros)/desde.Seconds()), pacotes, amostras)
			if ignorados > 0 {
				msg += fmt.Sprintf(" · %d descartados sem ninguém olhando", ignorados)
			}
			p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "ritmo", Msg: msg})
			relatorio = time.Now()
			quadros, pacotes, amostras, ignorados = 0, 0, 0, 0
		}
	}
}

// queremVer responde se a tela desta pessoa está no palco do Astra.
//
// NULO É SIM, e não é descuido: é o que mantém o processo utilizável fora do Astra. Ver
// `App.assistindo`, que é quem monta este fechamento.
func (p *Par) queremVer() bool {
	return p.querVer == nil || p.querVer()
}
