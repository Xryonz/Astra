package main

import (
	"fmt"
	"os"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

// SONDA DO CANCELADOR DE ECO — pergunta ao próprio objeto quais propriedades ele
// tem, em vez de confiar em constante copiada de algum lugar.
//
// As chaves MFPKEY_WMAAECMA_* não estão na documentação pública da Microsoft com os
// valores, só com os nomes. Copiar de um fórum é como se ganha um GUID errado que
// falha EM SILÊNCIO: a propriedade simplesmente não é reconhecida, o cancelador roda
// com a configuração padrão, e ninguém entende por que o eco continua.
//
// O objeto sabe. `IPropertyStore` enumera as próprias chaves e devolve os valores.
//
// O QUE ESTA SONDA JÁ ESTABELECEU (Windows 11, agosto de 2026):
//
//	conjunto = {6F52C567-0360-4BD2-9617-CCBF1421C939}, 28 propriedades
//
//	pid=2  I4   = 0     -> SYSTEM_MODE      (0 = SINGLE_CHANNEL_AEC)
//	pid=3  BOOL = true  -> DMO_SOURCE_MODE  (o modo fonte é o padrão, e é o que
//	                                         queremos: o DSP puxa do aparelho
//	                                         sozinho e não precisa ser alimentado)
//	pid=4  I4   = -1    -> DEVICE_INDEXES   (-1 = aparelhos padrão)
//	pid=5  BOOL = false -> FEATURE_MODE     (liga o ajuste fino das outras)
//
// A identificação NÃO é chute: cada uma casa com o tipo E com o valor padrão que a
// Microsoft documenta por nome, e as quatro caem em PIDs consecutivos na ordem em
// que a documentação as apresenta. Rodar esta sonda de novo é o jeito de conferir
// isso noutra máquina antes de culpar o código.
//
//	ASTRA_SONDA_ECO=1 go test -run SondaDoEco -v
func TestSondaDoEco(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_ECO") == "" {
		t.Skip("ASTRA_SONDA_ECO nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	// CLSID_CWMAudioAEC {745057c7-f353-4f2d-a7ee-58434477730e}
	// Conferido em DUAS fontes: o registro desta maquina (HKLM\...\CLSID, nome
	// "AEC") e o cabecalho wmcodecdsp.h do mingw.
	clsid := guid(0x745057C7, 0xF353, 0x4F2D,
		[8]byte{0xA7, 0xEE, 0x58, 0x43, 0x44, 0x77, 0x73, 0x0E})
	// IID_IPropertyStore {886d8eeb-8cf2-4446-8d02-cdba1dbdcf99}
	// Se este estiver errado, o CoCreateInstance devolve E_NOINTERFACE na hora —
	// ou seja, o proprio teste avisa em vez de seguir com lixo.
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
	// LER O VALOR DE CADA UMA, e não só o nome da chave.
	//
	// A enumeração dá os identificadores mas não os nomes. O tipo e o valor PADRÃO
	// de cada propriedade estão documentados por nome na Microsoft, então ler os
	// valores é o que permite casar um com o outro — é a diferença entre saber e
	// achar que sabe.
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

// Os poucos tipos de PROPVARIANT que este objeto usa.
func descreverValor(v propvariant) string {
	switch v.tipo {
	case 3: // VT_I4
		return fmt.Sprintf("I4    = %d", int32(uint32(v.ponteiro)))
	case 11: // VT_BOOL
		return fmt.Sprintf("BOOL  = %v", uint16(v.ponteiro) != 0)
	case 19: // VT_UI4
		return fmt.Sprintf("UI4   = %d", uint32(v.ponteiro))
	case 5: // VT_R8
		return fmt.Sprintf("R8    = %v", *(*float64)(unsafe.Pointer(&v.ponteiro)))
	case 0:
		return "VAZIO"
	default:
		return fmt.Sprintf("vt=%d bruto=%d", v.tipo, v.ponteiro)
	}
}
