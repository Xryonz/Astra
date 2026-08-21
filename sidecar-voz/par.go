package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"
)

// Par é a conexão com UMA pessoa da call.
//
// Numa malha, cada participante tem um destes por companheiro. Eles não sabem uns
// dos outros: quem coordena é o App, e essa ignorância é proposital — um par que
// cai não pode arrastar os outros junto.
type Par struct {
	id         string
	pc         *webrtc.PeerConnection
	saida      *Escritor
	misturador *Misturador

	// Candidatos que chegaram ANTES da descrição remota.
	//
	// Isto não é caso raro, é o caso NORMAL do trickle ICE: o outro lado começa a
	// mandar candidato assim que descobre o primeiro, e isso costuma acontecer
	// antes de a resposta dele voltar por um servidor que está do outro lado do
	// país. O Pion recusa candidato sem descrição remota, então guardar e aplicar
	// depois é obrigatório — sem isso a conexão fecha mais devagar, ou não fecha.
	mu        sync.Mutex
	guardados []webrtc.ICECandidateInit
	temRemota bool
	fechado   bool
}

// NovoPar abre a conexão e liga os avisos. `faixa` é o áudio do microfone.
//
// A MESMA `faixa` é passada para todos os pares, de propósito, e essa é a
// otimização mais importante desta malha. Um `TrackLocalStaticSample` guarda por
// dentro uma ligação por conexão em que foi adicionado, e uma escrita nele
// reaproveita o mesmo quadro codificado para todas. Ou seja: o Opus roda UMA vez
// por quadro de 20ms, não uma vez por pessoa na sala.
//
// O que isso NÃO faz, e é importante não se enganar: não economiza banda. Cada par
// continua recebendo a própria cópia dos pacotes pela rede. O que fica constante é
// a CPU de codificação, e é ela que estouraria primeiro numa máquina modesta.
func NovoPar(
	id string,
	config webrtc.Configuration,
	faixa *webrtc.TrackLocalStaticSample,
	tela *webrtc.TrackLocalStaticSample,
	mist *Misturador,
	saida *Escritor,
) (*Par, error) {
	pc, err := webrtc.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("criar conexão: %w", err)
	}

	p := &Par{id: id, pc: pc, saida: saida, misturador: mist}

	if faixa != nil {
		if _, err := pc.AddTrack(faixa); err != nil {
			// Fecha o que acabou de abrir: devolver um par sem áudio seria pior
			// que devolver erro, porque a call "conectaria" muda e ninguém saberia
			// por quê.
			_ = pc.Close()
			return nil, fmt.Errorf("publicar microfone: %w", err)
		}
	} else {
		// SEM MICROFONE AINDA PRECISA DECLARAR QUE OUVE.
		//
		// Isto não é detalhe: em WebRTC, quem não anuncia nada não negocia nada. Um
		// par sem faixa produz uma resposta sem áudio nenhum, e o outro lado falha
		// com "codec não suportado" — foi exatamente assim que o teste de chamada
		// completa quebrou na primeira vez.
		//
		// O caso é real e não é raro: entrar na call sem microfone, com o
		// microfone tomado por outro programa, ou só para escutar. Sem esta linha,
		// essas pessoas não ouviriam ninguém.
		if _, err := pc.AddTransceiverFromKind(
			webrtc.RTPCodecTypeAudio,
			webrtc.RTPTransceiverInit{Direction: webrtc.RTPTransceiverDirectionRecvonly},
		); err != nil {
			_ = pc.Close()
			return nil, fmt.Errorf("declarar que ouve: %w", err)
		}
	}

	// A TELA ENTRA AGORA, MESMO SEM NINGUÉM TRANSMITINDO.
	//
	// Uma faixa incluída depois obriga a renegociar o SDP com todo mundo da sala — e
	// renegociação em malha é N apertos de mão, no exato instante em que a pessoa
	// acabou de apertar "transmitir" e a máquina já vai começar a comprimir. Declarada
	// desde o início, ela custa uma linha de mídia parada, e começar a transmitir vira
	// apenas começar a escrever nela.
	//
	// Falhar aqui NÃO derruba a conexão, e é a diferença deste bloco para o do
	// microfone: sem áudio a call não tem função, mas sem vídeo ela é exatamente o que
	// o Astra já era até esta versão. Uma call com voz e sem tela compartilhada é bem
	// melhor que erro na cara de quem só queria conversar.
	if tela != nil {
		if _, err := pc.AddTrack(tela); err != nil {
			fmt.Fprintf(os.Stderr, "sem transmissão de tela para %s: %v\n", id, err)
			saida.Manda(Evento{Ev: EvErro, Par: id, Msg: "sem transmissão de tela: " + err.Error()})
		}
	}

	// TRICKLE ICE: manda cada candidato assim que aparece, em vez de esperar a
	// coleta terminar. Numa malha isso pesa muito mais do que numa call de dois —
	// são N-1 apertos de mão acontecendo ao mesmo tempo, e esperar a coleta
	// completa de cada um multiplica a espera até a primeira voz sair.
	pc.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			// Fim da coleta. O outro lado não precisa saber.
			return
		}
		bruto, err := json.Marshal(c.ToJSON())
		if err != nil {
			return
		}
		saida.Manda(Evento{Ev: EvSinal, Par: id, Tipo: SinalCandidato, Dados: string(bruto)})
	})

	pc.OnConnectionStateChange(func(estado webrtc.PeerConnectionState) {
		saida.Manda(Evento{Ev: EvEstado, Par: id, V: estado.String()})
	})

	pc.OnTrack(func(remota *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		// A faixa PRECISA ser lida sempre: pacote que não é consumido fica se
		// acumulando no buffer do Pion. Uma conexão sem leitor não é "silenciosa",
		// é uma conexão que vaza memória enquanto a pessoa fala.
		go p.receber(remota)
	})

	return p, nil
}

