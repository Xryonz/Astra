//go:build aec3

package eco3

/*
#cgo CXXFLAGS: -std=c++17
#include "eco3.h"
*/
import "C"

import (
	"errors"
	"unsafe"
)

const MilissegundosPorQuadro = 10

var ErrIndisponivel = errors.New("cancelador de eco indisponivel")

type Cancelador struct {
	handle          *C.AstraEco
	amostrasPorQuadro int
}

func Novo(taxa, canais int) (*Cancelador, error) {
	h := C.astra_eco_criar(C.int(taxa), C.int(canais))
	if h == nil {
		return nil, ErrIndisponivel
	}
	return &Cancelador{
		handle:            h,
		amostrasPorQuadro: taxa * MilissegundosPorQuadro / 1000 * canais,
	}, nil
}

func (c *Cancelador) Fechar() {
	if c == nil || c.handle == nil {
		return
	}
	C.astra_eco_destruir(c.handle)
	c.handle = nil
}

func (c *Cancelador) AmostrasPorQuadro() int { return c.amostrasPorQuadro }

func (c *Cancelador) Ajustar(ruido, ganho bool) error {
	if c == nil || c.handle == nil {
		return ErrIndisponivel
	}
	comoInt := func(b bool) C.int {
		if b {
			return 1
		}
		return 0
	}
	if C.astra_eco_ajustar(c.handle, comoInt(ruido), comoInt(ganho)) != 0 {
		return errors.New("cancelador recusou os ajustes")
	}
	return nil
}

func (c *Cancelador) Referencia(quadro []int16) error {
	if c == nil || c.handle == nil {
		return ErrIndisponivel
	}
	if len(quadro) != c.amostrasPorQuadro {
		return errors.New("quadro de referencia com tamanho errado")
	}
	r := C.astra_eco_referencia(c.handle, (*C.int16_t)(unsafe.Pointer(&quadro[0])), C.int(len(quadro)))
	if r != 0 {
		return errors.New("cancelador recusou a referencia")
	}
	return nil
}

func (c *Cancelador) Capturar(quadro []int16, atrasoMs int) error {
	if c == nil || c.handle == nil {
		return ErrIndisponivel
	}
	if len(quadro) != c.amostrasPorQuadro {
		return errors.New("quadro de captura com tamanho errado")
	}
	r := C.astra_eco_capturar(c.handle, (*C.int16_t)(unsafe.Pointer(&quadro[0])), C.int(len(quadro)), C.int(atrasoMs))
	if r != 0 {
		return errors.New("cancelador recusou a captura")
	}
	return nil
}
