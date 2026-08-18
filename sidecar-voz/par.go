package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"sync"

	"github.com/pion/webrtc/v4"
)

// Par é a conexão com UMA pessoa da call.
//
// Numa malha, cada participante tem um destes por companheiro. Eles não sabem uns
// dos outros: quem coordena é o App, e essa ignorância é proposital — um par que
// cai não pode arrastar os outros junto.
type Par struct {
	id    string
	pc    *webrtc.PeerConnection
	saida *Escritor

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
	saida *Escritor,
) (*Par, error) {
	pc, err := webrtc.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("criar conexão: %w", err)
	}

	p := &Par{id: id, pc: pc, saida: saida}

	if faixa != nil {
		if _, err := pc.AddTrack(faixa); err != nil {
			// Fecha o que acabou de abrir: devolver um par sem áudio seria pior
			// que devolver erro, porque a call "conectaria" muda e ninguém saberia
			// por quê.
			_ = pc.Close()
			return nil, fmt.Errorf("publicar microfone: %w", err)
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
		// A faixa PRECISA ser drenada, mesmo antes de existir reprodução: pacote
		// que não é lido fica se acumulando no buffer do Pion. Uma conexão sem
		// leitor não é "silenciosa", é uma conexão que vaza memória enquanto a
		// pessoa fala.
		go p.drenar(remota)
	})

	return p, nil
}

// drenar lê a faixa remota até ela acabar.
//
// Por ora só descarta: a decodificação e a mistura entram junto com a camada de
// áudio. A goroutine morre sozinha quando `ReadRTP` devolve erro, o que acontece
// quando o par fecha — então não há vazamento aqui, e é por isso que não precisa
// de contexto nem de canal de parada.
func (p *Par) drenar(remota *webrtc.TrackRemote) {
	for {
		if _, _, err := remota.ReadRTP(); err != nil {
			return
		}
	}
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

	if err := p.pc.Close(); err != nil {
		fmt.Fprintf(os.Stderr, "fechar par %s: %v\n", p.id, err)
	}
}
