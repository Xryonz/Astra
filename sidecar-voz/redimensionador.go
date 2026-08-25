package main

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

var clsidVideoProcessor = guid(0x88753B26, 0x5B24, 0x49BD,
	[8]byte{0xB2, 0xE7, 0x0C, 0x44, 0x5C, 0x78, 0xC9, 0x82})

var iidIMFTransform = guid(0xBF94C121, 0x5B05, 0x4E6F,
	[8]byte{0x80, 0x00, 0xBA, 0x59, 0x89, 0x61, 0x41, 0x4D})

type Redimensionador struct {
	t objeto
}

func AbrirRedimensionador(gerente objeto, deL, deA, paraL, paraA int, formatoSaida windows.GUID) (*Redimensionador, error) {
	t, err := criar(&clsidVideoProcessor, &iidIMFTransform)
	if err != nil {
		return nil, fmt.Errorf("o Video Processor MFT não existe nesta máquina: %w", err)
	}
	r := &Redimensionador{t: t}

	pronto := false
	defer func() {
		if !pronto {
			r.Fechar()
		}
	}()

	if err := hr(t.chamar(transMandarRecado, recadoDefinirD3D, uintptr(gerente)),
		"dizer ao redimensionador qual é a placa"); err != nil {
		return nil, err
	}

	if err := r.definirLado(transDefinirEntrada, formatoARGB32, deL, deA, "entrada"); err != nil {
		return nil, err
	}
	if err := r.definirLado(transDefinirSaida, formatoSaida, paraL, paraA, "saída"); err != nil {
		return nil, err
	}

	t.chamar(transMandarRecado, recadoComecarFluxo, 0)
	t.chamar(transMandarRecado, recadoAbrirFluxo, 0)
	pronto = true
	return r, nil
}

func (r *Redimensionador) definirLado(indice int, formato windows.GUID, largura, altura int, qual string) error {
	var tipo objeto
	res, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(res, "criar o tipo da "+qual); err != nil {
		return err
	}
	defer tipo.soltar()

	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formato)
	definirNumero(tipo, &chaveEntrelacamento, progressivo)
	definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)

	return hr(r.t.chamar(indice, 0, uintptr(tipo), 0),
		fmt.Sprintf("amarrar a %s em %dx%d", qual, largura, altura))
}

func (r *Redimensionador) Reduzir(entrada objeto) (objeto, error) {
	if err := hr(r.t.chamar(transEntrarQuadro, 0, uintptr(entrada), 0),
		"entregar o quadro ao redimensionador"); err != nil {
		return 0, err
	}

	var saida saidaDoCompressor
	var estado uint32
	res := r.t.chamar(transSairQuadro, 0, 1,
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&estado)),
	)
	if uint32(res) == querMaisEntrada {

		return 0, nil
	}
	if err := hr(res, "pegar o quadro reduzido"); err != nil {
		return 0, err
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}
	return saida.Amostra, nil
}

func (r *Redimensionador) Fechar() {
	if r.t != 0 {
		r.t.chamar(transMandarRecado, recadoEncerrarFluxo, 0)
	}
	r.t.soltar()
	r.t = 0
}
