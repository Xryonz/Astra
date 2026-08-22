package main

// A TRANSMISSÃO DE TELA — o laço que leva o quadro capturado até a rede.
//
// O QUE JÁ EXISTIA E O QUE FALTAVA. A captura (`tela.go`, DXGI Desktop Duplication) e
// o compressor (`transmissao.go`, H.264 do Media Foundation) estavam prontos e
// testados havia tempo, mas só eram exercitados por `MedirTransmissao` — um BANCO DE
// PROVAS, que captura, comprime, conta e joga fora. Os bytes nunca saíam da máquina,
// porque não havia faixa de vídeo em conexão nenhuma.
//
// Este arquivo é a peça que faltava, e é pequena de propósito: quase tudo aqui é o
// laço de `MedirTransmissao` com um destino em vez de um contador.
//
// UMA FAIXA PARA A SALA INTEIRA, pela mesma razão do microfone (ver `NovoPar`): um
// `TrackLocalStaticSample` guarda uma ligação por conexão em que foi adicionado, e uma
// escrita nele reaproveita o MESMO quadro comprimido para todas. Numa malha isso é a
// diferença entre comprimir uma vez e comprimir N vezes — e o compressor é, de longe,
// a coisa mais cara que este processo faz.
//
// A FAIXA NASCE COM A CONEXÃO, e não quando a transmissão começa. Em WebRTC, incluir
// uma faixa depois obriga a renegociar o SDP com todo mundo da sala; declarar desde o
// início custa uma linha de mídia parada e apagada, e "começar a transmitir" vira
// simplesmente começar a escrever nela.

