//go:build aec3

package eco3

import (
	"math/rand"
	"testing"
)

func ruidoBranco(r *rand.Rand, amostras int, ganho float64) []int16 {
	q := make([]int16, amostras)
	for i := range q {
		q[i] = int16(r.NormFloat64() * ganho)
	}
	return q
}

func energiaDepoisDe(t *testing.T, ruido bool) float64 {
	t.Helper()

	c, err := Novo(taxaCheia, umCanal)
	if err != nil {
		t.Fatalf("nao criou: %v", err)
	}
	defer c.Fechar()

	if err := c.Ajustar(ruido, false); err != nil {
		t.Fatalf("nao aceitou os ajustes: %v", err)
	}

	r := rand.New(rand.NewSource(7))
	silencio := make([]int16, c.AmostrasPorQuadro())

	var ultima float64
	for i := 0; i < voltas; i++ {
		if err := c.Referencia(silencio); err != nil {
			t.Fatalf("referencia falhou: %v", err)
		}
		quadro := ruidoBranco(r, c.AmostrasPorQuadro(), 2500)
		if err := c.Capturar(quadro, 20); err != nil {
			t.Fatalf("captura falhou: %v", err)
		}
		ultima = energia(quadro)
	}
	return ultima
}

func TestSupressaoDeRuidoObedeceAEscolha(t *testing.T) {
	ligada := energiaDepoisDe(t, true)
	desligada := energiaDepoisDe(t, false)

	t.Logf("ruido residual: supressao ligada %.0f · desligada %.0f", ligada, desligada)

	if ligada >= desligada {
		t.Errorf("ligar a supressao de ruido nao mudou nada (%.0f contra %.0f): "+
			"a escolha do dono nao estaria chegando no cancelador", ligada, desligada)
	}
}
