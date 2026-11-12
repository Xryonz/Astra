package main

import "testing"

func TestOTamanhoDoEspelhoSegueOLimiteSemAmpliar(t *testing.T) {
	casos := []struct {
		deL, deA, limite int
		l, a             int
	}{
		{1920, 1080, larguraDoEspelho, 320, 180},
		{1366, 768, larguraDoEspelho, 320, 178},
		{2560, 1440, 1920, 1920, 1080},
		{1280, 720, 1920, 1280, 720},
		{1001, 501, 1920, 1000, 500},
	}
	for _, c := range casos {
		l, a := tamanhoDoEspelho(c.deL, c.deA, c.limite)
		if l != c.l || a != c.a {
			t.Errorf("%dx%d até %d: deu %dx%d, esperava %dx%d", c.deL, c.deA, c.limite, l, a, c.l, c.a)
		}
	}
}