import (
	"context"
	"fmt"
	"os"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

// CapacidadeH264 é como a faixa de vídeo se anuncia no SDP.
//
// `42e01f` é o dialeto que todo mundo entende: perfil Baseline (0x42) com as três
// restrições ligadas (0xe0) no nível 3.1 (0x1f). Não é o melhor H.264 possível — é o
// único que atravessa navegador, celular e biblioteca sem negociação falhar.
//
// `packetization-mode=1` permite fatiar um NAL grande em vários pacotes (FU-A). Sem
// ele, um quadro-chave de 720p simplesmente não caberia num pacote e a imagem nunca
// abriria.
//
// `level-asymmetry-allowed=1` diz que os dois lados não precisam do mesmo nível. É o
// que permite declarar 3.1 aqui e mandar 720p60 (que pede 3.2) sem o outro lado
// recusar de saída.
//
// O QUE ISTO AINDA NÃO GARANTE, e está anotado porque só dói do lado que decodifica: o
// compressor do Windows não recebe ordem de perfil (ver `configurarSaida`), então ele
// emite o padrão DELE. Se emitir Main ou High, esta declaração estará mentindo. Por
// isso o emissor LÊ o perfil de dentro do primeiro SPS que sai e o reporta — ver
// `perfilDoSPS`. Medir em vez de supor, e conferir antes de existir alguém para
// reclamar.
var CapacidadeH264 = webrtc.RTPCodecCapability{
	MimeType:    webrtc.MimeTypeH264,
	ClockRate:   90000,
	SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
}

// A JANELA DO AQUECIMENTO, em quadros.
//
// Oito descartados porque a partida do compressor não representa o regime: não há quadro
// de referência ainda, o primeiro é obrigatoriamente chave (dezenas de vezes maior que
// os outros), e o de software leva algumas voltas para encher a fila interna. Medir a
// partida e concluir que a máquina é fraca condenaria a taxa por causa dos piores
// quadros que ela jamais produzirá.
//
// Dezesseis medidos porque é amostra suficiente para o custo parar de oscilar e ainda
// assim fechar rápido: vinte e quatro quadros são 0,4s a 60/s e 1,6s a 15/s.
const (
	quadrosDescartados = 8
	quadrosMedidos     = 16
)

// AjustesDaTela é o preset que a pessoa escolheu em Configurações › Voz.
type AjustesDaTela struct {
	Monitor int
	Largura int
	Altura  int
	Fps     int
	Kbps    int
}

// Emissor liga e desliga a transmissão. Um por processo — transmitir duas telas ao
// mesmo tempo custaria dois compressores, e ninguém pediu isso.
type Emissor struct {
	faixa *webrtc.TrackLocalStaticSample
	saida *Escritor

	mu     sync.Mutex
	parar  context.CancelFunc
	parada chan struct{}

	// "Alguém precisa de um quadro-chave AGORA." Bandeira e não canal: o pedido não se
	// acumula — dois pedidos no mesmo instante querem a mesma coisa, e atender duas
	// vezes só gastaria banda com dois quadros caros seguidos.
	querChave atomic.Bool
}

func NovoEmissor(faixa *webrtc.TrackLocalStaticSample, saida *Escritor) *Emissor {
	return &Emissor{faixa: faixa, saida: saida}
}

// PedirQuadroChave atende ao "perdi a imagem" de quem assiste.
//
// CHAMADA DE OUTRA GOROUTINE — a que lê os recados de cada conexão —, então ela só
// levanta a bandeira; quem manda no compressor é o laço, que é dono da thread presa
// onde o objeto do Media Foundation vive. Tocar no compressor daqui seria usá-lo de
// outra thread, que em COM não dá erro: dá comportamento indefinido.
//
// Segura chamar com a transmissão desligada: a bandeira fica levantada e a próxima
// transmissão começa com um quadro-chave, que é justamente o que se quer.
func (e *Emissor) PedirQuadroChave() { e.querChave.Store(true) }

// Ligar começa a transmitir. Chamar com a transmissão no ar TROCA o preset: desliga o
// laço atual e sobe outro. É o caminho de "mudei a qualidade no meio da call", e sai
// de graça porque desligar e ligar já é o que ele faz.
func (e *Emissor) Ligar(aj AjustesDaTela) {
	e.Desligar()

	ctx, cancelar := context.WithCancel(context.Background())
	parada := make(chan struct{})

	e.mu.Lock()
	e.parar = cancelar
	e.parada = parada
	e.mu.Unlock()

	go func() {
		defer close(parada)
		if err := e.laco(ctx, aj); err != nil && ctx.Err() == nil {
			// Falha DEPOIS de ter começado: a pessoa apertou "transmitir" e a imagem
			// morreu. Precisa aparecer na tela dela, senão fica um botão aceso sobre
			// nada acontecendo.
			fmt.Fprintf(os.Stderr, "transmissão parou: %v\n", err)
			e.saida.Manda(Evento{Ev: EvErro, Msg: "transmissão parou: " + err.Error()})
		}
		e.saida.Manda(Evento{Ev: EvTransmissao, V: "0"})
	}()
}

// Desligar para o laço e ESPERA ele morrer.
//
// A espera não é zelo: o laço segura um dispositivo D3D11, uma duplicação de tela e um
// compressor do Media Foundation. Voltar antes de ele soltar tudo deixaria o próximo
// `Ligar` disputando a duplicação com o anterior — e a Desktop Duplication é EXCLUSIVA
// por processo, então o segundo simplesmente falharia.
func (e *Emissor) Desligar() {
	e.mu.Lock()
	parar, parada := e.parar, e.parada
	e.parar, e.parada = nil, nil
	e.mu.Unlock()

	if parar == nil {
		return
	}
	parar()
	<-parada
}

// laco é o caminho inteiro: captura, comprime, escreve na faixa.
//
// PRESO NUMA THREAD SÓ, do começo ao fim. COM tem afinidade de thread, e o dispositivo
// D3D11, a duplicação e o compressor foram todos criados aqui — usá-los de outra
// thread não dá erro claro, dá comportamento indefinido.
func (e *Emissor) laco(ctx context.Context, aj AjustesDaTela) error {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		return err
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return err
	}
	defer fecharMF()

	tela, err := AbrirTela(aj.Monitor)
	if err != nil {
		return fmt.Errorf("abrir a tela: %w", err)
	}
	defer tela.Fechar()

	// DUAS TENTATIVAS NO MÁXIMO, e a segunda só existe quando a primeira descobriu que a
	// máquina não sustenta a taxa pedida. A primeira MEDE; a segunda já roda na taxa
	// escolhida e não mede mais nada.
	//
	// Reabrir em vez de só espaçar o laço não é capricho: o controle de banda do
	// compressor DIVIDE a banda pela taxa declarada. Declarar 60 e entregar 30 faz cada
	// quadro sair com metade dos bits que poderia — imagem pior pela mesma banda, e nada
	// no código dizendo por quê.
	novaTaxa, err := e.transmitir(ctx, tela, aj, true)
	if err != nil || ctx.Err() != nil || novaTaxa == 0 {
		return err
	}
	aj.Fps = novaTaxa
	_, err = e.transmitir(ctx, tela, aj, false)
	return err
}

