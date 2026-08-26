package main

import (
	"sync"
	"time"

	"github.com/pion/rtcp"
)

const (
	perdaQueDoi = 0.10

	perdaConfortavel = 0.02

	segundosParaRecuar = 3
	segundosParaSubir  = 20

	bandaMinima = 300

	passoDeSubida = 1.30

	mudancaQueValeAPena = 0.15
)

type ControleDeBanda struct {
	teto int

	atual int
	ruins int
	bons  int
}

func NovoControleDeBanda(teto int) *ControleDeBanda {
	if teto < bandaMinima {
		teto = bandaMinima
	}
	return &ControleDeBanda{teto: teto, atual: teto}
}

func (c *ControleDeBanda) Banda() int { return c.atual }

func (c *ControleDeBanda) Segundo(perda float64) (int, bool) {
	switch {
	case perda > perdaQueDoi:
		c.ruins++
		c.bons = 0
	case perda < perdaConfortavel:
		c.bons++
		c.ruins = 0
	default:

		return c.atual, false
	}

	switch {
	case c.ruins >= segundosParaRecuar:

		return c.mudarPara(int(float64(c.atual) * (1 - 0.5*perda)))
	case c.bons >= segundosParaSubir:
		return c.mudarPara(int(float64(c.atual) * passoDeSubida))
	}
	return c.atual, false
}

func (c *ControleDeBanda) mudarPara(novo int) (int, bool) {
	if novo > c.teto {
		novo = c.teto
	}
	if novo < bandaMinima {
		novo = bandaMinima
	}

	if diferenca(novo, c.atual) < mudancaQueValeAPena {
		return c.atual, false
	}

	c.atual = novo
	c.ruins, c.bons = 0, 0
	return novo, true
}

func diferenca(a, b int) float64 {
	if b == 0 {
		return 1
	}
	d := float64(a-b) / float64(b)
	if d < 0 {
		return -d
	}
	return d
}

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

type PerdaDosPares struct {
	mu     sync.Mutex
	perdas map[string]relatoDePerda
}

type relatoDePerda struct {
	fracao float64
	quando time.Time
}

func NovaPerdaDosPares() *PerdaDosPares {
	return &PerdaDosPares{perdas: map[string]relatoDePerda{}}
}

func (p *PerdaDosPares) Relatar(par string, fracao float64) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.perdas[par] = relatoDePerda{fracao: fracao, quando: time.Now()}
}

func (p *PerdaDosPares) Esquecer(par string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	delete(p.perdas, par)
}

func (p *PerdaDosPares) Pior() float64 {
	p.mu.Lock()
	defer p.mu.Unlock()

	const validade = 3 * time.Second
	pior, agora := 0.0, time.Now()
	for par, r := range p.perdas {
		if agora.Sub(r.quando) > validade {
			delete(p.perdas, par)

			continue
		}
		if r.fracao > pior {
			pior = r.fracao
		}
	}
	return pior
}
