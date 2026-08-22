package main

// AS DUAS REGRAS QUE PROTEGEM A MÁQUINA FRACA, e nenhuma delas precisa de placa para ser
// provada: são contas puras. Ficam num arquivo só porque respondem à mesma pergunta —
// "o que esta máquina aguenta?" — por dois lados: quantos quadros por segundo, e de que
// tamanho.

import (
	"testing"
	"time"
)

func TestTaxaQueCabe(t *testing.T) {
	// A regra: o quadro tem de caber em METADE do orçamento da taxa. A outra metade é
	// da captura, do empacotamento em RTP, da rede e do resto do aplicativo desenhando
	// a própria janela na mesma máquina.
	casos := []struct {
		nome     string
		custo    time.Duration
		teto     int
		esperado int
	}{
		{"caminho de placa nesta máquina", 940 * time.Microsecond, 60, 60},
		{"caminho de software nesta máquina", 4530 * time.Microsecond, 60, 60},
		{"bem no limite de 60/s", 8333 * time.Microsecond, 60, 60},
		{"um fio acima de 60/s", 8400 * time.Microsecond, 60, 30},
		{"máquina três vezes mais lenta", 14 * time.Millisecond, 60, 30},
		{"máquina seis vezes mais lenta", 27 * time.Millisecond, 60, 15},
		{"máquina que não aguenta nem 15/s", 90 * time.Millisecond, 60, 15},

		// O PRESET SÓ ABAIXA, NUNCA SOBE. Quem escolheu 30 quer 30, mesmo numa máquina
		// que daria 60 sobrando — o preset é escolha da pessoa, não um alvo a superar.
		{"máquina rápida com preset de 30", 940 * time.Microsecond, 30, 30},
		{"máquina lenta com preset de 30", 27 * time.Millisecond, 30, 15},
		{"preset de 15 continua 15", 940 * time.Microsecond, 15, 15},

		// Área de trabalho parada: não houve quadro para medir. Tela parada não é
		// máquina em apuros, e abaixar a taxa por falta de medição seria punir o caso
		// mais comum de quem compartilha um documento.
		{"sem medição nenhuma", 0, 60, 60},
	}
	for _, c := range casos {
		t.Run(c.nome, func(t *testing.T) {
			if got := TaxaQueCabe(c.custo, c.teto); got != c.esperado {
				t.Errorf("custo %v, teto %d: esperava %d/s, veio %d/s",
					c.custo, c.teto, c.esperado, got)
			}
		})
	}
}

func TestTetoDeSoftware(t *testing.T) {
	casos := []struct {
		nome         string
		l, a         int
		querL, querA int
	}{
		{"1080p cai para 720p", 1920, 1080, 1280, 720},
		{"720p passa intacto", 1280, 720, 1280, 720},
		{"menor que 720p não sobe", 854, 480, 854, 480},
		{"1440p cai para 720p", 2560, 1440, 1280, 720},
		{"ultrawide mantém a proporção", 3440, 1440, 1720, 720},
	}
	for _, c := range casos {
		t.Run(c.nome, func(t *testing.T) {
			l, a := tetoDeSoftware(c.l, c.a)
			if l != c.querL || a != c.querA {
				t.Errorf("%dx%d virou %dx%d, esperava %dx%d", c.l, c.a, l, a, c.querL, c.querA)
			}
			// DIMENSÃO ÍMPAR QUEBRA O H.264 em silêncio: a cor mora em blocos de dois
			// por dois pixels, e o estrago aparece como faixa errada na borda, não como
			// erro. Vale conferir sempre, não só nos casos redondos.
			if l%2 != 0 || a%2 != 0 {
				t.Errorf("%dx%d tem dimensão ímpar", l, a)
			}
		})
	}
}
