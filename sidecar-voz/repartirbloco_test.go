package main

import "testing"

func serie(de, ate int16) []int16 {
	q := make([]int16, 0, int(ate-de))
	for v := de; v < ate; v++ {
		q = append(q, v)
	}
	return q
}

func TestOBlocoQueCabeNaoDeixaSobra(t *testing.T) {
	destino := make([]int16, 8)
	n, sobra := repartirBloco(destino, serie(1, 6), nil)

	if n != 5 {
		t.Errorf("entregou %d amostras, esperava 5", n)
	}
	if len(sobra) != 0 {
		t.Errorf("guardou %d amostras à toa", len(sobra))
	}
}

func TestOBlocoGrandeDemaisNaoPerdeNadaNoCaminho(t *testing.T) {
	destino := make([]int16, 4)
	origem := serie(1, 11)

	n, sobra := repartirBloco(destino, origem, nil)
	if n != 4 {
		t.Fatalf("entregou %d amostras, esperava 4", n)
	}

	recolhido := append([]int16(nil), destino[:n]...)
	for len(sobra) > 0 {
		outro := make([]int16, 4)
		k := copy(outro, sobra)
		sobra = sobra[k:]
		recolhido = append(recolhido, outro[:k]...)
	}

	if len(recolhido) != len(origem) {
		t.Fatalf("saíram %d amostras de um bloco de %d: o resto foi descartado calado",
			len(recolhido), len(origem))
	}
	for i := range origem {
		if recolhido[i] != origem[i] {
			t.Fatalf("amostra %d saiu como %d, esperava %d — o bloco foi remontado fora de ordem",
				i, recolhido[i], origem[i])
		}
	}
}

func TestAReservaEhReaproveitada(t *testing.T) {
	destino := make([]int16, 2)

	_, sobra := repartirBloco(destino, serie(1, 9), nil)
	antes := cap(sobra)

	for volta := 0; volta < 20; volta++ {
		_, sobra = repartirBloco(destino, serie(1, 9), sobra)
	}

	if cap(sobra) != antes {
		t.Errorf("a reserva cresceu de %d para %d em 20 voltas: haveria alocação por bloco lido",
			antes, cap(sobra))
	}
}
