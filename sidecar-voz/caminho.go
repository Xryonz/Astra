package main

import (
	"fmt"
	"os"
	"time"

	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
)

const (
	relatarOCaminhoACada = 5 * time.Second

	desde1900 = 2208988800

	idaAbsurda = 10 * time.Second
)

func ntpDoMeio(t time.Time) uint32 {
	segundos := uint64(t.Unix()) + desde1900
	fracao := uint64(t.Nanosecond()) << 32 / 1_000_000_000
	return uint32((segundos<<32 | fracao) >> 16)
}

func passosParaTempo(passos uint32) time.Duration {
	return time.Duration(passos) * time.Second / 65536
}

func idaEVolta(r rtcp.ReceptionReport, agora time.Time) (time.Duration, bool) {
	if r.LastSenderReport == 0 {
		return 0, false
	}
	d := passosParaTempo(ntpDoMeio(agora) - r.LastSenderReport - r.Delay)
	if d > idaAbsurda {
		return 0, false
	}
	return d, true
}

func tremorDoRelato(r rtcp.ReceptionReport, taxa uint32) time.Duration {
	if taxa == 0 {
		return 0
	}
	return time.Duration(r.Jitter) * time.Second / time.Duration(taxa)
}

type MedidorDoCaminho struct {
	oQue string
	taxa uint32

	relatorio time.Time
	soma      time.Duration
	pico      time.Duration
	tremor    time.Duration
	amostras  int
}

func NovoMedidorDoCaminho(oQue string, taxa uint32) *MedidorDoCaminho {
	return &MedidorDoCaminho{oQue: oQue, taxa: taxa, relatorio: time.Now()}
}

func (m *MedidorDoCaminho) Anotar(relato *rtcp.ReceiverReport, agora time.Time) {
	for _, r := range relato.Reports {
		if t := tremorDoRelato(r, m.taxa); t > m.tremor {
			m.tremor = t
		}
		d, ok := idaEVolta(r, agora)
		if !ok {
			continue
		}
		m.soma += d
		m.amostras++
		if d > m.pico {
			m.pico = d
		}
	}
}

func (m *MedidorDoCaminho) Fechar(agora time.Time) (string, bool) {
	if agora.Sub(m.relatorio) < relatarOCaminhoACada || m.amostras == 0 {
		return "", false
	}
	linha := fmt.Sprintf("%s: ida e volta %d ms (pico %d ms) · tremor %d ms",
		m.oQue,
		(m.soma / time.Duration(m.amostras)).Milliseconds(),
		m.pico.Milliseconds(),
		m.tremor.Milliseconds())

	m.relatorio = agora
	m.soma, m.pico, m.tremor, m.amostras = 0, 0, 0, 0
	return linha, true
}

func escoarRtcp(receptor *webrtc.RTPReceiver) {
	for {
		if _, _, err := receptor.ReadRTCP(); err != nil {
			return
		}
	}
}

func (p *Par) ouvirOCaminho(remetente *webrtc.RTPSender, oQue string, taxa uint32) {
	medidor := NovoMedidorDoCaminho(oQue, taxa)

	for {
		pacotes, _, err := remetente.ReadRTCP()
		if err != nil {
			return
		}
		agora := time.Now()
		for _, pacote := range pacotes {
			if relato, ok := pacote.(*rtcp.ReceiverReport); ok {
				medidor.Anotar(relato, agora)
			}
		}
		if linha, pronto := medidor.Fechar(agora); pronto {
			fmt.Fprintf(os.Stderr, "caminho de %s · %s\n", p.id, linha)
		}
	}
}
