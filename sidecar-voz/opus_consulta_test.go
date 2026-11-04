package main

import (
	"fmt"
	"unsafe"
)

func (c *Codificador) consultar(pedido int) (int, error) {
	var valor int32
	r, _, _ := procControlar.Call(c.st, uintptr(pedido), uintptr(unsafe.Pointer(&valor)))
	if int32(r) != opusOK {
		return 0, fmt.Errorf("opus_encoder_ctl(%d) devolveu %d", pedido, int32(r))
	}
	return int(valor), nil
}
