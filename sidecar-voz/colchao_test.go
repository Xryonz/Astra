package main

import (
	"testing"
	"time"
)

func vozDe(t *testing.T, m *Misturador, id string) *vozRecebida {
	t.Helper()
	m.mu.Lock()
	defer m.mu.Unlock()
	v, ok := m.vozes[id]
	if !ok {
		t.Fatalf("a voz %q sumiu do misturador", id)
	}
	return v
}

func quadroDeTeste(valor int16) []int16 {
	pcm := make([]int16, AmostrasPorQuadro)
	for i := range pcm {
		pcm[i] = valor
	}
	return pcm
}

func TestFluxoEmPassoCertoNaoInflaOColchao(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	for r := 0; r < 200; r++ {
		m.Entregar("alguem", quadro)
		if vozes := m.Puxar(saida); vozes != 1 {
			t.Fatalf("rodada %d: esperava 1 voz, veio %d", r, vozes)
		}
	}

	if a := vozDe(t, m, "alguem").alvo; a != 0 {
		t.Errorf("um fluxo que nunca falhou inflou o colchão para %d quadros", a)
	}
}

func TestOColchaoAbsorveOEngasgoDepoisDoPrimeiro(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	const rodadas = 300
	const aCadaEngasgo = 25

	buracos, ultimoBuraco, engasgos := 0, -1, 0
	atrasado := false

	for r := 0; r < rodadas; r++ {
		switch {
		case r > 0 && r%aCadaEngasgo == 0:
			atrasado = true
			engasgos++
		case atrasado:
			m.Entregar("alguem", quadro)
			m.Entregar("alguem", quadro)
			atrasado = false
		default:
			m.Entregar("alguem", quadro)
		}

		if m.Puxar(saida) == 0 {
			buracos++
			ultimoBuraco = r
		}
	}

	v := vozDe(t, m, "alguem")
	t.Logf("%d engasgos injetados · colchão parou em %d quadros · %d buracos, o último na rodada %d",
		engasgos, v.alvo, buracos, ultimoBuraco)

	if v.alvo == 0 {
		t.Error("houve engasgo e o colchão não cresceu")
	}
	if buracos > 2 {
		t.Errorf("%d buracos em %d engasgos: o colchão devia engatar no primeiro e segurar o resto",
			buracos, engasgos)
	}
	if ultimoBuraco > aCadaEngasgo+2 {
		t.Errorf("ainda houve buraco na rodada %d, depois do primeiro engasgo (rodada %d)",
			ultimoBuraco, aCadaEngasgo)
	}
}

func TestOColchaoQueEstaTrabalhandoNaoEDesmontado(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	rodadas := pulosParaAcalmar + 400
	const aCadaEngasgo = 25

	buracos, ultimoBuraco := 0, -1
	atrasado := false

	for r := 0; r < rodadas; r++ {
		switch {
		case r > 0 && r%aCadaEngasgo == 0:
			atrasado = true
		case atrasado:
			m.Entregar("alguem", quadro)
			m.Entregar("alguem", quadro)
			atrasado = false
		default:
			m.Entregar("alguem", quadro)
		}

		if m.Puxar(saida) == 0 {
			buracos++
			ultimoBuraco = r
		}
	}

	v := vozDe(t, m, "alguem")
	t.Logf("%d rodadas (acalmar em %d) · colchão em %d · %d buracos, o último na rodada %d",
		rodadas, pulosParaAcalmar, v.alvo, buracos, ultimoBuraco)

	if v.alvo == 0 {
		t.Error("a calmaria desmontou um colchão que estava segurando engasgo a cada 25 quadros")
	}
	if buracos > 2 {
		t.Errorf("%d buracos: o colchão foi encolhido no meio do serviço e teve de crescer de novo", buracos)
	}
}

func TestPausaDeFalaNaoInflaOColchao(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	m.Entregar("alguem", quadro)
	m.Puxar(saida)
	m.Puxar(saida)

	time.Sleep(paradaQueNaoEJitter + 50*time.Millisecond)

	m.Entregar("alguem", quadro)

	if a := vozDe(t, m, "alguem").alvo; a != 0 {
		t.Errorf("a pausa entre frases foi lida como engasgo de rede: colchão foi para %d", a)
	}
}

func TestSilencioDeDtxNaoInflaOColchao(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	m.Entregar("alguem", quadro)
	m.Puxar(saida)

	for r := 0; r < 100; r++ {
		m.Entregar("alguem", nil)
		m.Puxar(saida)
	}

	if a := vozDe(t, m, "alguem").alvo; a != 0 {
		t.Errorf("o silêncio de DTX inflou o colchão para %d quadros: "+
			"cada respiro na fala viraria latência acumulada", a)
	}
}

func TestVozCaladaContinuaViva(t *testing.T) {
	m := NovoMisturador()
	saida := make([]int16, AmostrasPorQuadro)

	m.Entregar("alguem", quadroDeTeste(5000))
	m.Puxar(saida)

	for r := 0; r < 50; r++ {
		m.Entregar("alguem", nil)
		m.Puxar(saida)
	}

	vozDe(t, m, "alguem")
}

func TestOColchaoEncolheNaCalmaria(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	m.Entregar("alguem", quadro)
	m.Puxar(saida)
	m.Puxar(saida)
	m.Entregar("alguem", quadro)

	subiu := vozDe(t, m, "alguem").alvo
	if subiu == 0 {
		t.Fatal("o engasgo não fez o colchão crescer; nada para encolher")
	}

	for r := 0; r < pulosParaAcalmar+50; r++ {
		m.Entregar("alguem", quadro)
		m.Entregar("alguem", quadro)
		m.Puxar(saida)
	}

	desceu := vozDe(t, m, "alguem").alvo
	t.Logf("colchão: subiu para %d, desceu para %d", subiu, desceu)

	if desceu >= subiu {
		t.Errorf("depois de %d puxadas limpas o colchão continuou em %d", pulosParaAcalmar, desceu)
	}
}

func TestOColchaoTemTeto(t *testing.T) {
	m := NovoMisturador()
	quadro := quadroDeTeste(5000)
	saida := make([]int16, AmostrasPorQuadro)

	for r := 0; r < folgaMaxima*10; r++ {
		m.Entregar("alguem", quadro)
		for m.Puxar(saida) != 0 {
		}
		m.Puxar(saida)
	}

	if a := vozDe(t, m, "alguem").alvo; a > folgaMaxima {
		t.Errorf("o colchão passou do teto: %d > %d", a, folgaMaxima)
	} else {
		t.Logf("colchão travou em %d quadros (%d ms)", a, a*MilissegundosPorQuadro)
	}
}