// receber lê a voz desta pessoa, decodifica e entrega ao misturador.
//
// UMA GOROUTINE E UM DECODIFICADOR POR PESSOA, e isso é obrigatório, não escolha
// de estilo: o decodificador Opus guarda estado entre quadros (é assim que ele
// recupera perda e faz a transição suave), então dois fluxos passando pelo mesmo
// decodificador produzem lixo. É também aqui que mora o custo que cresce com o
// tamanho da call.
//
// A goroutine morre sozinha quando `ReadRTP` devolve erro, o que acontece quando o
// par fecha — daí não precisar de contexto nem de canal de parada.
func (p *Par) receber(remota *webrtc.TrackRemote) {
	dec, err := NovoDecodificador(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		fmt.Fprintf(os.Stderr, "decodificador de %s: %v\n", p.id, err)
		return
	}
	defer dec.Fechar()

	var det DetectorDeFala
	// Faixa que morre com a pessoa falando deixaria o círculo aceso para sempre na
	// tela de quem ficou. Apagar na saída custa uma linha.
	defer func() {
		if det.Calar() {
			p.avisarFala(false)
		}
	}()

	pcm := make([]int16, AmostrasPorQuadro*6)
	for {
		// PRAZO DE LEITURA, e ele existe por um motivo específico.
		//
		// Quem está mudo não manda pacote NENHUM — o mudo corta na fonte, lá no
		// motor. Sem prazo, `ReadRTP` simplesmente bloqueia, o detector nunca é
		// alimentado, e o indicador de fala fica aceso até a pessoa sair da call.
		// O prazo transforma "não chegou nada" em silêncio explícito, que é o que
		// isso de fato é.
		//
		// Mais curto que a espera do detector, senão a espera nunca venceria.
		_ = remota.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			if errors.Is(err, os.ErrDeadlineExceeded) {
				if det.Alimentar(nil, time.Now()) {
					p.avisarFala(det.Falando())
				}
				continue
			}
			return
		}
		// Pacote de 1 ou 2 bytes é o DTX dizendo "continuo aqui, calado". Passar
		// isso ao decodificador é correto — ele gera o ruído de conforto — mas não
		// há voz nenhuma ali, então não vale ocupar espaço na fila da mistura.
		if len(pacote.Payload) <= 2 {
			// O DTX dizendo "continuo aqui, calado". Vale como silêncio para o
			// detector — é literalmente o que ele significa.
			if det.Alimentar(nil, time.Now()) {
				p.avisarFala(det.Falando())
			}
			continue
		}
		n, err := dec.Decodificar(pacote.Payload, pcm, false)
		if err != nil || n <= 0 {
			// Quadro estragado acontece; derrubar a conexão por causa de um seria
			// trocar um engasgo por uma queda.
			continue
		}
		if det.Alimentar(pcm[:n], time.Now()) {
			p.avisarFala(det.Falando())
		}
		if p.misturador != nil {
			p.misturador.Entregar(p.id, pcm[:n])
		}
	}
}