// transmitir é o cano inteiro numa taxa. Devolve uma taxa DIFERENTE DE ZERO quando o
// aquecimento concluiu que a máquina não sustenta a que está rodando — e aí quem chamou
// reabre nessa.
func (e *Emissor) transmitir(ctx context.Context, tela *Tela, aj AjustesDaTela, medir bool) (int, error) {
	c, err := AbrirCompressor(tela, aj.Largura, aj.Altura, aj.Fps, aj.Kbps)
	if err != nil {
		return 0, err
	}
	defer c.Fechar()

	// A CONFIRMAÇÃO SAI ANTES DO PRIMEIRO QUADRO, e com o que de fato foi montado —
	// não com o que foi pedido. O compressor pode ter caído para software, e o tamanho
	// pode ter sido arredondado para par. Anunciar o pedido em vez do obtido é como se
	// esconde uma queda de qualidade de quem está pagando por ela.
	//
	// E A QUEDA PARA SOFTWARE VAI POR EXTENSO, porque o nome do compressor não conta
	// isso a quem não é do ramo: "H264 Encoder MFT" e "Intel® Quick Sync Video H.264
	// Encoder MFT" são a diferença entre a máquina estar acelerada e não estar, e nada
	// nos dois nomes diz qual é qual. Sem esta frase, quem cair para software vê 30
	// quadros por segundo e conclui que o Astra escolheu 30 por conta própria.
	comoSubiu := fmt.Sprintf("%dx%d @%d", c.saidaL, c.saidaA, c.fps)
	if c.NaMemoria {
		comoSubiu = "sem aceleração de placa · " + comoSubiu
	}
	e.saida.Manda(Evento{
		Ev:   EvTransmissao,
		V:    "1",
		Tipo: c.Nome,
		Msg:  comoSubiu,
	})

	// UMA CHAMADA DE VOLTA = UM QUADRO = UMA AMOSTRA. Escrito assim depois de o
	// contrário ter sido MEDIDO custando metade da taxa.
	//
	// O primeiro desenho juntava tudo que saísse de uma chamada de `Comprimir` num
	// buffer só, na crença de que a chamada de volta viesse uma vez por PEDAÇO de
	// quadro. Não vem: `sair` já junta os buffers de uma amostra do compressor
	// (`ConvertToContiguousBuffer`) e chama de volta UMA vez por amostra — e uma amostra
	// é um quadro codificado inteiro.
	//
	// O estrago aparecia porque o compressor da placa é ASSÍNCRONO. Ele responde por
	// recados, então uma volta do laço às vezes drena DOIS quadros já prontos. Juntando
	// os dois numa escrita só, o relógio do RTP andava a duração de UM — e a medição
	// mostrava exatamente isso: 29 quadros capturados por segundo virando 14 amostras.
	// Metade da taxa, com o tempo andando errado, e nada errado na rede para explicar.
	//
	// Escrever de dentro da chamada de volta é seguro: `WriteSample` empacota e
	// despacha na hora, então o fatiamento emprestado não sobrevive à chamada — que é
	// exatamente a regra que ele exige.
	duracao := time.Second / time.Duration(c.fps)

	ritmo := NovoRitmo(c.fps)
	comeco := time.Now()
	relatorio := time.Now()
	var quadros, bytesEnviados, capturados, semSaida, semMudanca int
	var marco Custos // onde a janela do aquecimento começa; ver o `switch` no laço
	perfilVisto := false

	// A ENTREGA VIVE FORA DO LAÇO porque o quadro pronto pode chegar em DUAS ocasiões:
	// junto de uma compressão, e na colheita avulsa de quando a tela não mudou. Duas
	// cópias desta função divergiriam, e a que divergisse seria a do caminho raro — o
	// que se percebe só quando alguém para de mexer no mouse.
	var falhaAoEntregar error
	entregar := func(quadroPronto []byte) {
		if !perfilVisto {
			if p, ok := perfilDoSPS(quadroPronto); ok {
				perfilVisto = true
				e.saida.Manda(Evento{Ev: EvTransmissao, V: "1", Tipo: "perfil", Msg: p})
			}
		}
		// ESCREVER SEM NINGUÉM CONECTADO NÃO É ERRO. Um `TrackLocalStaticSample` sem
		// ligação nenhuma engole a amostra em silêncio, e é o que queremos: transmitir
		// sozinho numa sala é o caso de quem começou a compartilhar antes de o primeiro
		// convidado chegar.
		//
		// A FALHA É GUARDADA e não devolvida daqui: esta é uma chamada de volta que o
		// compressor faz de dentro do laço dele, e abandoná-la no meio deixaria a fila
		// de saída por drenar — o jeito conhecido de travar os dois lados.
		if err := e.faixa.WriteSample(media.Sample{Data: quadroPronto, Duration: duracao}); err != nil && falhaAoEntregar == nil {
			falhaAoEntregar = err
		}
		quadros++
		bytesEnviados += len(quadroPronto)
	}

	for {
		if ctx.Err() != nil {
			return 0, nil
		}

		// A ESPERA VEM ANTES DA CAPTURA — mesma razão do banco de provas: ir ao DXGI
		// mais rápido que o compasso só produz quadro para jogar fora, e a ida custa.
		ritmo.Esperar()

		textura, err := tela.ProximoQuadro(100)
		if err != nil {
			if _, perdeu := err.(ErroDeAcessoPerdido); perdeu {
				// TROCA DE RESOLUÇÃO, JOGO EM TELA CHEIA, BLOQUEIO DE SESSÃO. A
				// duplicação morre nessas horas e remontar é o comportamento certo —
				// derrubar a transmissão faria a pessoa reapertar o botão toda vez que
				// alguém apertasse Ctrl+Alt+Del do outro lado da casa.
				if err := tela.Remontar(aj.Monitor); err != nil {
					return 0, fmt.Errorf("recuperar a tela: %w", err)
				}
				continue
			}
			return 0, fmt.Errorf("capturar: %w", err)
		}
		if textura == 0 {
			// Nada mudou na tela dentro do prazo. Não é falha: é uma tela parada, que
			// é o caso comum de quem compartilha um documento.
			//
			// MAS AINDA PRECISA COLHER. O compressor deixou de ser drenado até o fim a
			// cada quadro (ver o comentário em `Comprimir`), então há sempre um quadro
			// ou dois maturando dentro dele. Sem esta chamada, parar de mexer na tela
			// congelaria a imagem de quem assiste UM QUADRO ANTES do que deveria — e
			// justamente no instante em que a pessoa parou de mexer para alguém ler o
			// que está ali.
			semMudanca++
			if err := c.Drenar(entregar); err != nil {
				return 0, fmt.Errorf("colher o que sobrou: %w", err)
			}
			if falhaAoEntregar != nil {
				return 0, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
			}
			continue
		}
		capturados++

		// O PEDIDO É ATENDIDO ANTES DE ENTREGAR O QUADRO, e a ordem é o que faz ele
		// valer: a ordem vale para o PRÓXIMO quadro que entrar no compressor, então
		// levantá-la depois de entregar atenderia o quadro seguinte — um a mais de
		// espera, que é justamente o que o pedido existe para cortar.
		if e.querChave.Swap(false) {
			if !c.ForcarQuadroChave() {
				// Compressor que não atende pedido não é erro: é um que segue o próprio
				// compasso, e a espera volta a ser o intervalo dele. Vale o registro
				// porque explica uma imagem que demora a abrir.
				fmt.Fprintf(os.Stderr, "%s não atende pedido de quadro-chave\n", c.Nome)
			}
		}

		saiuAlgo := false
		err = c.Comprimir(textura, time.Since(comeco), func(quadroPronto []byte) {
			saiuAlgo = true
			entregar(quadroPronto)
		})
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			return 0, fmt.Errorf("comprimir: %w", err)
		}
		if falhaAoEntregar != nil {
			return 0, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
		}
		if !saiuAlgo {
			// O compressor ainda está juntando. Normal nos primeiros quadros, e normal
			// em qualquer volta de um compressor assíncrono — o quadro sai numa próxima.
			semSaida++
		}

		// O AQUECIMENTO: a máquina sustenta a taxa que prometemos?
		//
		// A medição acontece com a transmissão JÁ NO AR — estes quadros são reais e
		// saem para quem assiste. Não há tela de espera, não há atraso para começar; o
		// que existe é uma pergunta feita ao vigésimo quadro em vez de a um banco de
		// provas que teria de abrir um segundo compressor.
		//
		// OS PRIMEIROS QUADROS SÃO OS MAIS CAROS e por isso são descartados: o
		// compressor ainda não tem quadro de referência, o primeiro é sempre chave, e o
		// de software leva algumas voltas para encher a fila interna. Medir esses e
		// concluir que a máquina é fraca condenaria a taxa por causa da partida.
		if medir {
			switch c.Custos.Quadros {
			case quadrosDescartados:
				// A marca de onde a conta começa. Tudo antes daqui foi partida.
				marco = c.Custos
			case quadrosDescartados + quadrosMedidos:
				medir = false
				custo := (c.Custos.Total() - marco.Total()) / quadrosMedidos
				if nova := TaxaQueCabe(custo, c.fps); nova < c.fps {
					e.saida.Manda(Evento{
						Ev:   EvTransmissao,
						V:    "1",
						Tipo: "ritmo",
						Msg: fmt.Sprintf("esta máquina gasta %.1fms por quadro; caindo para %d/s",
							float64(custo.Microseconds())/1000, nova),
					})
					return nova, nil
				}
			}
		}

		// UM RELATÓRIO POR SEGUNDO. É o que dá à pessoa uma prova de que a transmissão
		// está viva enquanto ainda não existe imagem para ver — e o que permite
		// perceber que a máquina não está dando conta antes de alguém reclamar.
		// O RELATÓRIO CONTA ONDE OS QUADROS FICAM, e não só quantos saíram.
		//
		// "14 fps" sozinho não diz se a máquina não dá conta, se a tela estava parada, ou
		// se o compressor está segurando quadro — três coisas com o mesmo número e
		// remédios opostos. Do lado que recebe, contar as etapas foi o que apontou o
		// defeito do remontador em vez de mandar caçar no decodificador; aqui vale a
		// mesma regra.
		if desde := time.Since(relatorio); desde >= time.Second {
			e.saida.Manda(Evento{
				Ev:   EvTransmissao,
				V:    "1",
				Tipo: "ritmo",
				Msg: fmt.Sprintf("%d fps · %.1f Mbps · %d capturados · %d sem saída · %d sem mudança",
					int(float64(quadros)/desde.Seconds()),
					float64(bytesEnviados)*8/desde.Seconds()/1_000_000,
					capturados, semSaida, semMudanca),
			})
			relatorio = time.Now()
			quadros, bytesEnviados, capturados, semSaida, semMudanca = 0, 0, 0, 0, 0
		}
	}
}

