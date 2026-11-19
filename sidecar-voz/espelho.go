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
	gerente  objeto
	deL, deA int
	saidaL   int
	cheio    func() bool

	reduzir        *Redimensionador
	l, a           int
	emTamanhoCheio bool

	proximo  time.Time
	desistiu bool

	entregar func(Quadro)
}

func AbrirEspelho(gerente objeto, deL, deA, saidaL int, cheio func() bool, entregar func(Quadro)) (*Espelho, error) {
	if entregar == nil || deL <= 0 || deA <= 0 {
		return nil, nil
	}
	e := &Espelho{gerente: gerente, deL: deL, deA: deA, saidaL: saidaL, cheio: cheio, entregar: entregar}
	if err := e.montar(false); err != nil {
		return nil, err
	}
	return e, nil
}

func (e *Espelho) montar(cheio bool) error {
	limite := larguraDoEspelho
	if cheio {
		limite = e.saidaL
	}
	l, a := tamanhoDoEspelho(e.deL, e.deA, limite)
	if l <= 0 || a <= 0 {
		return fmt.Errorf("espelho sem tamanho para %dx%d", e.deL, e.deA)
	}

	r, err := AbrirRedimensionador(e.gerente, e.deL, e.deA, l, a, formatoNV12)
	if err != nil {
		return fmt.Errorf("abrir o espelho em %dx%d: %w", l, a, err)
	}
	if e.reduzir != nil {
		e.reduzir.Fechar()
	}
	e.reduzir, e.l, e.a, e.emTamanhoCheio = r, l, a, cheio
	return nil
}

func tamanhoDoEspelho(deL, deA, limite int) (int, int) {
	l := min(limite, deL)
	a := deA * l / deL
	return l &^ 1, a &^ 1
}

func (e *Espelho) Talvez(amostra objeto) {
	if e == nil || e.desistiu || amostra == 0 {
		return
	}
	cheio := e.cheio != nil && e.cheio()
	agora := time.Now()
	if !cheio && agora.Before(e.proximo) {
		return
	}
	e.proximo = agora.Add(compassoDoEspelho)

	if cheio != e.emTamanhoCheio {
		if err := e.montar(cheio); err != nil {
			e.desistir(err)
			return
		}
	}
	if err := e.passar(amostra); err != nil {
		e.desistir(err)
	}
}

func (e *Espelho) desistir(err error) {
	fmt.Fprintf(os.Stderr, "espelho desligado: %v\n", err)
	e.desistiu = true
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
	defer buffer.chamar(bufDestrancar)

	esperado := e.l * e.a * 3 / 2
	if int(atual) != esperado {
		return fmt.Errorf("miniatura veio com %d bytes, esperava %d (%dx%d NV12)", atual, esperado, e.l, e.a)
	}

	e.entregar(Quadro{Dados: unsafe.Slice((*byte)(unsafe.Pointer(p)), atual), Largura: e.l, Altura: e.a, Passo: e.l})
	return nil
}

func (e *Espelho) Fechar() {
	if e == nil {
		return
	}
	e.reduzir.Fechar()
	e.reduzir = nil
}
