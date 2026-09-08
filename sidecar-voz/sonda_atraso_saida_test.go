package main

import (
	"os"
	"testing"
	"time"
)

func TestSondaAtrasoDaSaida(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_SAIDA") == "" {
		t.Skip("ASTRA_SONDA_SAIDA nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("COM: %v", err)
	}
	defer fecharCOM()

	alto, err := AbrirSaida("")
	if err != nil {
		t.Fatalf("abrir alto-falante: %v", err)
	}
	defer alto.Fechar()

	emMs := func(quadros uint32) float64 {
		return float64(quadros) * 1000 / float64(TaxaDeAmostragem)
	}

	t.Logf("capacidade do buffer: %.1f ms", emMs(alto.capacidade))

	maior, menor := 0.0, 1e9
	secou := 0
	fim := time.Now().Add(5 * time.Second)
	for time.Now().Before(fim) {
		if err := alto.Esperar(200); err != nil {
			continue
		}
		enfileirado, err := alto.Enfileirado()
		if err != nil {
			t.Fatalf("consultar a saida: %v", err)
		}
		if enfileirado == 0 {
			secou++
		}
		if fila := emMs(enfileirado); fila > maior {
			maior = fila
		} else if fila < menor {
			menor = fila
		}
		for alto.capacidade-enfileirado >= AmostrasPorQuadro && enfileirado < QuadrosDeFolgaNaSaida {
			if err := alto.Silenciar(AmostrasPorQuadro); err != nil {
				t.Fatalf("escrever silencio: %v", err)
			}
			enfileirado += AmostrasPorQuadro
		}
	}

	t.Logf("fila enquanto ninguem fala: entre %.1f e %.1f ms", menor, maior)
	if secou > 0 {
		t.Errorf("a saida secou %d vezes — a folga de %d ms nao basta", secou, MilissegundosDeFolgaNaSaida)
	}
}
