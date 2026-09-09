package main

import "testing"

func entregaDeTeste(vagas int) *EntregaDeQuadros {
	e := &EntregaDeQuadros{
		fila:  make(chan *quadroPronto, vagas),
		parar: make(chan struct{}),
	}
	e.pool.New = func() any { return &quadroPronto{} }
	return e
}

func quadroDeImagem(valor byte, bytes int) Quadro {
	dados := make([]byte, bytes)
	for i := range dados {
		dados[i] = valor
	}
	return Quadro{Dados: dados, Largura: 4, Altura: 2, Passo: 4}
}

func TestOQuadroEhCopiadoENaoEmprestado(t *testing.T) {
	e := entregaDeTeste(2)

	imagem := quadroDeImagem(7, 8)
	e.Mandar("alguem", imagem)

	for i := range imagem.Dados {
		imagem.Dados[i] = 0
	}

	p := <-e.fila
	for i, b := range p.bytes {
		if b != 7 {
			t.Fatalf("byte %d do quadro na fila virou %d: o quadro foi guardado por referência "+
				"e o dono do buffer já reescreveu por cima", i, b)
		}
	}
}

func TestFilaCheiaNaoPagaACopia(t *testing.T) {
	e := entregaDeTeste(1)

	e.Mandar("alguem", quadroDeImagem(1, 8))
	if len(e.fila) != 1 {
		t.Fatalf("a fila devia ter 1 quadro, tem %d", len(e.fila))
	}

	antes := e.pool.Get().(*quadroPronto)
	e.pool.Put(antes)

	e.Mandar("alguem", quadroDeImagem(2, 8))

	if len(e.fila) != 1 {
		t.Errorf("a fila cheia aceitou mais um quadro: virou %d", len(e.fila))
	}

	p := <-e.fila
	if p.bytes[0] != 1 {
		t.Errorf("o quadro guardado virou o %d: a fila cheia devia descartar o novo, "+
			"não trocar o que já estava esperando", p.bytes[0])
	}
}
