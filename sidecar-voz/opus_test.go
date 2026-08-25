package main

import (
	"math"
	"os"
	"testing"
)

func abrirParaTeste(t *testing.T) {
	t.Helper()
	caminho := os.Getenv("ASTRA_OPUS_DLL")
	if caminho == "" {
		t.Skip("ASTRA_OPUS_DLL não definida — pulando a conferência da ligação")
	}
	if err := AbrirOpus(caminho); err != nil {
		t.Fatalf("abrir opus: %v", err)
	}
}

const amostrasPorQuadro = 960

func ondaDeTeste() []int16 {
	pcm := make([]int16, amostrasPorQuadro)
	for i := range pcm {

		pcm[i] = int16(math.Sin(2*math.Pi*440*float64(i)/48000) * 16000)
	}
	return pcm
}

func TestCodificarEDecodificar(t *testing.T) {
	abrirParaTeste(t)

	cod, err := NovoCodificador(48000, 1)
	if err != nil {
		t.Fatalf("criar codificador: %v", err)
	}
	defer cod.Fechar()

	pcm := ondaDeTeste()
	saida := make([]byte, 4000)
	n, err := cod.Codificar(pcm, saida)
	if err != nil {
		t.Fatalf("codificar: %v", err)
	}

	if n < 10 {
		t.Fatalf("quadro pequeno demais para som real: %d bytes", n)
	}

	if n > 200 {
		t.Errorf("quadro de %d bytes: os ajustes de bitrate podem não ter pegado", n)
	}
	t.Logf("quadro de 20ms -> %d bytes (~%d kbps)", n, n*8*50/1000)

	dec, err := NovoDecodificador(48000, 1)
	if err != nil {
		t.Fatalf("criar decodificador: %v", err)
	}
	defer dec.Fechar()

	volta := make([]int16, amostrasPorQuadro)
	lidos, err := dec.Decodificar(saida[:n], volta, false)
	if err != nil {
		t.Fatalf("decodificar: %v", err)
	}
	if lidos != amostrasPorQuadro {
		t.Fatalf("decodificou %d amostras, esperava %d", lidos, amostrasPorQuadro)
	}

	var energia float64
	for _, v := range volta {
		energia += float64(v) * float64(v)
	}
	rms := math.Sqrt(energia / float64(len(volta)))
	if rms < 1000 {
		t.Fatalf("voltou quase silêncio (rms %.0f) — a ligação com a DLL está errada", rms)
	}
	t.Logf("rms de volta: %.0f", rms)
}

func TestPerdaDePacote(t *testing.T) {
	abrirParaTeste(t)

	dec, err := NovoDecodificador(48000, 1)
	if err != nil {
		t.Fatalf("criar decodificador: %v", err)
	}
	defer dec.Fechar()

	pcm := make([]int16, amostrasPorQuadro)
	lidos, err := dec.Decodificar(nil, pcm, false)
	if err != nil {
		t.Fatalf("avisar perda: %v", err)
	}
	if lidos != amostrasPorQuadro {
		t.Fatalf("recuperação devolveu %d amostras, esperava %d", lidos, amostrasPorQuadro)
	}
}
