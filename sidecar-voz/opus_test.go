package main

import (
	"math"
	"os"
	"testing"
)

// ESTE TESTE É A REDE DE SEGURANÇA DA LIGAÇÃO SEM CGO.
//
// Sem cgo não existe compilador conferindo assinatura: um tipo errado em `opus.go`
// não vira erro de compilação, vira memória corrompida em produção. O que substitui
// o compilador é isto — chamar a biblioteca de verdade e conferir que o que volta
// faz sentido.
//
// Roda com o caminho da DLL no ambiente:
//
//	$env:ASTRA_OPUS_DLL="C:\...\opus-0.dll"; go test ./...
//
// Sem a variável, o teste é PULADO em vez de falhar: numa máquina limpa, ou no
// runner de CI, a ausência da DLL não é defeito do código.
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

// Um quadro de 20ms a 48 kHz, mono: 960 amostras POR CANAL. É a duração padrão do
// WebRTC, e é o número que a conta inteira da malha assume.
const amostrasPorQuadro = 960

func ondaDeTeste() []int16 {
	pcm := make([]int16, amostrasPorQuadro)
	for i := range pcm {
		// 440 Hz a meio volume. Onda de verdade em vez de silêncio de propósito: com
		// silêncio o DTX devolveria um pacote mínimo, e um pacote mínimo passaria
		// tanto numa ligação certa quanto numa errada — o teste não provaria nada.
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
	// Som real tem que produzir um quadro de verdade. Um ou dois bytes seria o
	// pacote de silêncio do DTX, e aí algo estaria muito errado.
	if n < 10 {
		t.Fatalf("quadro pequeno demais para som real: %d bytes", n)
	}
	// A 24 kbps, 20ms cabem em ~60 bytes. Muito acima disso indica que os ajustes
	// não pegaram — que é justamente o tipo de falha silenciosa que este teste caça.
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

	// Não dá pra comparar amostra a amostra: Opus tem perda. O que dá pra afirmar é
	// que saiu SOM — se a ligação estivesse errada, o mais provável seria silêncio
	// ou lixo, e silêncio o teste pega aqui.
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

// Perda de pacote não pode derrubar nada: é o caminho mais percorrido de todos numa
// call de verdade, e é onde o FEC entra.
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
