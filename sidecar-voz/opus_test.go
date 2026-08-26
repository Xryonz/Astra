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

	if n > 400 {
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

func ondaLarga(fase int) []int16 {
	pcm := make([]int16, amostrasPorQuadro)
	for i := range pcm {
		t := float64(fase*amostrasPorQuadro+i) / 48000
		v := math.Sin(2*math.Pi*300*t)*0.5 +
			math.Sin(2*math.Pi*5000*t)*0.3 +
			math.Sin(2*math.Pi*15000*t)*0.2
		pcm[i] = int16(v * 12000)
	}
	return pcm
}

func TestAVozSobeEmBandaCheia(t *testing.T) {
	abrirParaTeste(t)

	cod, err := NovoCodificador(48000, 1)
	if err != nil {
		t.Fatalf("criar codificador: %v", err)
	}
	defer cod.Fechar()

	taxa, err := cod.consultar(4003)
	if err != nil {
		t.Fatalf("consultar bitrate: %v", err)
	}
	if taxa != 64000 {
		t.Errorf("bitrate configurado = %d, esperava 64000", taxa)
	}

	saida := make([]byte, 4000)
	bytes := 0
	for fase := 0; fase < 25; fase++ {
		n, err := cod.Codificar(ondaLarga(fase), saida)
		if err != nil {
			t.Fatalf("codificar quadro %d: %v", fase, err)
		}
		if fase >= 5 {
			bytes += n
		}
	}

	banda, err := cod.consultar(ctlGetBandwidth)
	if err != nil {
		t.Fatalf("consultar banda: %v", err)
	}

	nomes := map[int]string{1101: "estreita 4kHz", 1102: "média 6kHz", 1103: "larga 8kHz",
		1104: "super-larga 12kHz", 1105: "cheia 20kHz"}
	t.Logf("banda escolhida: %d (%s)", banda, nomes[banda])
	t.Logf("20 quadros em regime: %d bytes (~%d kbps)", bytes, bytes*8*50/20/1000)

	if banda < 1104 {
		t.Errorf("o codificador ficou em %d (%s); com 64 kbps e o teto liberado ele deveria passar de 12kHz",
			banda, nomes[banda])
	}
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
