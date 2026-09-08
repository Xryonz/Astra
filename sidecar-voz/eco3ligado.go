package main

import (
	"fmt"
	"os"
	"sync"

	"github.com/Xryonz/Astra/sidecar-voz/eco3"
)

const AtrasoDaSaidaMs = MilissegundosDeFolgaNaSaida + MilissegundosPorQuadro

type Eco3 struct {
	mu         sync.Mutex
	cancelador *eco3.Cancelador
	porBloco   int
	silencio   []int16
}

func AbrirEco3() *Eco3 {
	c, err := eco3.Novo(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		fmt.Fprintf(os.Stderr, "AEC3 indisponível (%v); fica o cancelador do Windows\n", err)
		return nil
	}
	porBloco := c.AmostrasPorQuadro()
	return &Eco3{
		cancelador: c,
		porBloco:   porBloco,
		silencio:   make([]int16, porBloco),
	}
}

func (e *Eco3) Ajustar(ruido, ganho bool) {
	if e == nil {
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.cancelador == nil {
		return
	}
	_ = e.cancelador.Ajustar(ruido, ganho)
}

func (e *Eco3) Fechar() {
	if e == nil {
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	e.cancelador.Fechar()
	e.cancelador = nil
}

func (e *Eco3) Referencia(bloco []int16) {
	if e == nil {
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.cancelador == nil {
		return
	}
	if bloco == nil {
		for restam := AmostrasPorQuadro; restam >= e.porBloco; restam -= e.porBloco {
			_ = e.cancelador.Referencia(e.silencio)
		}
		return
	}
	for inicio := 0; inicio+e.porBloco <= len(bloco); inicio += e.porBloco {
		_ = e.cancelador.Referencia(bloco[inicio : inicio+e.porBloco])
	}
}

func (e *Eco3) Limpar(quadro []int16) {
	if e == nil {
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.cancelador == nil {
		return
	}
	for inicio := 0; inicio+e.porBloco <= len(quadro); inicio += e.porBloco {
		_ = e.cancelador.Capturar(quadro[inicio:inicio+e.porBloco], AtrasoDaSaidaMs)
	}
}

type CapturaComEco3 struct {
	fonte *Captura
	eco   *Eco3
}

var _ FonteDeAudio = (*CapturaComEco3)(nil)

func NovaCapturaComEco3(fonte *Captura, eco *Eco3) *CapturaComEco3 {
	return &CapturaComEco3{fonte: fonte, eco: eco}
}

func (c *CapturaComEco3) Ler(destino []int16) (int, bool, error) {
	n, silencio, err := c.fonte.Ler(destino)
	if err != nil || n <= 0 {
		return n, silencio, err
	}
	if !silencio {
		c.eco.Limpar(destino[:n])
	}
	return n, silencio, nil
}

func (c *CapturaComEco3) Esperar(limiteMs uint32) error { return c.fonte.Esperar(limiteMs) }

func (c *CapturaComEco3) Taxa() int { return c.fonte.Taxa() }

func (c *CapturaComEco3) Fechar() { c.fonte.Fechar() }
