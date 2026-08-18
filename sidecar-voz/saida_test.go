package main

import (
	"errors"
	"math"
	"os"
	"testing"
	"time"
)

// TOCA UM TOM AUDÍVEL. É teste de verdade justamente por isso: som saindo pelo
// alto-falante é a única prova de que a ligação COM da saída está certa, e nenhuma
// verificação em código substitui ouvir.
//
//	$env:ASTRA_TESTE_SOM="1"; go test -run Saida -v ./...
func TestTocarTom(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_SOM") == "" {
		t.Skip("ASTRA_TESTE_SOM não definida — pulando (toca som de verdade)")
	}

	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	alto, err := AbrirSaida()
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
	// Logo após encher de silêncio, o espaço livre tem que ser pequeno. Se ele
	// vier igual à capacidade, o preenchimento inicial não escreveu nada.
	if livre == alto.capacidade {
		t.Error("o silêncio inicial não entrou — GetBuffer/ReleaseBuffer não está escrevendo")
	}

	// 440 Hz por meio segundo, baixinho. Baixo de propósito: quem roda o teste não
	// merece um susto no fone.
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
