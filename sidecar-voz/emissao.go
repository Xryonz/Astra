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

// DE QUANTO EM QUANTO TEMPO A TELA PARADA DÁ SINAL DE VIDA.
//
// O número sai de um empate entre duas coisas que puxam para lados opostos:
//
//	curto demais  ->  banda gasta à toa. O quadro reenviado é um quadro-chave, e neste
//	                  compressor ele mede ~99 KB (medido em `sonda_nal_test.go`). A dois
//	                  segundos isso são ~0,4 Mbps com a tela imóvel.
//	longo demais  ->  quem para de transmitir demora a sumir do palco alheio, porque o
//	                  outro lado só pode declarar o fim depois de esperar mais que isto.
//
// Dois segundos gastam 16% do preset de 2,5 Mbps — e gastam isso justamente no momento em
// que os outros 84% não estão sendo usados, porque nada está mudando na tela. Do outro
// lado, `silencioQueEncerra` espera duas vezes e meia isto antes de dar a tela por
// encerrada, de modo que dois sinais podem se perder na rede sem apagar a imagem de
// ninguém.
const sinalDeVida = 2 * time.Second

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

	// Quanto cada par está perdendo. Vive aqui, e não dentro do laço, porque quem
	// escreve são as goroutines de RTCP — uma por par — e elas existem mesmo com a
	// transmissão desligada.
	perdas *PerdaDosPares
}

func NovoEmissor(faixa *webrtc.TrackLocalStaticSample, saida *Escritor) *Emissor {
	return &Emissor{faixa: faixa, saida: saida, perdas: NovaPerdaDosPares()}
}

// PerdaRelatada guarda o que um par acabou de dizer sobre o que não chegou.
//
// CHAMADA DE OUTRA GOROUTINE, a que lê o RTCP daquele par — mesma regra de
// `PedirQuadroChave`. Segura chamar com a transmissão desligada: o número fica guardado
// e envelhece sozinho.
func (e *Emissor) PerdaRelatada(par string, fracao float64) { e.perdas.Relatar(par, fracao) }

// EsquecerPar tira alguém da conta ao sair da sala.
func (e *Emissor) EsquecerPar(par string) { e.perdas.Esquecer(par) }

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

	// REABRIR É O ATUADOR DE DUAS COISAS, e por isso este laço existe.
	//
	//	a MÁQUINA não sustenta a taxa pedida  ->  reabre com menos quadros por segundo
	//	a REDE não sustenta a banda pedida    ->  reabre com menos kbps
	//
	// Reabrir em vez de ajustar ao vivo não é preguiça: o compressor só aceita a taxa e
	// a banda na ABERTURA. Foram três rotas tentadas para mudar a banda com ele aberto,
	// e as três falharam — duas aceitas e ignoradas, uma derrubando o compressor. O
	// registro está em `sonda_banda_ao_vivo_test.go`.
	//
	// O CONTROLE VIVE AQUI FORA, e essa é a linha que faz a coisa funcionar: ele guarda
	// o TETO do preset e os contadores de histerese. Criado lá dentro, cada reabertura o
	// zeraria — o teto viraria a banda já reduzida, e a imagem nunca voltaria a melhorar
	// depois que a rede sarasse.
	controle := NovoControleDeBanda(aj.Kbps)

	medir := true
	for {
		novo, err := e.transmitir(ctx, tela, aj, medir, controle)
		if err != nil || ctx.Err() != nil || novo == nil {
			return err
		}
		aj = *novo
		medir = false
	}
}

