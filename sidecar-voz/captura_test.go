package main

import (
	"errors"
	"math"
	"os"
	"testing"
	"time"
)

// CONFERÊNCIA DA LIGAÇÃO COM O WASAPI.
//
// Vale o mesmo que foi dito no teste do Opus, e aqui vale mais: são chamadas COM
// escritas à mão, e um índice de vtable errado não dá erro de compilação — chama a
// função errada e trava. Este teste é o que separa "compila" de "funciona".
//
// Precisa de microfone de verdade, então só roda quando pedido:
//
//	$env:ASTRA_TESTE_MIC="1"; go test -run Captura -v ./...
func TestAbrirELerMicrofone(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_MIC") == "" {
		t.Skip("ASTRA_TESTE_MIC não definida — pulando (precisa de microfone real)")
	}

	// As duas linhas mais importantes do arquivo. COM é preso à thread, e o Go
	// move goroutine entre threads quando quer. Sem prender, isto passa no teste e
	// falha em produção.
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	mic, err := AbrirCaptura("")
	if err != nil {
		t.Fatalf("abrir microfone: %v", err)
	}
	defer mic.Fechar()

	buf := make([]int16, AmostrasPorQuadro*4)
	var totalAmostras int
	var energia float64
	var pico int16
	var buracos int

	// Um segundo de escuta. Suficiente para provar que o laço de evento gira e que
	// o buffer circular não desalinha — desalinhamento aparece rápido.
	fim := time.Now().Add(time.Second)
	for time.Now().Before(fim) {
		if err := mic.Esperar(100); err != nil {
			if errors.Is(err, ErrSemAudio) {
				continue
			}
			t.Fatalf("esperar: %v", err)
		}
		for {
			n, buraco, err := mic.Ler(buf)
			if errors.Is(err, ErrSemAudio) {
				break
			}
			if err != nil {
				t.Fatalf("ler: %v", err)
			}
			if buraco {
				buracos++
			}
			for _, v := range buf[:n] {
				energia += float64(v) * float64(v)
				if v > pico {
					pico = v
				}
			}
			totalAmostras += n
		}
	}

	if totalAmostras == 0 {
		t.Fatal("nenhuma amostra em um segundo — o laço de evento não está girando")
	}

	// A 48 kHz, um segundo deveria render perto de 48000 amostras. Aceito uma folga
	// larga porque o teste começa e termina no meio de um bloco; o que este limite
	// pega de verdade é ligação errada, que produz ordens de grandeza a menos.
	if totalAmostras < 24000 {
		t.Errorf("só %d amostras em 1s (esperava ~48000) — taxa ou formato errados", totalAmostras)
	}

	rms := math.Sqrt(energia / float64(totalAmostras))
	t.Logf("%d amostras, rms %.0f, pico %d, %d buracos", totalAmostras, rms, pico, buracos)

	// Silêncio absoluto em TODAS as amostras é suspeito: mesmo microfone mudo capta
	// ruído de fundo. Zero perfeito costuma significar que estamos lendo um buffer
	// que nunca foi escrito.
	if pico == 0 {
		t.Error("silêncio perfeito o tempo todo — provável leitura de buffer não preenchido")
	}
}
