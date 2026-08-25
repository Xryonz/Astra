package main

import (
	"errors"
	"math"
	"os"
	"testing"
	"time"
)

func TestAbrirELerMicrofone(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_MIC") == "" {
		t.Skip("ASTRA_TESTE_MIC não definida — pulando (precisa de microfone real)")
	}

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

	if totalAmostras < 24000 {
		t.Errorf("só %d amostras em 1s (esperava ~48000) — taxa ou formato errados", totalAmostras)
	}

	rms := math.Sqrt(energia / float64(totalAmostras))
	t.Logf("%d amostras, rms %.0f, pico %d, %d buracos", totalAmostras, rms, pico, buracos)

	if pico == 0 {
		t.Error("silêncio perfeito o tempo todo — provável leitura de buffer não preenchido")
	}
}
