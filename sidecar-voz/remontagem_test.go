package main

import (
	"math"
	"testing"
)

func rms(pcm []int16) float64 {
	if len(pcm) == 0 {
		return 0
	}
	var energia float64
	for _, v := range pcm {
		energia += float64(v) * float64(v)
	}
	return math.Sqrt(energia / float64(len(pcm)))
}

func parecidas(a, b []int16) float64 {
	n := len(a)
	if len(b) < n {
		n = len(b)
	}
	if n == 0 {
		return 0
	}
	var junto, ea, eb float64
	for i := 0; i < n; i++ {
		x, y := float64(a[i]), float64(b[i])
		junto += x * y
		ea += x * x
		eb += y * y
	}
	if ea == 0 || eb == 0 {
		return 0
	}
	return junto / math.Sqrt(ea*eb)
}

func ondaImprevisivel(quadros int) [][]int16 {
	fatias := make([][]int16, quadros)
	fase := 0.0
	for q := range fatias {
		frequencia := 260 + 130*float64((q*7)%11)
		amplitude := 6000 + 2500*float64((q*5)%7)
		pcm := make([]int16, AmostrasPorQuadro)
		for i := range pcm {
			fase += 2 * math.Pi * frequencia / 48000
			pcm[i] = int16(math.Sin(fase) * amplitude)
		}
		fatias[q] = pcm
	}
	return fatias
}

func codificar(t *testing.T, fatias [][]int16) [][]byte {
	t.Helper()

	cod, err := NovoCodificador(48000, 1)
	if err != nil {
		t.Fatalf("criar codificador: %v", err)
	}
	defer cod.Fechar()

	pacotes := make([][]byte, len(fatias))
	saida := make([]byte, 4000)
	for i, pcm := range fatias {
		n, err := cod.Codificar(pcm, saida)
		if err != nil {
			t.Fatalf("codificar quadro %d: %v", i, err)
		}
		pacotes[i] = append([]byte(nil), saida[:n]...)
	}
	return pacotes
}

func quadrosCodificados(t *testing.T, quantos int) [][]byte {
	t.Helper()
	return codificar(t, ondaImprevisivel(quantos))
}

func remontadorDeTeste(t *testing.T) (*RemontadorDeVoz, func()) {
	t.Helper()
	dec, err := NovoDecodificador(48000, 1)
	if err != nil {
		t.Fatalf("criar decodificador: %v", err)
	}
	return NovoRemontadorDeVoz(dec), dec.Fechar
}

type colhidos struct {
	quadros [][]int16
}

func (c *colhidos) juntar(pcm []int16) {
	c.quadros = append(c.quadros, append([]int16(nil), pcm...))
}

func TestRemontagemEntregaNaOrdemDeChegada(t *testing.T) {
	abrirParaTeste(t)

	pacotes := quadrosCodificados(t, 4)
	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	for i, p := range pacotes {
		r.Entregar(uint16(100+i), p, c.juntar)
	}

	if len(c.quadros) != 4 {
		t.Fatalf("saíram %d quadros, esperava 4", len(c.quadros))
	}
	if r.Houve() {
		t.Errorf("sem perda nenhuma, mas o remontador mexeu em alguma coisa: %+v", r)
	}
}

func TestRemontagemDesfazTrocaDeVizinhos(t *testing.T) {
	abrirParaTeste(t)

	pacotes := quadrosCodificados(t, 4)
	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	r.Entregar(100, pacotes[0], c.juntar)
	r.Entregar(102, pacotes[2], c.juntar)
	r.Entregar(101, pacotes[1], c.juntar)
	r.Entregar(103, pacotes[3], c.juntar)

	if len(c.quadros) != 4 {
		t.Fatalf("saíram %d quadros, esperava 4", len(c.quadros))
	}
	if r.Reordenados != 1 {
		t.Errorf("reordenados = %d, esperava 1", r.Reordenados)
	}
	if r.TapadosComVizinho+r.TapadosNoEscuro+r.Atrasados != 0 {
		t.Errorf("a troca de vizinhos foi tratada como perda: %+v", r)
	}
}

