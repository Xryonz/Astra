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
	for n := 1; n <= quadrosDeFolga+2; n++ {
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
