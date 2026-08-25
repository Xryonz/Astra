package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
)

type Par struct {
	id         string
	pc         *webrtc.PeerConnection
	saida      *Escritor
	misturador *Misturador
	entrega    *EntregaDeQuadros
	plateia    *PlateiaDaTela

	pedirQuadroChave func()

	relatarPerda func(float64)

	querVer func() bool

	canal    *webrtc.DataChannel
	queroVer atomic.Bool

	mu        sync.Mutex
	guardados []webrtc.ICECandidateInit
	temRemota bool
	fechado   bool
}

const (
	idDoCanalDoPalco = uint16(1)
	marcaAssisto     = byte('1')
	marcaNaoAssisto  = byte('0')
)

func NovoPar(
	id string,
	config webrtc.Configuration,
	faixa *webrtc.TrackLocalStaticSample,
	plateia *PlateiaDaTela,
	mist *Misturador,
	entrega *EntregaDeQuadros,
	saida *Escritor,
) (*Par, error) {
	pc, err := webrtc.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("criar conexão: %w", err)
	}

	p := &Par{id: id, pc: pc, saida: saida, misturador: mist, entrega: entrega, plateia: plateia}
	p.queroVer.Store(true)

	if faixa != nil {
		if _, err := pc.AddTrack(faixa); err != nil {

			_ = pc.Close()
			return nil, fmt.Errorf("publicar microfone: %w", err)
		}
	} else {

		if _, err := pc.AddTransceiverFromKind(
			webrtc.RTPCodecTypeAudio,
			webrtc.RTPTransceiverInit{Direction: webrtc.RTPTransceiverDirectionRecvonly},
		); err != nil {
			_ = pc.Close()
			return nil, fmt.Errorf("declarar que ouve: %w", err)
		}
	}

	if plateia != nil {
		if err := p.abrirFaixaDaTela(); err != nil {
			fmt.Fprintf(os.Stderr, "sem transmissão de tela para %s: %v\n", id, err)
			saida.Manda(Evento{Ev: EvErro, Par: id, Msg: "sem transmissão de tela: " + err.Error()})
		}
	}

	p.abrirCanalDoPalco()

	pc.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {

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

		if remota.Kind() == webrtc.RTPCodecTypeVideo {
			go p.receberTela(remota)
			return
		}
		go p.receber(remota)
	})

	return p, nil
}

func (p *Par) abrirFaixaDaTela() error {
	faixa, err := p.plateia.Entrar(p.id)
	if err != nil {
		return err
	}
	remetente, err := p.pc.AddTrack(faixa)
	if err != nil {
		p.plateia.Sair(p.id)
		return err
	}
	go p.ouvirPedidos(remetente)
	return nil
}

func (p *Par) abrirCanalDoPalco() {
	negociado, id := true, idDoCanalDoPalco
	canal, err := p.pc.CreateDataChannel("palco", &webrtc.DataChannelInit{
		Negotiated: &negociado,
		ID:         &id,
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "sem canal de palco com %s: %v\n", p.id, err)
		return
	}
	p.canal = canal

	canal.OnOpen(func() { p.mandarPalco() })
	canal.OnMessage(func(recado webrtc.DataChannelMessage) {
		if len(recado.Data) == 0 || p.plateia == nil {
			return
		}
		p.plateia.Assiste(p.id, recado.Data[0] == marcaAssisto)
	})
}

func (p *Par) AvisarQueAssisto(quer bool) {
	p.queroVer.Store(quer)
	p.mandarPalco()
}

func (p *Par) mandarPalco() {
	if p.canal == nil || p.canal.ReadyState() != webrtc.DataChannelStateOpen {
		return
	}
	marca := marcaNaoAssisto
	if p.queroVer.Load() {
		marca = marcaAssisto
	}
	if err := p.canal.Send([]byte{marca}); err != nil {
		fmt.Fprintf(os.Stderr, "avisar o palco a %s: %v\n", p.id, err)
	}
}

func (p *Par) receber(remota *webrtc.TrackRemote) {
	dec, err := NovoDecodificador(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		fmt.Fprintf(os.Stderr, "decodificador de %s: %v\n", p.id, err)
		return
	}
	defer dec.Fechar()

	var det DetectorDeFala

	defer func() {
		if det.Calar() {
			p.avisarFala(false)
		}
	}()

	pcm := make([]int16, AmostrasPorQuadro*6)
	for {

		_ = remota.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			if esperaEstourada(err) {
				if det.Alimentar(nil, time.Now()) {
					p.avisarFala(det.Falando())
				}
				continue
			}
			return
		}

		if len(pacote.Payload) <= 2 {

			if det.Alimentar(nil, time.Now()) {
				p.avisarFala(det.Falando())
			}
			continue
		}
		n, err := dec.Decodificar(pacote.Payload, pcm, false)
		if err != nil || n <= 0 {

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

func (p *Par) ouvirPedidos(remetente *webrtc.RTPSender) {
	for {
		pacotes, _, err := remetente.ReadRTCP()
		if err != nil {
			return
		}
		for _, pacote := range pacotes {
			switch recado := pacote.(type) {
			case *rtcp.PictureLossIndication, *rtcp.FullIntraRequest:
				if p.pedirQuadroChave != nil {
					p.pedirQuadroChave()
				}
			case *rtcp.ReceiverReport:
				if p.relatarPerda == nil {
					continue
				}

				pior := 0.0
				for _, r := range recado.Reports {
					if f := float64(r.FractionLost) / 256; f > pior {
						pior = f
					}
				}
				p.relatarPerda(pior)
			}
		}
	}
}

func esperaEstourada(err error) bool {
	var deRede net.Error
	return errors.As(err, &deRede) && deRede.Timeout()
}

func (p *Par) avisarFala(falando bool) {
	p.saida.Manda(Evento{Ev: EvFala, Par: p.id, V: marcaDeFala(falando)})
}

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

func (p *Par) Fechar() {
	p.mu.Lock()
	if p.fechado {
		p.mu.Unlock()
		return
	}
	p.fechado = true
	p.mu.Unlock()

	if p.misturador != nil {
		p.misturador.Esquecer(p.id)
	}
	if p.plateia != nil {
		p.plateia.Sair(p.id)
	}

	if err := p.pc.Close(); err != nil {
		fmt.Fprintf(os.Stderr, "fechar par %s: %v\n", p.id, err)
	}
}
