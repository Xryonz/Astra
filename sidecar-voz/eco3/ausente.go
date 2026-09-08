//go:build !aec3

package eco3

import "errors"

const MilissegundosPorQuadro = 10

var ErrIndisponivel = errors.New("cancelador de eco nao foi compilado neste binario")

type Cancelador struct{}

func Novo(taxa, canais int) (*Cancelador, error) { return nil, ErrIndisponivel }

func (c *Cancelador) Fechar() {}

func (c *Cancelador) AmostrasPorQuadro() int { return 0 }

func (c *Cancelador) Ajustar(ruido, ganho bool) error { return ErrIndisponivel }

func (c *Cancelador) Referencia(quadro []int16) error { return ErrIndisponivel }

func (c *Cancelador) Capturar(quadro []int16, atrasoMs int) error { return ErrIndisponivel }
