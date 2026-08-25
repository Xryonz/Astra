package main

import (
	"math"
	"testing"
	"time"
)

func quadroCom(amplitude float64) []int16 {
	pcm := make([]int16, AmostrasPorQuadro)

	pico := amplitude * 32767 * math.Sqrt2
	for i := range pcm {
		pcm[i] = int16(pico * math.Sin(2*math.Pi*float64(i)/64))
	}
	return pcm
}

func TestDetectorAvisaSoNaTransicao(t *testing.T) {
	var d DetectorDeFala
	t0 := time.Now()

	if !d.Alimentar(quadroCom(0.2), t0) {
		t.Fatal("o primeiro quadro alto tinha de avisar que começou a falar")
	}
	if !d.Falando() {
		t.Fatal("depois de avisar, o estado tinha de ser falando")
	}

	for i := 1; i <= 50; i++ {
		if d.Alimentar(quadroCom(0.2), t0.Add(time.Duration(i)*20*time.Millisecond)) {
			t.Fatalf("quadro %d avisou de novo sem ter mudado nada", i)
		}
	}
}

func TestDetectorSeguraNaPausaEntrePalavras(t *testing.T) {
	var d DetectorDeFala
	t0 := time.Now()
	d.Alimentar(quadroCom(0.2), t0)

	if d.Alimentar(nil, t0.Add(200*time.Millisecond)) {
		t.Fatal("apagou no meio de uma pausa curta; o círculo piscaria a cada sílaba")
	}
	if !d.Falando() {
		t.Fatal("continuava falando, mas o estado disse que não")
	}

	if !d.Alimentar(nil, t0.Add(esperaAntesDeCalar+50*time.Millisecond)) {
		t.Fatal("não apagou depois da espera inteira de silêncio")
	}
	if d.Falando() {
		t.Fatal("avisou que parou mas continuou marcado como falando")
	}
}

func TestDetectorIgnoraRuidoDeFundo(t *testing.T) {
	var d DetectorDeFala
	t0 := time.Now()
	for i := 0; i < 50; i++ {
		if d.Alimentar(quadroCom(0.005), t0.Add(time.Duration(i)*20*time.Millisecond)) {
			t.Fatal("ruído de fundo acendeu o indicador de fala")
		}
	}
}

func TestCalarSoAvisaQuandoHaviaFala(t *testing.T) {
	var d DetectorDeFala
	if d.Calar() {
		t.Fatal("mandou apagar um indicador que já estava apagado")
	}
	d.Alimentar(quadroCom(0.2), time.Now())
	if !d.Calar() {
		t.Fatal("estava falando e o Calar não avisou")
	}
	if d.Calar() {
		t.Fatal("o segundo Calar avisou de novo")
	}
}

func TestNivelDeQuadroVazioEZero(t *testing.T) {
	if n := nivelDe(nil); n != 0 {
		t.Fatalf("quadro vazio deu nível %v", n)
	}
}
