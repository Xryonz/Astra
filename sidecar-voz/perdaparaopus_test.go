package main

import "testing"

func TestRedeLimpaBaixaAProtecaoAtePiso(t *testing.T) {
	p := NovaPerdaParaOpus()

	for volta := 0; volta < 50; volta++ {
		p.Relato(0)
	}
	if p.Atual() != perdaEsperadaMinima {
		t.Errorf("rede sem perda nenhuma parou em %d%%, esperava o piso de %d%%",
			p.Atual(), perdaEsperadaMinima)
	}
}

func TestAProtecaoSobeNaHora(t *testing.T) {
	p := NovaPerdaParaOpus()

	novo, mudou := p.Relato(0.18)
	if !mudou || novo != 18 {
		t.Fatalf("18%% de perda deviam virar 18%% de proteção na hora, veio %d (mudou=%v)", novo, mudou)
	}
}

func TestAProtecaoDesceDevagar(t *testing.T) {
	p := NovaPerdaParaOpus()
	p.Relato(0.20)

	if novo, _ := p.Relato(0); novo != 20-quedaPorRelato {
		t.Errorf("a proteção caiu de 20%% para %d%% de uma vez: uma rede que oscila "+
			"ficaria desprotegida no próximo tropeço", novo)
	}
}

func TestOTetoSeguraRedePessima(t *testing.T) {
	p := NovaPerdaParaOpus()

	novo, _ := p.Relato(0.90)
	if novo != perdaEsperadaMaxima {
		t.Errorf("90%% de perda viraram %d%% de proteção; acima de %d%% só se gasta banda à toa",
			novo, perdaEsperadaMaxima)
	}
}

func TestSemMudancaNaoMexeNoCodificador(t *testing.T) {
	p := NovaPerdaParaOpus()

	if _, mudou := p.Relato(float64(perdaEsperadaDePartida) / 100); mudou {
		t.Error("relato igual ao valor atual pediu troca à toa no codificador")
	}
}
