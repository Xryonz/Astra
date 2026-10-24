package main

import (
	"fmt"
	"os"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	kernel32                    = windows.NewLazySystemDLL("kernel32.dll")
	procSetProcessInformation   = kernel32.NewProc("SetProcessInformation")
	procSetThreadExecutionState = kernel32.NewProc("SetThreadExecutionState")

	avrt                                = windows.NewLazySystemDLL("avrt.dll")
	procAvSetMmThreadCharacteristics    = avrt.NewProc("AvSetMmThreadCharacteristicsW")
	procAvRevertMmThreadCharacteristics = avrt.NewProc("AvRevertMmThreadCharacteristics")
)

const (
	processPowerThrottling  = 4
	versaoDoEstrangulamento = 1
	estrangularVelocidade   = 0x1

	esContinuo = 0x80000000
	esSistema  = 0x00000001
	esTela     = 0x00000002
)

type estadoDoEstrangulamento struct {
	Versao, Controle, Estado uint32
}

func definirEstrangulamento(controle, estado uint32) error {
	e := estadoDoEstrangulamento{Versao: versaoDoEstrangulamento, Controle: controle, Estado: estado}
	ok, _, err := procSetProcessInformation.Call(
		uintptr(windows.CurrentProcess()), processPowerThrottling,
		uintptr(unsafe.Pointer(&e)), unsafe.Sizeof(e))
	if ok == 0 {
		return err
	}
	return nil
}

func PrioridadeTotal(ligada bool) error {
	if ligada {
		return definirEstrangulamento(estrangularVelocidade, 0)
	}
	return definirEstrangulamento(0, 0)
}

func EntrarNoAgendadorDeAudio() func() {
	tarefa, err := windows.UTF16PtrFromString("Pro Audio")
	if err != nil {
		return func() {}
	}
	var indice uint32
	alca, _, err := procAvSetMmThreadCharacteristics.Call(uintptr(unsafe.Pointer(tarefa)), uintptr(unsafe.Pointer(&indice)))
	if alca == 0 {
		fmt.Fprintf(os.Stderr, "agendador de áudio do Windows: %v\n", err)
		return func() {}
	}
	return func() { procAvRevertMmThreadCharacteristics.Call(alca) }
}

type Vigilia struct{ pedidos chan bool }

func NovaVigilia() *Vigilia {
	v := &Vigilia{pedidos: make(chan bool, 1)}
	go v.segurar()
	return v
}

func (v *Vigilia) Manter(acordado bool) {
	select {
	case <-v.pedidos:
	default:
	}
	v.pedidos <- acordado
}

func (v *Vigilia) segurar() {
	runtime.LockOSThread()
	for acordado := range v.pedidos {
		estado := uintptr(esContinuo)
		if acordado {
			estado |= esSistema | esTela
		}
		if anterior, _, err := procSetThreadExecutionState.Call(estado); anterior == 0 {
			fmt.Fprintf(os.Stderr, "manter a tela acordada: %v\n", err)
		}
	}
}
