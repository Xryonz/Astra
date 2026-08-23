package main

// QUANTA MEMÓRIA ISTO SEGURA — e a pergunta é sobre a memória que o Go NÃO vê.
//
// O perfil do Go mede o heap do Go. Quase nada do que este processo segura mora lá: as
// texturas são da placa, as amostras são objetos COM, os buffers do Media Foundation são
// alocados pelo Windows. Um `IMFSample` que deixa de ser solto não aparece em
// `runtime.MemStats` — aparece como a memória do processo subindo sem explicação, que é
// exatamente a caçada que já custou caro neste projeto.
//
// POR ISSO A MEDIDA É `PrivateUsage` E NÃO O CONJUNTO DE TRABALHO. O conjunto de
// trabalho é quanto está na RAM física AGORA, e o Windows o encolhe sozinho quando quer
// memória — ele desce sem nada ter sido liberado, e sobe sem nada ter vazado. O
// `PrivateUsage` é o quanto o processo pediu e ainda não devolveu, incluindo o que foi
// paginado para o disco. É o número que só sobe quando alguém esquece de soltar.

import (
	"unsafe"
)

// `K32GetProcessMemoryInfo` e não `GetProcessMemoryInfo`: é o mesmo, exportado direto
// pelo kernel32 desde o Windows 7. Evita carregar a psapi.dll só para uma consulta.
var procPegarMemoriaDoProc = kernel32.NewProc("K32GetProcessMemoryInfo")

// PROCESS_MEMORY_COUNTERS_EX. Em 64 bits são dois campos de 32 bits e nove ponteiros de
// tamanho — escrito à mão porque um campo a menos faz a função escrever fora da struct.
type contadoresDeMemoria struct {
	Tamanho               uint32
	_                     uint32 // PageFaultCount, e o alinhamento do primeiro SIZE_T
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

// MemoriaDoProcesso devolve quanto este processo pediu ao Windows e ainda não devolveu,
// em bytes. Zero quando a consulta falha.
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
