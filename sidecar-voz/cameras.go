package main

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	mfplatCam = windows.NewLazySystemDLL("mfplat.dll")
	mfDll     = windows.NewLazySystemDLL("mf.dll")

	procCriarAtributos  = mfplatCam.NewProc("MFCreateAttributes")
	procListarAparelhos = mfDll.NewProc("MFEnumDeviceSources")
)

var (
	chaveTipoDaFonte = guid(0xC60AC5FE, 0x252A, 0x478F,
		[8]byte{0xA0, 0xEF, 0xBC, 0x8F, 0xA5, 0xF7, 0xCA, 0xD3})

	tipoFonteDeVideo = guid(0x8AC3587A, 0x4AE7, 0x42D8,
		[8]byte{0x99, 0xE0, 0x0A, 0x60, 0x13, 0xEE, 0xF9, 0x0F})

	chaveNomeDaCamera = guid(0x60D0E559, 0x52F8, 0x4FA2,
		[8]byte{0xBB, 0xCE, 0xAC, 0xDB, 0x34, 0xA8, 0xEC, 0x01})

	chaveLinkSimbolico = guid(0x58F0AAD8, 0x22BF, 0x4F8A,
		[8]byte{0xBB, 0x3D, 0xD2, 0xC4, 0x97, 0x8C, 0x6E, 0x2F})
)

type CameraDaMaquina struct {
	Id   string `json:"id"`
	Nome string `json:"nome"`
}

func ListarCameras() ([]CameraDaMaquina, error) {
	var atributos objeto
	r, _, _ := procCriarAtributos.Call(uintptr(unsafe.Pointer(&atributos)), 1)
	if err := hr(r, "criar os atributos da busca por câmeras"); err != nil {
		return nil, err
	}
	defer atributos.soltar()

	definirGUID(atributos, &chaveTipoDaFonte, tipoFonteDeVideo)

	var lista *objeto
	var quantas uint32
	r, _, _ = procListarAparelhos.Call(
		uintptr(atributos),
		uintptr(unsafe.Pointer(&lista)),
		uintptr(unsafe.Pointer(&quantas)),
	)
	if err := hr(r, "listar as câmeras"); err != nil {
		return nil, err
	}
	if quantas == 0 {
		return nil, nil
	}
	defer liberarMemoriaDoCOM(uintptr(unsafe.Pointer(lista)))

	achadas := make([]CameraDaMaquina, 0, quantas)
	for _, fonte := range unsafe.Slice(lista, quantas) {
		nome := textoDoAtributo(fonte, &chaveNomeDaCamera)
		id := textoDoAtributo(fonte, &chaveLinkSimbolico)
		if id != "" {
			achadas = append(achadas, CameraDaMaquina{Id: id, Nome: nome})
		}
		fonte.soltar()
	}
	return achadas, nil
}

func RelatarCameras() (string, error) {
	lista, err := ListarCameras()
	if err != nil {
		return "", err
	}
	if len(lista) == 0 {
		return "nenhuma câmera nesta máquina", nil
	}
	texto := ""
	for _, c := range lista {
		texto += fmt.Sprintf("%s\n  %s\n", c.Nome, c.Id)
	}
	return texto, nil
}
