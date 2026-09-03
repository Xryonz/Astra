package main

import (
	"fmt"
	"os"
	"time"

	"github.com/pion/rtcp"
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
	perda     float64
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
		if f := float64(r.FractionLost) / 256; f > m.perda {
			m.perda = f
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

type LeituraDoCaminho struct {
	Ida    int `json:"ida"`
	Pico   int `json:"pico"`
	Tremor int `json:"tremor"`
	Perda  int `json:"perda"`
}

func (m *MedidorDoCaminho) Fechar(agora time.Time) (LeituraDoCaminho, bool) {
	if agora.Sub(m.relatorio) < relatarOCaminhoACada || m.amostras == 0 {
		return LeituraDoCaminho{}, false
	}
	leitura := LeituraDoCaminho{
		Ida:    int((m.soma / time.Duration(m.amostras)).Milliseconds()),
		Pico:   int(m.pico.Milliseconds()),
		Tremor: int(m.tremor.Milliseconds()),
		Perda:  int(m.perda*100 + 0.5),
	}
	m.relatorio = agora
	m.soma, m.pico, m.tremor, m.amostras, m.perda = 0, 0, 0, 0, 0
	return leitura, true
}

func medidorDeCaminho(oQue string, taxa uint32, contar func(LeituraDoCaminho)) func(rtcp.Packet) {
	medidor := NovoMedidorDoCaminho(oQue, taxa)

	return func(pacote rtcp.Packet) {
		agora := time.Now()
		if relato, ok := pacote.(*rtcp.ReceiverReport); ok {
			medidor.Anotar(relato, agora)
		}
		if leitura, pronto := medidor.Fechar(agora); pronto {
			fmt.Fprintf(os.Stderr, "caminho da %s · ida e volta %d ms (pico %d) · tremor %d ms · perda %d%%\n",
				oQue, leitura.Ida, leitura.Pico, leitura.Tremor, leitura.Perda)
			if contar != nil {
				contar(leitura)
			}
		}
	}
}