// transmitir é o cano inteiro num preset. Devolve um preset NOVO quando descobriu que o
// atual não serve — e aí quem chamou reabre nele. Nulo significa "acabou".
func (e *Emissor) transmitir(
	ctx context.Context, tela *Tela, aj AjustesDaTela, medir bool, controle *ControleDeBanda,
) (*AjustesDaTela, error) {
	c, err := AbrirCompressor(tela, aj.Largura, aj.Altura, aj.Fps, aj.Kbps)
	if err != nil {
		return nil, err
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
	var quadros, bytesEnviados, capturados, semSaida, semMudanca, revividos int
	var marco Custos // onde a janela do aquecimento começa; ver o `switch` no laço
	perfilVisto := false

	// A ENTREGA VIVE FORA DO LAÇO porque o quadro pronto pode chegar em DUAS ocasiões:
	// junto de uma compressão, e na colheita avulsa de quando a tela não mudou. Duas
	// cópias desta função divergiriam, e a que divergisse seria a do caminho raro — o
	// que se percebe só quando alguém para de mexer no mouse.
	var falhaAoEntregar error
	// O ÚLTIMO QUADRO QUE ABRE IMAGEM SOZINHO, guardado para ser reenviado com a tela
	// parada. Ver `abreImagemSozinho` e o bloco `textura == 0` mais abaixo.
	var abridor []byte
	var ultimaSaida time.Time
	reenviando := false
	entregar := func(quadroPronto []byte) {
		if !perfilVisto {
			if p, ok := perfilDoSPS(quadroPronto); ok {
				perfilVisto = true
				e.saida.Manda(Evento{Ev: EvTransmissao, V: "1", Tipo: "perfil", Msg: p})
			}
		}
		// A CÓPIA É OBRIGATÓRIA: este fatiamento é emprestado do compressor e vale só até
		// a chamada de volta terminar. `reenviando` evita copiar o buffer sobre si mesmo
		// no caminho do reenvio, que funcionaria mas seria trabalho para nada.
		if !reenviando && abreImagemSozinho(quadroPronto) {
			abridor = append(abridor[:0], quadroPronto...)
		}
		ultimaSaida = time.Now()
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
			return nil, nil
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
					return nil, fmt.Errorf("recuperar a tela: %w", err)
				}
				continue
			}
			return nil, fmt.Errorf("capturar: %w", err)
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
				return nil, fmt.Errorf("colher o que sobrou: %w", err)
			}
			if falhaAoEntregar != nil {
				return nil, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
			}

			// A TELA PARADA AINDA PRECISA DAR SINAL DE VIDA — e este bloco conserta dois
			// defeitos de uma vez, os dois medidos em `sonda_fimdatela_test.go`.
			//
			// 1. QUEM CHEGA COM A TELA PARADA NUNCA VIA IMAGEM. O pedido de quadro-chave
			//    era atendido lá embaixo, DEPOIS do `continue` que este bloco substitui:
			//    com a tela parada, `querChave` ficava pendurado e ninguém o levantava.
			//    Quem entrasse numa sala onde já se compartilhava uma tela parada lia
			//    "abrindo a tela de fulano…" até alguém mexer o mouse.
			//
			// 2. QUEM PARAVA DE TRANSMITIR FICAVA CONGELADO NO PALCO ALHEIO. Não existe
			//    pacote de "acabou" em RTP; a faixa só para de trafegar. Quem assiste não
			//    tinha como distinguir "parou" de "está parada", porque os dois eram
			//    silêncio. Agora "está parada" faz barulho, e só o silêncio de verdade
			//    quer dizer que acabou (ver `recepcao.go`).
			//
			// POR QUE REENVIAR EM VEZ DE CAPTURAR DE NOVO: com nada mudando o DXGI não
			// entrega quadro nenhum — `QuadroAtual` não ajuda, foi medido. A alternativa
			// seria guardar uma cópia da textura na placa (CreateTexture2D + CopyResource)
			// e recomprimi-la; reenviar bytes que já saíram custa zero de CPU e ~99 KB de
			// memória, e o resultado no fio é idêntico.
			//
			// REENVIAR É SEGURO PORQUE O QUADRO É AUTOCONTIDO. Um IDR com SPS e PPS junto
			// não descreve diferença nenhuma em relação ao anterior: ele descreve a imagem
			// inteira. Aplicá-lo duas vezes dá duas vezes a mesma imagem. Com um quadro de
			// diferença (P) isso seria errado, e é por isso que só o abridor é guardado.
			if len(abridor) > 0 {
				pediram := e.querChave.Swap(false)
				if pediram || time.Since(ultimaSaida) >= sinalDeVida {
					reenviando = true
					entregar(abridor)
					reenviando = false
					revividos++
					if falhaAoEntregar != nil {
						return nil, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
					}
				}
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
			return nil, fmt.Errorf("comprimir: %w", err)
		}
		if falhaAoEntregar != nil {
			return nil, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
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
					proximo := aj
					proximo.Fps = nova
					return &proximo, nil
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
			perda := e.perdas.Pior()
			msg := fmt.Sprintf("%d fps · %.1f Mbps · %.0f%% perdido · %d capturados · %d sem saída · %d sem mudança",
				int(float64(quadros)/desde.Seconds()),
				float64(bytesEnviados)*8/desde.Seconds()/1_000_000,
				perda*100,
				capturados, semSaida, semMudanca)
			// SEPARADO DOS OUTROS, porque é o único número que aparece quando a tela está
			// parada — e é ele que explica uma transmissão gastando banda com "0 fps".
			if revividos > 0 {
				msg += fmt.Sprintf(" · %d reenviados com a tela parada", revividos)
			}
			e.saida.Manda(Evento{Ev: EvTransmissao, V: "1", Tipo: "ritmo", Msg: msg})
			relatorio = time.Now()
			quadros, bytesEnviados, capturados, semSaida, semMudanca, revividos = 0, 0, 0, 0, 0, 0

			// A REDE ESTÁ AGUENTANDO? O controle decide uma vez por segundo e quase
			// sempre responde "continua igual" — a histerese dele existe justamente
			// porque agir custa uma reabertura. Ver `banda.go`.
			//
			// SÓ VALE ENQUANTO NÃO ESTAMOS MEDINDO A MÁQUINA: durante o aquecimento a
			// taxa ainda pode mudar, e reabrir por banda no meio disso jogaria fora a
			// medição pela metade e recomeçaria a conta.
			if !medir {
				if nova, mudou := controle.Segundo(perda); mudou {
					e.saida.Manda(Evento{
						Ev: EvTransmissao, V: "1", Tipo: "ritmo",
						Msg: fmt.Sprintf("%.0f%% dos pacotes não chegam; ajustando para %d kbps",
							perda*100, nova),
					})
					proximo := aj
					proximo.Kbps = nova
					return &proximo, nil
				}
			}
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
	perfil, achou := "", false
	percorrerNal(fluxo, func(tipo byte, inicio int) bool {
		// 7 é a sequência de parâmetros (SPS), a única que carrega perfil e nível.
		if tipo != 7 || inicio+3 >= len(fluxo) {
			return true
		}
		perfil = fmt.Sprintf("%02x%02x%02x", fluxo[inicio+1], fluxo[inicio+2], fluxo[inicio+3])
		achou = true
		return false
	})
	return perfil, achou
}

