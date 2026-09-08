package main

const (
	perdaEsperadaDePartida = 10
	perdaEsperadaMinima    = 5
	perdaEsperadaMaxima    = 25

	quedaPorRelato = 1
)

type PerdaParaOpus struct {
	atual int
}

func NovaPerdaParaOpus() *PerdaParaOpus {
	return &PerdaParaOpus{atual: perdaEsperadaDePartida}
}

func (p *PerdaParaOpus) Atual() int { return p.atual }

func (p *PerdaParaOpus) Relato(fracao float64) (int, bool) {
	medida := int(fracao*100 + 0.5)
	if medida < perdaEsperadaMinima {
		medida = perdaEsperadaMinima
	}
	if medida > perdaEsperadaMaxima {
		medida = perdaEsperadaMaxima
	}

	antes := p.atual
	if medida > p.atual {
		p.atual = medida
	} else if medida < p.atual {
		p.atual -= quedaPorRelato
		if p.atual < medida {
			p.atual = medida
		}
	}
	return p.atual, p.atual != antes
}
