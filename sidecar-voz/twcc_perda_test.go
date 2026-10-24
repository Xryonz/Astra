package main

import (
	"time"

	"github.com/pion/rtcp"
)

const janelaDoTwcc = 500 * time.Millisecond

type PerdaPeloTwcc struct {
	recebidos int
	perdidos  int
	abertaEm  time.Time
}

func (p *PerdaPeloTwcc) Somar(pacote *rtcp.TransportLayerCC, agora time.Time) (float64, bool) {
	recebidos, perdidos := contarNoTwcc(pacote)
	p.recebidos += recebidos
	p.perdidos += perdidos

	if p.abertaEm.IsZero() {
		p.abertaEm = agora
	}
	if agora.Sub(p.abertaEm) < janelaDoTwcc {
		return 0, false
	}

	total := p.recebidos + p.perdidos
	fracao := 0.0
	if total > 0 {
		fracao = float64(p.perdidos) / float64(total)
	}
	p.recebidos, p.perdidos, p.abertaEm = 0, 0, agora
	return fracao, total > 0
}

func contarNoTwcc(pacote *rtcp.TransportLayerCC) (recebidos, perdidos int) {
	restam := int(pacote.PacketStatusCount)
	for _, pedaco := range pacote.PacketChunks {
		if restam <= 0 {
			break
		}
		switch c := pedaco.(type) {
		case *rtcp.RunLengthChunk:
			quantos := int(c.RunLength)
			if quantos > restam {
				quantos = restam
			}
			if c.PacketStatusSymbol == rtcp.TypeTCCPacketNotReceived {
				perdidos += quantos
			} else {
				recebidos += quantos
			}
			restam -= quantos
		case *rtcp.StatusVectorChunk:
			for _, simbolo := range c.SymbolList {
				if restam <= 0 {
					break
				}
				if simbolo == rtcp.TypeTCCPacketNotReceived {
					perdidos++
				} else {
					recebidos++
				}
				restam--
			}
		}
	}
	return recebidos, perdidos
}
