package main

import (
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	kernel32              = windows.NewLazySystemDLL("kernel32.dll")
	procPegarTemposDoProc = kernel32.NewProc("GetProcessTimes")
	procProcessoAtual     = kernel32.NewProc("GetCurrentProcess")
)

func TempoDeProcessador() time.Duration {
	processo, _, _ := procProcessoAtual.Call()

	var criacao, saida, nucleo, usuario windows.Filetime
	r, _, _ := procPegarTemposDoProc.Call(
		processo,
		uintptr(unsafe.Pointer(&criacao)),
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&nucleo)),
		uintptr(unsafe.Pointer(&usuario)),
	)
	if r == 0 {
		return 0
	}
	return cemNanos(nucleo) + cemNanos(usuario)
}

func cemNanos(f windows.Filetime) time.Duration {
	return time.Duration(uint64(f.HighDateTime)<<32|uint64(f.LowDateTime)) * 100
}
