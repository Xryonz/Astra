package main

// O CONTROLE DE BANDA, SEM REDE E SEM PLACA.
//
// É conta pura, e é por isso que dá para provar aqui: erro de sinal, histerese frouxa ou
// piso furado não aparecem em teste de integração — aparecem numa chamada de verdade,
// meia hora depois, como imagem que oscila ou que nunca volta a melhorar.
//
// O QUE MAIS IMPORTA PROVAR é o que NÃO acontece: que um pico isolado de perda não
// derruba a banda, e que a subida não é nervosa. O atuador deste controle é REABRIR o
// compressor (a banda só é ajustável na abertura — ver `sonda_banda_ao_vivo_test.go`), e
// cada reabertura custa um quadro-chave. Controle nervoso produziria mais engasgo do que
// a perda que ele existe para corrigir.

import (
	"testing"
	"time"
)

// alimentar entrega `quantos` segundos com a mesma perda e devolve quantas vezes a banda
// mudou e onde parou.
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

	// Dois segundos ruins e volta ao normal. É o padrão de um pico de rede — outra
	// pessoa da casa abrindo um vídeo, o wi-fi trocando de canal. Reagir a isso faria a
	// imagem piorar toda vez que alguém passasse perto do roteador.
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
	// Perder 30% tira 15% da banda: 2500 * (1 - 0.5*0.30) = 2125.
	if c.Banda() != 2125 {
		t.Errorf("banda ficou em %d kbps, esperava 2125", c.Banda())
	}
}

func TestQuedaEhProporcionalAPerda(t *testing.T) {
	leve := NovoControleDeBanda(2500)
	alimentar(leve, 0.30, segundosParaRecuar)

	grave := NovoControleDeBanda(2500)
	alimentar(grave, 0.60, segundosParaRecuar)

	// PERDA PIOR TEM DE DOER MAIS. Uma queda fixa seria tímida no colapso e brutal no
	// arranhão — e a diferença entre os dois é justamente o que decide se a imagem
	// volta.
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

	// SUBIR É LENTO DE PROPÓSITO. Vinte segundos limpos antes do primeiro passo: subir
	// cedo demais recria o congestionamento do qual acabamos de sair.
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

	// Rede perfeita por muito tempo. O preset é ESCOLHA da pessoa, não um alvo a
	// superar — a mesma regra de `TaxaQueCabe`.
	alimentar(c, 0.0, 500)
	if c.Banda() > 2500 {
		t.Errorf("passou do preset: %d kbps", c.Banda())
	}
}

func TestNuncaAfundaAbaixoDoPiso(t *testing.T) {
	c := NovoControleDeBanda(2500)

	// Rede em colapso por muito tempo. Abaixo do piso a imagem vira mosaico e deixa de
	// servir para o que compartilhar tela serve.
	alimentar(c, 0.90, 500)
	if c.Banda() < bandaMinima {
		t.Errorf("afundou para %d kbps, abaixo do piso de %d", c.Banda(), bandaMinima)
	}
}

func TestZonaMortaNaoZeraOsContadores(t *testing.T) {
	c := NovoControleDeBanda(2500)

	// Perda oscilando em torno do limiar é uma rede NO LIMITE, não uma rede saudável.
	// Se a zona morta zerasse os contadores, o controle ficaria esperando para sempre
	// uma sequência limpa que não vem — e a imagem quebraria sem nada agir.
	for i := 0; i < segundosParaRecuar; i++ {
		c.Segundo(0.30)
		c.Segundo(0.05) // zona morta: nem dói nem é confortável
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

	// A PIOR E NÃO A MÉDIA: numa malha o compressor é UM só para todos. Quem está na
	// conexão pior manda no ritmo de todo mundo, e a média esconderia exatamente essa
	// pessoa.
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
	// Um par que sumiu há dez segundos. Sem validade, a última coisa que ele disse antes
	// de cair seguraria a banda de todo mundo para sempre.
	p.perdas["fantasma"] = relatoDePerda{fracao: 0.80, quando: time.Now().Add(-10 * time.Second)}
	p.mu.Unlock()
	p.Relatar("ana", 0.01)

	if pior := p.Pior(); pior != 0.01 {
		t.Errorf("relato velho ainda conta: pior perda deu %.2f, esperava 0.01", pior)
	}
}
