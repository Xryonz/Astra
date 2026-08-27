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

func TestOEstimadorNuncaPassaDoPreset(t *testing.T) {
	c := NovoControleDeBanda(2500)

	if nova, mudou := c.Sugerido(800); !mudou || nova != 800 {
		t.Fatalf("a rede apertou para 800 e a banda foi para %d (mudou=%v)", nova, mudou)
	}

	nova, mudou := c.Sugerido(9000)
	if !mudou {
		t.Fatal("a rede liberou 9000 e a banda não subiu de 800")
	}
	if nova != 2500 {
		t.Fatalf("subiu para %d; o preset de 2500 é o teto e não se ultrapassa", nova)
	}
}

func TestOEstimadorRespeitaOPiso(t *testing.T) {
	c := NovoControleDeBanda(2500)

	if nova, _ := c.Sugerido(10); nova < bandaMinima {
		t.Errorf("a rede disse 10 kbps e a banda foi para %d, abaixo do piso %d", nova, bandaMinima)
	}
}

func TestOEstimadorSoMexeQuandoValeAPena(t *testing.T) {
	c := NovoControleDeBanda(2500)

	if _, mudou := c.Sugerido(2400); mudou {
		t.Error("4% de diferença reiniciou o compressor à toa")
	}
	if nova, mudou := c.Sugerido(1200); !mudou || nova != 1200 {
		t.Errorf("queda pela metade não pegou: %d (mudou=%v)", nova, mudou)
	}
}

func TestAMenorBandaEntreOsPares(t *testing.T) {
	p := NovaPerdaDosPares()
	p.RelatarBanda("folgado", 8000)
	p.RelatarBanda("apertado", 900)
	p.RelatarBanda("medio", 3000)

	menor, ok := p.MenorBanda()
	if !ok || menor != 900 {
		t.Fatalf("menor banda = %d (ok=%v), esperava 900", menor, ok)
	}
}

func TestSemRelatoDeBandaNaoHaAlvo(t *testing.T) {
	p := NovaPerdaDosPares()
	p.Relatar("alguem", 0.2)

	if menor, ok := p.MenorBanda(); ok {
		t.Errorf("inventou alvo de %d kbps sem ninguém ter medido", menor)
	}
}

func TestRelatarPerdaNaoApagaABanda(t *testing.T) {
	p := NovaPerdaDosPares()
	p.RelatarBanda("alguem", 1800)
	p.Relatar("alguem", 0.03)

	menor, ok := p.MenorBanda()
	if !ok || menor != 1800 {
		t.Fatalf("a banda sumiu ao relatar perda: %d (ok=%v)", menor, ok)
	}
	if pior := p.Pior(); pior < 0.029 || pior > 0.031 {
		t.Errorf("a perda sumiu ao relatar banda: %.3f", pior)
	}
}

func TestBandaVelhaNaoManda(t *testing.T) {
	p := NovaPerdaDosPares()
	p.mu.Lock()
	p.perdas["sumido"] = relatoDePerda{kbps: 500, bandaEm: time.Now().Add(-validadeDoRelato - time.Second)}
	p.mu.Unlock()

	if menor, ok := p.MenorBanda(); ok {
		t.Errorf("relato vencido ainda mandava: %d kbps", menor)
	}
}
