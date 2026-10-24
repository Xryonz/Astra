package main

import (
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

var chaveFalaD3D11 = guid(0x206B4FC8, 0xFCF9, 0x4C51,
	[8]byte{0xAF, 0xE3, 0x97, 0x64, 0x36, 0x9E, 0x33, 0xA0})

func (c CompressorDisponivel) FalaD3D11() (bool, error) {
	t, err := c.Montar()
	if err != nil {
		return false, err
	}
	defer t.soltar()

	var atributos objeto
	r := t.chamar(transPegarAtributos, uintptr(unsafe.Pointer(&atributos)))
	if err := hr(r, "ler os atributos do compressor"); err != nil {
		return false, err
	}
	defer atributos.soltar()

	return numeroDoAtributo(atributos, &chaveFalaD3D11) != 0, nil
}

func (c CompressorDisponivel) FormatosQueAceita() ([]string, error) {
	t, err := c.Montar()
	if err != nil {
		return nil, err
	}
	defer t.soltar()
	if err := destrancarSeAssincrono(t); err != nil {
		return nil, err
	}

	if err := configurarSaida(t, 1280, 720, 60, 4000); err != nil {
		return nil, err
	}

	formatos := []string{}
	for i := uint32(0); i < 64; i++ {
		var tipo objeto
		r := t.chamar(transTipoDeEntrada, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))

		if uint32(r)&0x80000000 != 0 {
			break
		}
		var sub windows.GUID
		if tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)),
			uintptr(unsafe.Pointer(&sub)),
		)&0x80000000 == 0 {
			formatos = append(formatos, nomeDoFormato(sub))
		}
		tipo.soltar()
	}
	return formatos, nil
}

func nomeDoFormato(g windows.GUID) string {
	molde := [8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71}
	if g.Data2 != 0x0000 || g.Data3 != 0x0010 || g.Data4 != molde {
		return g.String()
	}
	switch g.Data1 {
	case 21:
		return "ARGB32"
	case 22:
		return "RGB32"
	}

	quatro := [4]byte{byte(g.Data1), byte(g.Data1 >> 8), byte(g.Data1 >> 16), byte(g.Data1 >> 24)}
	for _, b := range quatro {
		if b < 0x20 || b > 0x7E {
			return fmt.Sprintf("formato %d", g.Data1)
		}
	}
	return string(quatro[:])
}

func RelatarCompressores() (string, error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err := abrirCOM(); err != nil {
		return "", err
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return "", err
	}
	defer fecharMF()

	lista, err := ProcurarCompressores()
	if err != nil {
		return "", err
	}
	defer SoltarCompressores(lista)

	if len(lista) == 0 {
		return "nenhum compressor de H.264 nesta máquina", nil
	}
	texto := ""
	for _, c := range lista {
		fala, err := c.FalaD3D11()
		switch {

		case err != nil:
			texto += fmt.Sprintf("%-46s não ligou: %v\n", c.Nome, err)
		case fala:
			texto += fmt.Sprintf("%-46s fala D3D11 (o quadro fica na placa)\n", c.Nome)
		default:
			texto += fmt.Sprintf("%-46s NÃO fala D3D11 (o quadro teria que descer)\n", c.Nome)
		}
	}
	return texto, nil
}
