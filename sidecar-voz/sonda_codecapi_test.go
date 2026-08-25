package main

import (
	"runtime"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

func TestSondaDoCodecAPI(t *testing.T) {
	precisaDeVideo(t)

	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		t.Fatalf("iniciar Media Foundation: %v", err)
	}
	defer fecharMF()

	lista, err := ProcurarCompressores()
	if err != nil {
		t.Fatalf("procurar compressores: %v", err)
	}
	defer SoltarCompressores(lista)
	if len(lista) == 0 {
		t.Skip("nenhum compressor de H.264 nesta máquina")
	}

	iidCodecAPI := guid(0x901DB4C7, 0x31CE, 0x41A2,
		[8]byte{0x85, 0xDC, 0x8F, 0xA0, 0xBF, 0x41, 0xB8, 0xDA})

	candidatos := []struct {
		nome  string
		chave windows.GUID
	}{

		{"ForceKeyFrame", guid(0x398C1B98, 0x8353, 0x475A,
			[8]byte{0x9E, 0xF2, 0x8F, 0x26, 0x5D, 0x26, 0x03, 0x45})},

		{"GOPSize", guid(0x95F31BE2, 0xFF9C, 0x4B9A,
			[8]byte{0x9F, 0x2B, 0x6B, 0xFB, 0xE8, 0xF0, 0xD5, 0xBA})},

		{"MeanBitRate", guid(0xF7222374, 0x2144, 0x4815,
			[8]byte{0xB5, 0x50, 0xA3, 0x7F, 0x8E, 0x12, 0xEE, 0x52})},
	}

	const (
		codecSuportado   = 3
		codecModificavel = 4
	)

	for _, cand := range lista {
		t.Run(cand.Nome, func(t *testing.T) {
			transformador, err := cand.Montar()
			if err != nil {
				t.Skipf("não liga: %v", err)
			}
			defer transformador.soltar()

			api, err := transformador.consultar(&iidCodecAPI)
			if err != nil {
				t.Logf("não expõe ICodecAPI: %v", err)
				return
			}
			defer api.soltar()
			t.Log("expõe ICodecAPI")

			for _, c := range candidatos {
				chave := c.chave
				rSup := api.chamar(codecSuportado, uintptr(unsafe.Pointer(&chave)))
				rMod := api.chamar(codecModificavel, uintptr(unsafe.Pointer(&chave)))
				t.Logf("  %-14s suportado=%s  modificavel=%s",
					c.nome, veredito(rSup), veredito(rMod))
			}

			mediaDeBanda := guid(0xF7222374, 0x2144, 0x4815,
				[8]byte{0xB5, 0x50, 0xA3, 0x7F, 0x8E, 0x12, 0xEE, 0x52})
			v := variante{tipo: varInteiroSemSinal, valor: 1_200_000}
			rSet := api.chamar(codecDefinirValor,
				uintptr(unsafe.Pointer(&mediaDeBanda)),
				uintptr(unsafe.Pointer(&v)),
			)
			t.Logf("  SetValue(MeanBitRate=1200 kbps) -> %s", veredito(rSet))
		})
	}
}

func veredito(r uintptr) string {
	if uint32(r) == 0 {
		return "SIM"
	}
	return "nao (" + hrTexto(uint32(r)) + ")"
}

func hrTexto(r uint32) string {
	switch r {
	case 0x80004002:
		return "E_NOINTERFACE"
	case 0x80070057:
		return "E_INVALIDARG"
	case 1:
		return "S_FALSE"
	}
	return "0x" + hex32(r)
}

func hex32(v uint32) string {
	const digitos = "0123456789ABCDEF"
	var b [8]byte
	for i := 7; i >= 0; i-- {
		b[i] = digitos[v&0xF]
		v >>= 4
	}
	return string(b[:])
}