// percorrerNal visita cada unidade do fluxo, entregando o tipo e onde ela começa.
// Devolver `false` para a função para a varredura.
//
// Código de início de três ou quatro bytes; os dois convivem no mesmo fluxo, e é por isso
// que isto não é um `bytes.Split`. Os cinco bits de baixo do primeiro byte dizem o tipo.
func percorrerNal(fluxo []byte, cada func(tipo byte, inicio int) bool) {
	for i := 0; i+4 < len(fluxo); i++ {
		if fluxo[i] != 0 || fluxo[i+1] != 0 {
			continue
		}
		inicio := 0
		switch {
		case fluxo[i+2] == 1:
			inicio = i + 3
		case fluxo[i+2] == 0 && i+5 < len(fluxo) && fluxo[i+3] == 1:
			inicio = i + 4
		default:
			continue
		}
		if !cada(fluxo[inicio]&0x1F, inicio) {
			return
		}
	}
}

// abreImagemSozinho diz se esta amostra basta para o outro lado montar imagem do zero.
//
// A DEFINIÇÃO É PRECISA E NÃO É "É UM QUADRO-CHAVE". Um IDR sozinho não abre nada: ele
// descreve a imagem, mas as dimensões, o perfil e as tabelas de referência estão no SPS
// (7) e no PPS (8). Guardar um IDR pelado para reenviar depois entregaria a quem chega um
// quadro que o descompressor recusa — e o sintoma seria "abrindo a tela…" para sempre,
// que é exatamente o defeito que se está consertando.
//
// MEDIDO neste compressor (`sonda_nal_test.go`): o quadro-chave sai `IDR SEI SPS PPS AUD`
// com ~99 KB, e os normais saem `P SEI PPS AUD` com ~9 KB. Ou seja, os três vêm juntos —
// mas isso é conclusão de medição, não de documentação, e esta função é onde a suposição
// fica testável se algum dia o compressor for outro.
func abreImagemSozinho(fluxo []byte) bool {
	var temSPS, temPPS, temIDR bool
	percorrerNal(fluxo, func(tipo byte, _ int) bool {
		switch tipo {
		case 7:
			temSPS = true
		case 8:
			temPPS = true
		case 5:
			temIDR = true
		}
		return !(temSPS && temPPS && temIDR)
	})
	return temSPS && temPPS && temIDR
}