func (p *Par) avisarFala(falando bool) {
	p.saida.Manda(Evento{Ev: EvFala, Par: p.id, V: marcaDeFala(falando)})
}

// Oferecer inicia o aperto de mão. Só um dos dois lados chama.
func (p *Par) Oferecer(ctx context.Context) error {
	oferta, err := p.pc.CreateOffer(nil)
	if err != nil {
		return fmt.Errorf("montar oferta: %w", err)
	}
	if err := p.pc.SetLocalDescription(oferta); err != nil {
		return fmt.Errorf("assumir oferta: %w", err)
	}
	return p.mandarDescricao(SinalOferta, oferta)
}

// Receber trata um envelope vindo do outro lado.
func (p *Par) Receber(ctx context.Context, tipo, dados string) error {
	switch tipo {
	case SinalOferta:
		var desc webrtc.SessionDescription
		if err := json.Unmarshal([]byte(dados), &desc); err != nil {
			return fmt.Errorf("ler oferta: %w", err)
		}
		if err := p.pc.SetRemoteDescription(desc); err != nil {
			return fmt.Errorf("aceitar oferta: %w", err)
		}
		p.liberarGuardados()

		resposta, err := p.pc.CreateAnswer(nil)
		if err != nil {
			return fmt.Errorf("montar resposta: %w", err)
		}
		if err := p.pc.SetLocalDescription(resposta); err != nil {
			return fmt.Errorf("assumir resposta: %w", err)
		}
		return p.mandarDescricao(SinalResposta, resposta)

	case SinalResposta:
		var desc webrtc.SessionDescription
		if err := json.Unmarshal([]byte(dados), &desc); err != nil {
			return fmt.Errorf("ler resposta: %w", err)
		}
		if err := p.pc.SetRemoteDescription(desc); err != nil {
			return fmt.Errorf("aceitar resposta: %w", err)
		}
		p.liberarGuardados()
		return nil

	case SinalCandidato:
		var cand webrtc.ICECandidateInit
		if err := json.Unmarshal([]byte(dados), &cand); err != nil {
			return fmt.Errorf("ler candidato: %w", err)
		}
		p.mu.Lock()
		if !p.temRemota {
			p.guardados = append(p.guardados, cand)
			p.mu.Unlock()
			return nil
		}
		p.mu.Unlock()
		if err := p.pc.AddICECandidate(cand); err != nil {
			return fmt.Errorf("somar candidato: %w", err)
		}
		return nil

	default:
		return fmt.Errorf("envelope desconhecido: %q", tipo)
	}
}

func (p *Par) mandarDescricao(tipo string, desc webrtc.SessionDescription) error {
	bruto, err := json.Marshal(desc)
	if err != nil {
		return fmt.Errorf("empacotar %s: %w", tipo, err)
	}
	p.saida.Manda(Evento{Ev: EvSinal, Par: p.id, Tipo: tipo, Dados: string(bruto)})
	return nil
}

// liberarGuardados aplica o que chegou cedo demais.
//
// Erro aqui é registrado e engolido de propósito: um candidato ruim entre vinte
// não invalida o aperto de mão, e derrubar a conexão por causa de um seria trocar
// uma falha parcial por uma total. O ICE tenta todos os caminhos e usa o que
// funcionar.
func (p *Par) liberarGuardados() {
	p.mu.Lock()
	p.temRemota = true
	pendentes := p.guardados
	p.guardados = nil
	p.mu.Unlock()

	for _, c := range pendentes {
		if err := p.pc.AddICECandidate(c); err != nil {
			fmt.Fprintf(os.Stderr, "candidato guardado de %s recusado: %v\n", p.id, err)
		}
	}
}

// Fechar solta a conexão. Chamar duas vezes é seguro — e vai acontecer, porque
// sair da call e a queda do par podem correr juntos.
func (p *Par) Fechar() {
	p.mu.Lock()
	if p.fechado {
		p.mu.Unlock()
		return
	}
	p.fechado = true
	p.mu.Unlock()

	// Tira a voz da mistura junto. Sem isto, o buffer desta pessoa ficaria pendurado
	// até o tempo de esquecimento correr — e nesse meio-tempo o que sobrou na fila
	// dela ainda tocaria, dando a impressão de que alguém que já saiu continua ali.
	if p.misturador != nil {
		p.misturador.Esquecer(p.id)
	}

	if err := p.pc.Close(); err != nil {
		fmt.Fprintf(os.Stderr, "fechar par %s: %v\n", p.id, err)
	}
}
