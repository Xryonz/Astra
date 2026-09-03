package main

import "testing"

func TestACamadaFinaEMetadeDaCheiaESempreDivisivelPorDois(t *testing.T) {
	casos := []struct {
		l, a         int
		querL, querA int
	}{
		{1920, 1080, 960, 540},
		{1280, 720, 640, 360},
		{1366, 768, 682, 384},
		{1919, 1079, 958, 538},
	}
	for _, c := range casos {
		l, a := tamanhoDaCamadaFina(c.l, c.a)
		if l != c.querL || a != c.querA {
			t.Errorf("%dx%d virou %dx%d, esperava %dx%d", c.l, c.a, l, a, c.querL, c.querA)
		}
		if l%2 != 0 || a%2 != 0 {
			t.Errorf("%dx%d virou %dx%d: H.264 em NV12 exige lado par", c.l, c.a, l, a)
		}
	}
}

func TestTelaMinusculaNaoViraCamadaDegenerada(t *testing.T) {
	l, a := tamanhoDaCamadaFina(2, 2)
	if l != 2 || a != 2 {
		t.Errorf("2x2 virou %dx%d: era para desistir de encolher, nao gerar quadro invalido", l, a)
	}
}
