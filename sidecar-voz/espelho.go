package main

import (
	"fmt"
	"os"
	"time"
	"unsafe"
)

const larguraDoEspelho = 320

const compassoDoEspelho = 125 * time.Millisecond

type Espelho struct {
	reduzir *Redimensionador
	l, a    int

	proximo  time.Time
	bytes    []byte
	desistiu bool

	entregar func(Quadro)
}

func AbrirEspelho(gerente objeto, deL, deA int, entregar func(Quadro)) (*Espelho, error) {
	if entregar == nil || deL <= 0 || deA <= 0 {
		return nil, nil
	}

	l := larguraDoEspelho
	if deL < l {

		l = deL
	}
	a := deA * l / deL
	l, a = l&^1, a&^1
	if l <= 0 || a <= 0 {
		return nil, nil
	}

	r, err := AbrirRedimensionador(gerente, deL, deA, l, a, formatoNV12)
	if err != nil {
		return nil, fmt.Errorf("abrir o espelho: %w", err)
	}
	return &Espelho{reduzir: r, l: l, a: a, entregar: entregar}, nil
}

func (e *Espelho) Talvez(amostra objeto) {
	if e == nil || e.desistiu || amostra == 0 {
		return
	}
	agora := time.Now()
	if agora.Before(e.proximo) {
		return
	}

	e.proximo = agora.Add(compassoDoEspelho)

	if err := e.passar(amostra); err != nil {

		fmt.Fprintf(os.Stderr, "espelho desligado: %v\n", err)
		e.desistiu = true
	}
}

func (e *Espelho) passar(amostra objeto) error {
	menor, err := e.reduzir.Reduzir(amostra)
	if err != nil {
		return err
	}
	if menor == 0 {
		return nil
	}
	defer menor.soltar()

	var buffer objeto
	if r := menor.chamar(amostraJuntarBuffers, uintptr(unsafe.Pointer(&buffer))); uint32(r)&0x80000000 != 0 {
		return hr(r, "juntar os pedaços do espelho")
	}
	defer buffer.soltar()

	var p uintptr
	var maximo, atual uint32
	r := buffer.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)),
		uintptr(unsafe.Pointer(&maximo)),
		uintptr(unsafe.Pointer(&atual)),
	)
	if err := hr(r, "abrir a miniatura para leitura"); err != nil {
		return err
	}

	esperado := e.l * e.a * 3 / 2
	if int(atual) != esperado {
		buffer.chamar(bufDestrancar)
		return fmt.Errorf("miniatura veio com %d bytes, esperava %d (%dx%d NV12)", atual, esperado, e.l, e.a)
	}

	if cap(e.bytes) < int(atual) {
		e.bytes = make([]byte, atual)
	}
	e.bytes = e.bytes[:atual]
	copy(e.bytes, unsafe.Slice((*byte)(unsafe.Pointer(p)), atual))
	buffer.chamar(bufDestrancar)

	e.entregar(Quadro{Dados: e.bytes, Largura: e.l, Altura: e.a, Passo: e.l})
	return nil
}

func (e *Espelho) Fechar() {
	if e == nil {
		return
	}
	e.reduzir.Fechar()
	e.reduzir = nil
}
