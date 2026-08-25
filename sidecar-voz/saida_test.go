package main

import (
	"errors"
	"math"
	"os"
	"testing"
	"time"
)

func TestTocarTom(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_SOM") == "" {
		t.Skip("ASTRA_TESTE_SOM não definida — pulando (toca som de verdade)")
	}

	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	alto, err := AbrirSaida("")
	if err != nil {
		t.Fatalf("abrir saída: %v", err)
	}
	defer alto.Fechar()

	livre, err := alto.EspacoLivre()
	if err != nil {
		t.Fatalf("espaço livre: %v", err)
	}
	t.Logf("buffer do aparelho: %d quadros (%.0f ms)",
		alto.capacidade, float64(alto.capacidade)*1000/TaxaDeAmostragem)
	if alto.capacidade == 0 {
		t.Fatal("buffer de tamanho zero — a consulta ao aparelho falhou")
	}

	if livre == alto.capacidade {
		t.Error("o silêncio inicial não entrou — GetBuffer/ReleaseBuffer não está escrevendo")
	}

	const dur = 500 * time.Millisecond
	fase := 0.0
	passo := 2 * math.Pi * 440 / TaxaDeAmostragem
	quadro := make([]int16, AmostrasPorQuadro)

	fim := time.Now().Add(dur)
	escritas := 0
	for time.Now().Before(fim) {
		if err := alto.Esperar(200); err != nil {
			if errors.Is(err, ErrSemAudio) {
				t.Fatal("a saída parou de pedir material — o aviso por evento não está chegando")
			}
			t.Fatalf("esperar: %v", err)
		}
		for i := range quadro {
			quadro[i] = int16(math.Sin(fase) * 3000)
			fase += passo
		}
		if err := alto.Escrever(quadro); err != nil {
			t.Fatalf("escrever: %v", err)
		}
		escritas++
	}

	if escritas < 5 {
		t.Errorf("só %d escritas em meio segundo — o laço de saída não está girando", escritas)
	}
	t.Logf("%d blocos entregues", escritas)
}