// perfilDoSPS lê o perfil e o nível de dentro do próprio fluxo.
//
// POR QUE LER O BITSTREAM em vez de perguntar ao compressor: perguntar exigiria a
// chave `MF_MT_MPEG2_PROFILE`, mais um GUID copiado de algum lugar — e GUID errado
// falha em SILÊNCIO no Media Foundation, devolvendo zero como se fosse resposta. Os
// três bytes que interessam estão em toda sequência de parâmetros, logo depois do
// cabeçalho do NAL, e ali não há o que interpretar errado.
//
// Devolve o mesmo formato do `profile-level-id` do SDP, para dar para comparar com o
// que a faixa declara sem converter nada na cabeça.
func perfilDoSPS(fluxo []byte) (string, bool) {
	for i := 0; i+4 < len(fluxo); i++ {
		// Código de início, de três ou quatro bytes. Os dois convivem no mesmo fluxo.
		if fluxo[i] != 0 || fluxo[i+1] != 0 {
			continue
		}
		inicio := 0
		if fluxo[i+2] == 1 {
			inicio = i + 3
		} else if fluxo[i+2] == 0 && i+5 < len(fluxo) && fluxo[i+3] == 1 {
			inicio = i + 4
		} else {
			continue
		}
		// Os cinco bits de baixo do cabeçalho dizem o tipo; 7 é a sequência de
		// parâmetros (SPS), a única que carrega perfil e nível.
		if inicio+3 >= len(fluxo) || fluxo[inicio]&0x1F != 7 {
			continue
		}
		return fmt.Sprintf("%02x%02x%02x", fluxo[inicio+1], fluxo[inicio+2], fluxo[inicio+3]), true
	}
	return "", false
}
