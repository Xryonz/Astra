package main

import (
	"math"
	"runtime"
	"sync"
	"testing"
)

func TestMisturaSobConcorrencia(t *testing.T) {
	m := NovoMisturador()
	const pessoas = 8
	const rodadas = 500

	var entregadores sync.WaitGroup
	var puxador sync.WaitGroup
	parar := make(chan struct{})

	puxador.Add(1)
	go func() {
		defer puxador.Done()
		destino := make([]int16, AmostrasPorQuadro)
		for {
			select {
			case <-parar:
				return
			default:
				m.Puxar(destino)
				runtime.Gosched()
			}
		}
	}()

	for p := 0; p < pessoas; p++ {
		entregadores.Add(1)
		go func(id int) {
			defer entregadores.Done()
			quadro := make([]int16, AmostrasPorQuadro)
			for i := range quadro {
				quadro[i] = int16(1000 + id)
			}
			for r := 0; r < rodadas; r++ {
				m.Entregar(string(rune('a'+id)), quadro)
			}
		}(p)
	}

	entregadores.Add(1)
	go func() {
		defer entregadores.Done()
		for r := 0; r < rodadas; r++ {
			m.Esquecer("c")
		}
	}()

	entregadores.Wait()
	close(parar)
	puxador.Wait()
}

func TestEntregarCopiaOQuadro(t *testing.T) {
	m := NovoMisturador()
	reaproveitado := make([]int16, AmostrasPorQuadro)

	for i := range reaproveitado {
		reaproveitado[i] = 1000
	}
	m.Entregar("alguem", reaproveitado)

	for i := range reaproveitado {
		reaproveitado[i] = -9999
	}

	saida := make([]int16, AmostrasPorQuadro)
	if vozes := m.Puxar(saida); vozes != 1 {
		t.Fatalf("esperava 1 voz, veio %d", vozes)
	}
	if saida[0] != 1000 {
		t.Fatalf("o quadro foi guardado por referência: esperava 1000, veio %d", saida[0])
	}
}

func TestSomaAltaNaoEstoura(t *testing.T) {
	m := NovoMisturador()
	quadro := make([]int16, AmostrasPorQuadro)
	for i := range quadro {
		quadro[i] = 30000
	}
	for _, quem := range []string{"a", "b", "c"} {
		m.Entregar(quem, quadro)
	}

	saida := make([]int16, AmostrasPorQuadro)
	if vozes := m.Puxar(saida); vozes != 3 {
		t.Fatalf("esperava 3 vozes, veio %d", vozes)
	}
	for i, v := range saida {
		if v != 32767 {
			t.Fatalf("amostra %d deu %d — devia ter sido cortada no teto", i, v)
		}
	}
}

func TestFilaCheiaGuardaORecente(t *testing.T) {
	m := NovoMisturador()
	for n := 1; n <= folgaDeRajada+2; n++ {
		quadro := make([]int16, AmostrasPorQuadro)
		for i := range quadro {
			quadro[i] = int16(n * 100)
		}
		m.Entregar("alguem", quadro)
	}

	saida := make([]int16, AmostrasPorQuadro)
	m.Puxar(saida)

	esperado := int16(3 * 100)
	if saida[0] != esperado {
		t.Fatalf("primeiro quadro da fila deu %d, esperava %d", saida[0], esperado)
	}
}

func TestSemVozesDaSilencio(t *testing.T) {
	m := NovoMisturador()
	saida := make([]int16, AmostrasPorQuadro)
	for i := range saida {
		saida[i] = 12345
	}
	if vozes := m.Puxar(saida); vozes != 0 {
		t.Fatalf("esperava 0 vozes, veio %d", vozes)
	}
	var soma float64
	for _, v := range saida {
		soma += math.Abs(float64(v))
	}
	if soma != 0 {
		t.Fatal("o destino não foi zerado quando não havia ninguém falando")
	}
}

func picoDe(amostras []int16) int16 {
	var pico int16
	for _, a := range amostras {
		if a > pico {
			pico = a
		}
	}
	return pico
}

func entregarTom(m *Misturador, id string, valor int16) {
	quadro := make([]int16, AmostrasPorQuadro)
	for i := range quadro {
		quadro[i] = valor
	}
	m.Entregar(id, quadro)
}

