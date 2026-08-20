package main

// QUANTO DE PROCESSADOR ISTO CUSTA — a pergunta da máquina fraca.
//
// Tempo de relógio não responde. O cano gasta 4,4ms por quadro, mas quase tudo é
// ESPERAR a placa terminar, e esperar não queima processador: a thread fica parada num
// evento do Windows. Uma máquina fraca não sofre com espera, sofre com trabalho.
//
// Daí esta medida. É a mesma régua da decisão que originou a migração inteira, e que
// está guardada no histórico do projeto:
//
//	quadro fica na GPU do começo ao fim   0,07 núcleos
//	quadro desce pra CPU + encoder de HW  0,68
//	quadro desce pra CPU + software       0,84
//
// Sem reproduzir esse número no cano novo, "está rápido" é fé. Com ele, dá para dizer
// a quem tem um computador de escritório se a transmissão vai roubar o processador do
// resto do app — que é o que decide se a call fica utilizável ou não.

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

// TempoDeProcessador devolve quanto processador este processo já consumiu, somando o
// tempo em código nosso e o tempo dentro do núcleo do Windows.
//
// Os dois juntos, e não só o de usuário, porque a maior parte do trabalho aqui
// acontece do outro lado de uma chamada de sistema — driver de vídeo, Media Foundation,
// cópia de memória. Contar só o tempo de usuário diria que a transmissão é gratuita.
func TempoDeProcessador() time.Duration {
	processo, _, _ := procProcessoAtual.Call()

	// FILETIME: contagem de intervalos de 100 nanossegundos, em duas metades de 32
	// bits. Os dois primeiros (criação e saída) não interessam, mas precisam existir
	// — a função escreve nos quatro, e passar menos corrompe a pilha.
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
