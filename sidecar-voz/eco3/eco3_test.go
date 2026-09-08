//go:build aec3

package eco3

import (
	"math"
	"testing"
)

const (
	taxaCheia = 48000
	umCanal   = 1
	voltas    = 60
)

func tomDe(amostras int, ganho float64) []int16 {
	q := make([]int16, amostras)
	for i := range q {
		q[i] = int16(math.Sin(2*math.Pi*440*float64(i)/float64(taxaCheia)) * ganho)
	}
	return q
}

func energia(q []int16) float64 {
	soma := 0.0
	for _, v := range q {
		soma += float64(v) * float64(v)
	}
	return math.Sqrt(soma / float64(len(q)))
}

func TestCancelaOEcoNaTaxaCheia(t *testing.T) {
	c, err := Novo(taxaCheia, umCanal)
	if err != nil {
		t.Fatalf("nao criou: %v", err)
	}
	defer c.Fechar()

	referencia := tomDe(c.AmostrasPorQuadro(), 8000)
	vazado := tomDe(c.AmostrasPorQuadro(), 5600)
	antes := energia(vazado)

	var depois float64
	for i := 0; i < voltas; i++ {
		if err := c.Referencia(append([]int16(nil), referencia...)); err != nil {
			t.Fatalf("referencia falhou na volta %d: %v", i, err)
		}
		quadro := append([]int16(nil), vazado...)
		if err := c.Capturar(quadro, 20); err != nil {
			t.Fatalf("captura falhou na volta %d: %v", i, err)
		}
		depois = energia(quadro)
	}

	if depois >= antes/10 {
		t.Fatalf("o eco nao foi cancelado: antes %.0f, depois %.0f", antes, depois)
	}
}

func TestQuadroDoTamanhoErradoEhRecusado(t *testing.T) {
	c, err := Novo(taxaCheia, umCanal)
	if err != nil {
		t.Fatalf("nao criou: %v", err)
	}
	defer c.Fechar()

	if err := c.Capturar(make([]int16, 7), 20); err == nil {
		t.Fatal("aceitou quadro de tamanho errado")
	}
}
