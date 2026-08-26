package main

import (
	"errors"
	"fmt"
	"syscall"
	"unsafe"
)

const (
	opusOK = 0

	appVoIP = 2048

	ctlSetBitrate      = 4002
	ctlSetMaxBandwidth = 4004
	ctlGetBandwidth    = 4009
	ctlSetComplexity   = 4010
	ctlSetInbandFEC    = 4012
	ctlSetPacketLoss   = 4014
	ctlSetDTX          = 4016
	ctlSetSignal       = 4024

	bandaCheia = 1105
	sinalDeVoz = 3001
)

var (
	dll *syscall.LazyDLL

	procCriarCodificador      *syscall.LazyProc
	procControlar             *syscall.LazyProc
	procCodificar             *syscall.LazyProc
	procDestruirCodificador   *syscall.LazyProc
	procCriarDecodificador    *syscall.LazyProc
	procDecodificar           *syscall.LazyProc
	procDestruirDecodificador *syscall.LazyProc
)

func AbrirOpus(caminho string) error {
	dll = syscall.NewLazyDLL(caminho)
	if err := dll.Load(); err != nil {
		return fmt.Errorf("carregar %s: %w", caminho, err)
	}

	procCriarCodificador = dll.NewProc("opus_encoder_create")
	procControlar = dll.NewProc("opus_encoder_ctl")
	procCodificar = dll.NewProc("opus_encode")
	procDestruirCodificador = dll.NewProc("opus_encoder_destroy")
	procCriarDecodificador = dll.NewProc("opus_decoder_create")
	procDecodificar = dll.NewProc("opus_decode")
	procDestruirDecodificador = dll.NewProc("opus_decoder_destroy")

	for nome, p := range map[string]*syscall.LazyProc{
		"opus_encoder_create":  procCriarCodificador,
		"opus_encoder_ctl":     procControlar,
		"opus_encode":          procCodificar,
		"opus_encoder_destroy": procDestruirCodificador,
		"opus_decoder_create":  procCriarDecodificador,
		"opus_decode":          procDecodificar,
		"opus_decoder_destroy": procDestruirDecodificador,
	} {
		if err := p.Find(); err != nil {
			return fmt.Errorf("símbolo %s ausente em %s: %w", nome, caminho, err)
		}
	}
	return nil
}

type Codificador struct {
	st uintptr
}

func NovoCodificador(taxa, canais int) (*Codificador, error) {
	var errC int32
	st, _, _ := procCriarCodificador.Call(
		uintptr(taxa),
		uintptr(canais),
		uintptr(appVoIP),
		uintptr(unsafe.Pointer(&errC)),
	)
	if errC != opusOK || st == 0 {
		return nil, fmt.Errorf("opus_encoder_create devolveu %d", errC)
	}

	c := &Codificador{st: st}

	ajustes := []struct {
		pedido int
		valor  int
		nome   string
	}{
		{ctlSetBitrate, 64000, "bitrate"},
		{ctlSetMaxBandwidth, bandaCheia, "banda máxima"},
		{ctlSetSignal, sinalDeVoz, "tipo de sinal"},
		{ctlSetDTX, 1, "DTX"},
		{ctlSetInbandFEC, 1, "FEC embutido"},
		{ctlSetPacketLoss, 10, "perda esperada"},
		{ctlSetComplexity, 9, "complexidade"},
	}
	for _, a := range ajustes {
		if err := c.controlar(a.pedido, a.valor); err != nil {
			c.Fechar()
			return nil, fmt.Errorf("ajustar %s: %w", a.nome, err)
		}
	}
	return c, nil
}

func (c *Codificador) controlar(pedido, valor int) error {
	r, _, _ := procControlar.Call(c.st, uintptr(pedido), uintptr(valor))
	if int32(r) != opusOK {
		return fmt.Errorf("opus_encoder_ctl(%d) devolveu %d", pedido, int32(r))
	}
	return nil
}

func (c *Codificador) consultar(pedido int) (int, error) {
	var valor int32
	r, _, _ := procControlar.Call(c.st, uintptr(pedido), uintptr(unsafe.Pointer(&valor)))
	if int32(r) != opusOK {
		return 0, fmt.Errorf("opus_encoder_ctl(%d) devolveu %d", pedido, int32(r))
	}
	return int(valor), nil
}

func (c *Codificador) Codificar(pcm []int16, saida []byte) (int, error) {
	if len(pcm) == 0 {
		return 0, errors.New("quadro de áudio vazio")
	}
	n, _, _ := procCodificar.Call(
		c.st,
		uintptr(unsafe.Pointer(&pcm[0])),
		uintptr(len(pcm)),
		uintptr(unsafe.Pointer(&saida[0])),
		uintptr(len(saida)),
	)
	escritos := int32(n)
	if escritos < 0 {
		return 0, fmt.Errorf("opus_encode devolveu %d", escritos)
	}
	return int(escritos), nil
}

func (c *Codificador) Fechar() {
	if c.st == 0 {
		return
	}
	procDestruirCodificador.Call(c.st)
	c.st = 0
}

type Decodificador struct {
	st uintptr
}

func NovoDecodificador(taxa, canais int) (*Decodificador, error) {
	var errC int32
	st, _, _ := procCriarDecodificador.Call(
		uintptr(taxa),
		uintptr(canais),
		uintptr(unsafe.Pointer(&errC)),
	)
	if errC != opusOK || st == 0 {
		return nil, fmt.Errorf("opus_decoder_create devolveu %d", errC)
	}
	return &Decodificador{st: st}, nil
}

func (d *Decodificador) Decodificar(dados []byte, pcm []int16, recuperando bool) (int, error) {
	var ptr uintptr
	if len(dados) > 0 {
		ptr = uintptr(unsafe.Pointer(&dados[0]))
	}
	fec := uintptr(0)
	if recuperando {
		fec = 1
	}
	n, _, _ := procDecodificar.Call(
		d.st,
		ptr,
		uintptr(len(dados)),
		uintptr(unsafe.Pointer(&pcm[0])),
		uintptr(len(pcm)),
		fec,
	)
	lidos := int32(n)
	if lidos < 0 {
		return 0, fmt.Errorf("opus_decode devolveu %d", lidos)
	}
	return int(lidos), nil
}

func (d *Decodificador) Fechar() {
	if d.st == 0 {
		return
	}
	procDestruirDecodificador.Call(d.st)
	d.st = 0
}
