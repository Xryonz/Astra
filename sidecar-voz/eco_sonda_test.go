package main

import (
	"fmt"
	"os"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

func TestSondaDoEco(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_ECO") == "" {
		t.Skip("ASTRA_SONDA_ECO nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	clsid := guid(0x745057C7, 0xF353, 0x4F2D,
		[8]byte{0xA7, 0xEE, 0x58, 0x43, 0x44, 0x77, 0x73, 0x0E})

	iid := guid(0x886D8EEB, 0x8CF2, 0x4446,
		[8]byte{0x8D, 0x02, 0xCD, 0xBA, 0x1D, 0xBD, 0xCF, 0x99})

	loja, err := criar(&clsid, &iid)
	if err != nil {
		t.Fatalf("criar o cancelador de eco: %v", err)
	}
	defer loja.soltar()

	const psGetCount = 3
	const psGetAt = 4

	var quantas uint32
	if err := hr(loja.chamar(psGetCount, uintptr(unsafe.Pointer(&quantas))), "contar propriedades"); err != nil {
		t.Fatalf("%v", err)
	}
	t.Logf("o cancelador expoe %d propriedade(s)", quantas)

	type chave struct {
		conjunto windows.GUID
		id       uint32
	}

	for i := uint32(0); i < quantas; i++ {
		var k chave
		if hr(loja.chamar(psGetAt, uintptr(i), uintptr(unsafe.Pointer(&k))), "ler chave") != nil {
			continue
		}
		var v propvariant
		descricao := "(sem leitura)"
		if hr(loja.chamar(lojaLer, uintptr(unsafe.Pointer(&k)), uintptr(unsafe.Pointer(&v))), "ler valor") == nil {
			descricao = descreverValor(v)
		}
		t.Logf("  pid=%-3d tipo/valor: %s", k.id, descricao)
	}
}

func descreverValor(v propvariant) string {
	switch v.tipo {
	case 3:
		return fmt.Sprintf("I4    = %d", int32(uint32(v.ponteiro)))
	case 11:
		return fmt.Sprintf("BOOL  = %v", uint16(v.ponteiro) != 0)
	case 19:
		return fmt.Sprintf("UI4   = %d", uint32(v.ponteiro))
	case 5:
		return fmt.Sprintf("R8    = %v", *(*float64)(unsafe.Pointer(&v.ponteiro)))
	case 0:
		return "VAZIO"
	default:
		return fmt.Sprintf("vt=%d bruto=%d", v.tipo, v.ponteiro)
	}
}
