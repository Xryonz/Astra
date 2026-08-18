package main

import (
	"math"
	"runtime"
	"sync"
	"testing"
)

// O misturador é a peça mais disputada do processo: uma goroutine por pessoa
// entregando voz, mais a goroutine da saída puxando, todas ao mesmo tempo. Estes
// testes existem para rodar sob `-race`, que é o que transforma uma corrida
// silenciosa em falha visível.

// Corrida de verdade: várias pessoas entregando enquanto a saída puxa sem parar.
func TestMisturaSobConcorrencia(t *testing.T) {
	m := NovoMisturador()
	const pessoas = 8
	const rodadas = 500

	// DOIS GRUPOS DE ESPERA, e não um.
	//
	// Na primeira versão a goroutine que puxa estava no MESMO grupo das que
	// entregam. Como ela só para depois do `close(parar)`, e o `close` só acontece
	// depois do `Wait()`, o `Wait()` esperava por alguém que esperava por ele —
	// impasse por construção. O teste travou por nove minutos até o tempo estourar.
	//
	// Separar deixa a ordem óbvia: espera quem entrega terminar, manda a saída
	// parar, e só então espera por ela.
	var entregadores sync.WaitGroup
	var puxador sync.WaitGroup
	parar := make(chan struct{})

	// A saída puxa sem parar, como no app — mas CEDENDO a vez a cada volta.
	//
	// Sem o `Gosched`, este laço vira uma espera ocupada que pega o cadeado, larga
	// e pega de novo sem intervalo, e as goroutinas que entregam nunca conseguem
	// entrar. Não é hipótese: a primeira versão deste teste travou por nove
	// minutos exatamente assim.
	//
	// No app real isso não acontece porque a saída dorme esperando o aviso do
	// aparelho (~10ms entre voltas). O `Gosched` aqui reproduz esse respiro sem
	// precisar de relógio, e mantém o teste rápido.
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

	// Sair no meio também é concorrência: acontece toda vez que alguém desliga.
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

// O QUADRO ENTREGUE NÃO PODE SER GUARDADO POR REFERÊNCIA.
//
// Quem entrega reaproveita o próprio buffer no quadro seguinte — é o que o laço de
// recepção faz. Se o misturador guardasse a fatia em vez de copiar, a fila inteira
// apontaria para a mesma memória e o som viraria o último quadro repetido N vezes.
// Este teste falha exatamente nesse caso.
func TestEntregarCopiaOQuadro(t *testing.T) {
	m := NovoMisturador()
	reaproveitado := make([]int16, AmostrasPorQuadro)

	for i := range reaproveitado {
		reaproveitado[i] = 1000
	}
	m.Entregar("alguem", reaproveitado)

	// Quem chamou mexe no próprio buffer, como faria no quadro seguinte.
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

// Somar vozes altas não pode dar a volta e virar negativo. Sem o corte, duas ondas
// perto do limite produzem um rangido — o valor estoura os 16 bits e troca de
// sinal, que soa como rádio quebrado e não como volume alto.
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

// Fila cheia mantém o RECENTE e descarta o antigo. Áudio velho não vale nada numa
// conversa ao vivo, e guardar o antigo empurraria a call para o passado.
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
	// Com folga de 3 e 5 entregas, o mais antigo que sobrou é o terceiro.
	esperado := int16(3 * 100)
	if saida[0] != esperado {
		t.Fatalf("primeiro quadro da fila deu %d, esperava %d", saida[0], esperado)
	}
}

// Silêncio de verdade quando não há ninguém: o laço de saída depende disso para
// saber que pode avisar "silêncio" em vez de escrever um buffer inteiro de zeros.
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