func TestOFECDevolveOAudioCertoENaoUmPalpite(t *testing.T) {
	abrirParaTeste(t)

	const perdido = 3
	original := ondaImprevisivel(8)
	pacotes := codificar(t, original)

	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	for i, p := range pacotes {
		if i == perdido {
			continue
		}
		r.Entregar(uint16(100+i), p, c.juntar)
	}
	r.Escoar(c.juntar)

	if r.TapadosComVizinho != 1 || r.TapadosNoEscuro != 0 {
		t.Fatalf("o buraco não foi tapado pelo caminho do vizinho: %+v", r)
	}
	if len(c.quadros) != len(pacotes) {
		t.Fatalf("saíram %d quadros, esperava %d", len(c.quadros), len(pacotes))
	}

	tapado := c.quadros[perdido]
	semelhanca := parecidas(tapado, original[perdido])

	t.Logf("quadro %d perdido e reconstruído:", perdido)
	t.Logf("  semelhança com o original: %+.3f", semelhanca)
	t.Logf("  rms reconstruído %.0f · rms original %.0f", rms(tapado), rms(original[perdido]))

	for _, outro := range []int{perdido - 1, perdido + 1} {
		t.Logf("  semelhança com o quadro %d (o que a ocultação copiaria): %+.3f",
			outro, parecidas(tapado, original[outro]))
	}

	if semelhanca < 0.30 {
		t.Errorf("o quadro reconstruído não se parece com o que foi perdido (%.3f): "+
			"o FEC não entrou e isto virou palpite do decodificador", semelhanca)
	}
}

func TestRemontagemTapaPerdaDuplaComOcultacao(t *testing.T) {
	abrirParaTeste(t)

	pacotes := quadrosCodificados(t, 8)
	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	for i, p := range pacotes {
		if i == 3 || i == 4 {
			continue
		}
		r.Entregar(uint16(100+i), p, c.juntar)
	}
	r.Escoar(c.juntar)

	if r.TapadosComVizinho+r.TapadosNoEscuro != 2 {
		t.Fatalf("dois buracos, mas tapou %d vezes: %+v",
			r.TapadosComVizinho+r.TapadosNoEscuro, r)
	}
	if r.TapadosNoEscuro != 1 {
		t.Errorf("no escuro = %d, esperava 1: o primeiro dos dois perdidos não tem vizinho seguinte",
			r.TapadosNoEscuro)
	}
	if len(c.quadros) != 8 {
		t.Errorf("saíram %d quadros, esperava 8", len(c.quadros))
	}
}

func TestRemontagemDescartaOQueChegouTarde(t *testing.T) {
	abrirParaTeste(t)

	pacotes := quadrosCodificados(t, 6)
	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	r.Entregar(100, pacotes[0], c.juntar)
	r.Entregar(102, pacotes[2], c.juntar)
	r.Entregar(103, pacotes[3], c.juntar)
	r.Entregar(104, pacotes[4], c.juntar)
	r.Entregar(101, pacotes[1], c.juntar)

	if r.Atrasados != 1 {
		t.Errorf("atrasados = %d, esperava 1", r.Atrasados)
	}
}

func TestRemontagemRessincronizaEmSaltoGrande(t *testing.T) {
	abrirParaTeste(t)

	pacotes := quadrosCodificados(t, 4)
	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	r.Entregar(100, pacotes[0], c.juntar)
	r.Entregar(40000, pacotes[1], c.juntar)
	r.Entregar(40001, pacotes[2], c.juntar)

	if r.Ressincronizados != 1 {
		t.Fatalf("ressincronizados = %d, esperava 1", r.Ressincronizados)
	}
	if r.TapadosNoEscuro+r.TapadosComVizinho > 2 {
		t.Errorf("tapou %d buracos num salto de sequência: deveria ressincronizar, não inventar 39900 quadros",
			r.TapadosNoEscuro+r.TapadosComVizinho)
	}
	if len(c.quadros) != 3 {
		t.Errorf("saíram %d quadros, esperava 3", len(c.quadros))
	}
}

func TestRemontagemAtravessaAViradaDaSequencia(t *testing.T) {
	abrirParaTeste(t)

	pacotes := quadrosCodificados(t, 5)
	r, fechar := remontadorDeTeste(t)
	defer fechar()

	var c colhidos
	inicio := uint16(65533)
	for i, p := range pacotes {
		r.Entregar(inicio+uint16(i), p, c.juntar)
	}

	if len(c.quadros) != 5 {
		t.Fatalf("saíram %d quadros, esperava 5", len(c.quadros))
	}
	if r.Houve() {
		t.Errorf("a virada de 65535 para 0 foi lida como perda: %+v", r)
	}
}
