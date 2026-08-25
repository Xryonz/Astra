package main

import (
	"unsafe"
)

var procPegarMemoriaDoProc = kernel32.NewProc("K32GetProcessMemoryInfo")

type contadoresDeMemoria struct {
	Tamanho               uint32
	_                     uint32
	PicoDoConjunto        uintptr
	ConjuntoDeTrabalho    uintptr
	PicoDaCotaPaginada    uintptr
	CotaPaginada          uintptr
	PicoDaCotaNaoPaginada uintptr
	CotaNaoPaginada       uintptr
	UsoDoArquivoDePagina  uintptr
	PicoDoArquivoDePagina uintptr
	UsoPrivado            uintptr
}

func MemoriaDoProcesso() uint64 {
	processo, _, _ := procProcessoAtual.Call()

	var c contadoresDeMemoria
	c.Tamanho = uint32(unsafe.Sizeof(c))
	r, _, _ := procPegarMemoriaDoProc.Call(
		processo,
		uintptr(unsafe.Pointer(&c)),
		uintptr(c.Tamanho),
	)
	if r == 0 {
		return 0
	}
	return uint64(c.UsoPrivado)
}
