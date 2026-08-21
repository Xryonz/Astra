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
	"bytes"
	"context"
	"fmt"
	"os"
	"runtime"
	"sync"
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
}

func NovoEmissor(faixa *webrtc.TrackLocalStaticSample, saida *Escritor) *Emissor {
	return &Emissor{faixa: faixa, saida: saida}
}

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

	c, err := AbrirCompressor(tela, aj.Largura, aj.Altura, aj.Fps, aj.Kbps)
	if err != nil {
		return err
	}
	defer c.Fechar()

	// A CONFIRMAÇÃO SAI ANTES DO PRIMEIRO QUADRO, e com o que de fato foi montado —
	// não com o que foi pedido. O compressor pode ter caído para software, e o tamanho
	// pode ter sido arredondado para par. Anunciar o pedido em vez do obtido é como se
	// esconde uma queda de qualidade de quem está pagando por ela.
	e.saida.Manda(Evento{
		Ev:   EvTransmissao,
		V:    "1",
		Tipo: c.Nome,
		Msg:  fmt.Sprintf("%dx%d @%d", c.saidaL, c.saidaA, c.fps),
	})

	// UM QUADRO INTEIRO POR AMOSTRA, e é aqui que mora o erro fácil.
	//
	// `Comprimir` chama de volta uma vez POR PEDAÇO, e um quadro rende mais de um
	// (parâmetros, fatias). Escrever na faixa a cada pedaço faria o relógio do RTP
	// andar uma duração de quadro por PEDAÇO — o outro lado veria a imagem em câmera
	// lenta e o áudio dessincronizado, sem nada errado na rede.
	//
	// Então os pedaços de um quadro se juntam aqui e saem numa escrita só. O
	// empacotador de H.264 do pion sabe separar os NALs pelos códigos de início e
	// fatiar o que não couber no pacote.
	var quadro bytes.Buffer
	duracao := time.Second / time.Duration(c.fps)

	ritmo := NovoRitmo(c.fps)
	comeco := time.Now()
	relatorio := time.Now()
	var quadros, bytesEnviados int
	perfilVisto := false

	for {
		if ctx.Err() != nil {
			return nil
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
					return fmt.Errorf("recuperar a tela: %w", err)
				}
				continue
			}
			return fmt.Errorf("capturar: %w", err)
		}
		if textura == 0 {
			// Nada mudou na tela dentro do prazo. Não é falha: é uma tela parada, que
			// é o caso comum de quem compartilha um documento.
			continue
		}

		quadro.Reset()
		err = c.Comprimir(textura, time.Since(comeco), func(nal []byte) {
			// COPIA, e a cópia é obrigatória: o fatiamento entregue vale só até a
			// chamada seguinte (ver `Comprimir`). Guardar a fatia em vez do conteúdo
			// daria um quadro montado com os bytes do quadro seguinte.
			quadro.Write(nal)
		})
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			return fmt.Errorf("comprimir: %w", err)
		}
		if quadro.Len() == 0 {
			// O compressor ainda está juntando. Normal nos primeiros quadros.
			continue
		}

		if !perfilVisto {
			if p, ok := perfilDoSPS(quadro.Bytes()); ok {
				perfilVisto = true
				e.saida.Manda(Evento{Ev: EvTransmissao, V: "1", Tipo: "perfil", Msg: p})
			}
		}

		// ESCREVER SEM NINGUÉM CONECTADO NÃO É ERRO. Um `TrackLocalStaticSample` sem
		// ligação nenhuma engole a amostra em silêncio, e é o que queremos: transmitir
		// sozinho numa sala é o caso de quem começou a compartilhar antes de o
		// primeiro convidado chegar.
		if err := e.faixa.WriteSample(media.Sample{Data: quadro.Bytes(), Duration: duracao}); err != nil {
			return fmt.Errorf("entregar o quadro: %w", err)
		}
		quadros++
		bytesEnviados += quadro.Len()

		// UM RELATÓRIO POR SEGUNDO. É o que dá à pessoa uma prova de que a transmissão
		// está viva enquanto ainda não existe imagem para ver — e o que permite
		// perceber que a máquina não está dando conta antes de alguém reclamar.
		if desde := time.Since(relatorio); desde >= time.Second {
			e.saida.Manda(Evento{
				Ev:   EvTransmissao,
				V:    "1",
				Tipo: "ritmo",
				Msg: fmt.Sprintf("%d fps · %.1f Mbps",
					int(float64(quadros)/desde.Seconds()),
					float64(bytesEnviados)*8/desde.Seconds()/1_000_000),
			})
			relatorio = time.Now()
			quadros, bytesEnviados = 0, 0
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
