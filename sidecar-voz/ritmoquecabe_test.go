package main

import (
	"testing"
	"time"
)

const (
	trabalhoDeSobra  = 200 * time.Microsecond
	trabalhoApertado = 14 * time.Millisecond
)

func TestUmSegundoRuimNaoDerrubaAtaxa(t *testing.T) {
	r := NovoRitmoQueCabe(60)

	if _, mudou := r.Segundo(trabalhoApertado, 60); mudou {
		t.Error("um único segundo apertado já derrubou a taxa: um pico de carga viraria queda de qualidade")
	}
	if nova, mudou := r.Segundo(trabalhoApertado, 60); !mudou || nova != 30 {
		t.Errorf("dois segundos apertados deviam cair para 30/s, veio %d (mudou=%v)", nova, mudou)
	}
}

func TestOSegundoBomLimpaOAperto(t *testing.T) {
	r := NovoRitmoQueCabe(60)

	r.Segundo(trabalhoApertado, 60)
	if _, mudou := r.Segundo(trabalhoDeSobra, 60); mudou {
		t.Error("um segundo folgado no meio não podia mudar nada")
	}
	if _, mudou := r.Segundo(trabalhoApertado, 60); mudou {
		t.Error("o aperto anterior devia ter sido esquecido; a contagem recomeça")
	}
}

func TestSubirEMaisLentoQueDescer(t *testing.T) {
	r := NovoRitmoQueCabe(60)

	for volta := 1; volta < segundosParaSubirQuadros; volta++ {
		if _, mudou := r.Segundo(trabalhoDeSobra, 30); mudou {
			t.Fatalf("subiu na volta %d; devia esperar %d segundos limpos",
				volta, segundosParaSubirQuadros)
		}
	}
	if nova, mudou := r.Segundo(trabalhoDeSobra, 30); !mudou || nova != 60 {
		t.Errorf("depois de %d segundos limpos devia subir para 60/s, veio %d (mudou=%v)",
			segundosParaSubirQuadros, nova, mudou)
	}
}

func TestOTetoEscolhidoPeloDonoManda(t *testing.T) {
	r := NovoRitmoQueCabe(30)

	for volta := 0; volta < segundosParaSubirQuadros+5; volta++ {
		if nova, mudou := r.Segundo(trabalhoDeSobra, 30); mudou {
			t.Fatalf("subiu para %d/s na volta %d: quem escolheu 30 não pediu 60", nova, volta)
		}
	}
}

func TestOPisoDeTrintaSeguraMaquinaLenta(t *testing.T) {
	r := NovoRitmoQueCabe(60)

	nova := 60
	for volta := 0; volta < 20; volta++ {
		if proposta, mudou := r.Segundo(90*time.Millisecond, nova); mudou {
			nova = proposta
		}
	}
	if nova != 30 {
		t.Errorf("máquina que não aguenta nem 30/s parou em %d/s; o piso do dono é 30", nova)
	}
}