func TestVolumePorPessoaSoAbaixaQuemFoiEscolhido(t *testing.T) {
	m := NovoMisturador()
	m.DefinirGanho("baixinho", 25)

	entregarTom(m, "baixinho", 1000)
	entregarTom(m, "normal", 1000)

	destino := make([]int16, AmostrasPorQuadro)
	if vozes := m.Puxar(destino); vozes != 2 {
		t.Fatalf("misturou %d vozes, esperava 2", vozes)
	}

	pico := picoDe(destino)
	if pico < 1200 || pico > 1300 {
		t.Errorf("pico %d: esperava perto de 1250 (1000 inteiro + 250 abafado)", pico)
	}
}

func TestVolumeZeroTiraAPessoaDaMisturaSemTravarAFila(t *testing.T) {
	m := NovoMisturador()
	m.DefinirGanho("calado", 0)

	entregarTom(m, "calado", 8000)
	destino := make([]int16, AmostrasPorQuadro)

	if vozes := m.Puxar(destino); vozes != 1 {
		t.Fatalf("puxou %d vozes, esperava 1 — o quadro precisa sair da fila mesmo mudo", vozes)
	}
	if pico := picoDe(destino); pico != 0 {
		t.Errorf("pico %d com volume zero, esperava silencio", pico)
	}
}

func TestVoltarPara100TiraOAbafamento(t *testing.T) {
	m := NovoMisturador()
	m.DefinirGanho("alguem", 10)
	m.DefinirGanho("alguem", 100)

	entregarTom(m, "alguem", 1000)
	destino := make([]int16, AmostrasPorQuadro)
	m.Puxar(destino)

	if pico := picoDe(destino); pico != 1000 {
		t.Errorf("pico %d depois de voltar a 100%%, esperava 1000", pico)
	}
}

func TestGanhoSobreviveAoSilencioQueApagaAVoz(t *testing.T) {
	m := NovoMisturador()
	m.DefinirGanho("some", 50)

	entregarTom(m, "some", 1000)
	destino := make([]int16, AmostrasPorQuadro)
	m.Puxar(destino)

	m.Esquecer("some")

	entregarTom(m, "some", 1000)
	m.Puxar(destino)
	if pico := picoDe(destino); pico < 480 || pico > 520 {
		t.Errorf("pico %d depois de a voz ser esquecida e voltar: o volume escolhido nao podia ter voltado a 100%%", pico)
	}
}

func TestSairDaSalaEsqueceOsVolumes(t *testing.T) {
	m := NovoMisturador()
	m.DefinirGanho("alguem", 20)
	m.EsquecerGanhos()

	entregarTom(m, "alguem", 1000)
	destino := make([]int16, AmostrasPorQuadro)
	m.Puxar(destino)

	if pico := picoDe(destino); pico != 1000 {
		t.Errorf("pico %d depois de esquecer os volumes, esperava 1000", pico)
	}
}

func TestEscutaAbafaTodoMundoDeUmaVez(t *testing.T) {
	m := NovoMisturador()
	m.DefinirEscuta(50)

	entregarTom(m, "um", 1000)
	entregarTom(m, "outro", 1000)

	destino := make([]int16, AmostrasPorQuadro)
	m.Puxar(destino)

	if pico := picoDe(destino); pico < 980 || pico > 1020 {
		t.Errorf("pico %d com a escuta em 50%%, esperava perto de 1000 (as duas vozes pela metade)", pico)
	}
}

func TestEscutaZeroDaSilencioSemPerderAContagem(t *testing.T) {
	m := NovoMisturador()
	m.DefinirEscuta(0)

	entregarTom(m, "alguem", 8000)
	destino := make([]int16, AmostrasPorQuadro)

	if vozes := m.Puxar(destino); vozes != 1 {
		t.Fatalf("puxou %d vozes, esperava 1", vozes)
	}
	if pico := picoDe(destino); pico != 0 {
		t.Errorf("pico %d com a escuta em zero, esperava silencio", pico)
	}
}

func TestEscutaSobreviveAoFimDaChamada(t *testing.T) {
	m := NovoMisturador()
	m.DefinirEscuta(50)
	m.EsquecerGanhos()

	entregarTom(m, "alguem", 1000)
	destino := make([]int16, AmostrasPorQuadro)
	m.Puxar(destino)

	if pico := picoDe(destino); pico < 480 || pico > 520 {
		t.Errorf("pico %d: a escuta e preferencia do app, nao podia morrer junto com os volumes da sala", pico)
	}
}
