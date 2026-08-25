package main

import (
	"testing"
	"time"
)

func alimentar(c *ControleDeBanda, perda float64, quantos int) (mudancas int) {
	for i := 0; i < quantos; i++ {
		if _, mudou := c.Segundo(perda); mudou {
			mudancas++
		}
	}
	return mudancas
}

func TestUmPicoIsoladoNaoDerrubaABanda(t *testing.T) {
	c := NovoControleDeBanda(2500)

	if n := alimentar(c, 0.30, segundosParaRecuar-1); n != 0 {
		t.Errorf("mudou %d vez(es) com %d segundos ruins; o limiar é %d",
			n, segundosParaRecuar-1, segundosParaRecuar)
	}
	if c.Banda() != 2500 {
		t.Errorf("banda caiu para %d kbps por causa de um pico", c.Banda())
	}
}

func TestPerdaSustentadaDerrubaABanda(t *testing.T) {
	c := NovoControleDeBanda(2500)

	if n := alimentar(c, 0.30, segundosParaRecuar); n != 1 {
		t.Fatalf("esperava uma queda com %d segundos ruins, houve %d", segundosParaRecuar, n)
	}

	if c.Banda() != 2125 {
		t.Errorf("banda ficou em %d kbps, esperava 2125", c.Banda())
	}
}

func TestQuedaEhProporcionalAPerda(t *testing.T) {
	leve := NovoControleDeBanda(2500)
	alimentar(leve, 0.30, segundosParaRecuar)

	grave := NovoControleDeBanda(2500)
	alimentar(grave, 0.60, segundosParaRecuar)

	if grave.Banda() >= leve.Banda() {
		t.Errorf("perda de 60%% deixou %d kbps e a de 30%% deixou %d: a queda não acompanha a perda",
			grave.Banda(), leve.Banda())
	}
}

func TestABandaVoltaASubir(t *testing.T) {
	c := NovoControleDeBanda(2500)
	alimentar(c, 0.40, segundosParaRecuar)
	caiuPara := c.Banda()
	if caiuPara >= 2500 {
		t.Fatalf("não caiu: %d kbps", caiuPara)
	}

	if n := alimentar(c, 0.0, segundosParaSubir-1); n != 0 {
		t.Errorf("subiu %d vez(es) antes dos %d segundos limpos", n, segundosParaSubir)
	}
	if n := alimentar(c, 0.0, 1); n != 1 {
		t.Errorf("não subiu depois de %d segundos limpos", segundosParaSubir)
	}
	if c.Banda() <= caiuPara {
		t.Errorf("banda ficou em %d kbps, não subiu de %d", c.Banda(), caiuPara)
	}
}

func TestNuncaPassaDoPreset(t *testing.T) {
	c := NovoControleDeBanda(2500)

	alimentar(c, 0.0, 500)
	if c.Banda() > 2500 {
		t.Errorf("passou do preset: %d kbps", c.Banda())
	}
}

func TestNuncaAfundaAbaixoDoPiso(t *testing.T) {
	c := NovoControleDeBanda(2500)

	alimentar(c, 0.90, 500)
	if c.Banda() < bandaMinima {
		t.Errorf("afundou para %d kbps, abaixo do piso de %d", c.Banda(), bandaMinima)
	}
}

func TestZonaMortaNaoZeraOsContadores(t *testing.T) {
	c := NovoControleDeBanda(2500)

	for i := 0; i < segundosParaRecuar; i++ {
		c.Segundo(0.30)
		c.Segundo(0.05)
	}
	if c.Banda() >= 2500 {
		t.Errorf("perda oscilando no limiar não fez nada: banda ainda em %d kbps", c.Banda())
	}
}

func TestPiorPerdaEntrePares(t *testing.T) {
	p := NovaPerdaDosPares()
	p.Relatar("ana", 0.02)
	p.Relatar("bob", 0.35)
	p.Relatar("caio", 0.10)

	if pior := p.Pior(); pior != 0.35 {
		t.Errorf("pior perda deu %.2f, esperava 0.35", pior)
	}

	p.Esquecer("bob")
	if pior := p.Pior(); pior != 0.10 {
		t.Errorf("depois de bob sair, pior perda deu %.2f, esperava 0.10", pior)
	}
}

func TestRelatoVelhoNaoSeguraABanda(t *testing.T) {
	p := NovaPerdaDosPares()
	p.mu.Lock()

	p.perdas["fantasma"] = relatoDePerda{fracao: 0.80, quando: time.Now().Add(-10 * time.Second)}
	p.mu.Unlock()
	p.Relatar("ana", 0.01)

	if pior := p.Pior(); pior != 0.01 {
		t.Errorf("relato velho ainda conta: pior perda deu %.2f, esperava 0.01", pior)
	}
}
