package main

import (
	"errors"
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	TaxaDeAmostragem = 48000
	CanaisDeVoz      = 1

	MilissegundosPorQuadro = 20
	AmostrasPorQuadro      = TaxaDeAmostragem * MilissegundosPorQuadro / 1000
)

type Captura struct {
	enumerador  objeto
	dispositivo objeto
	cliente     objeto
	captor      objeto
	evento      windows.Handle
	rodando     bool

	sobra    []int16
	reserva  []int16
	silencio []int16
}

func repartirBloco(destino, origem, reserva []int16) (int, []int16) {
	n := min(len(origem), len(destino))
	copy(destino[:n], origem[:n])
	return n, append(reserva[:0], origem[n:]...)
}

func AbrirCaptura(id string) (*Captura, error) {
	c := &Captura{}
	ok := false

	defer func() {
		if !ok {
			c.Fechar()
		}
	}()

	enumerador, err := criar(&clsidEnumeradorDeDispositivos, &iidEnumeradorDeDispositivos)
	if err != nil {
		return nil, fmt.Errorf("enumerador de áudio: %w", err)
	}
	c.enumerador = enumerador

	dispositivo, err := abrirDispositivo(enumerador, sentidoEntrada, id)
	if err != nil {
		return nil, err
	}
	c.dispositivo = dispositivo

	var cliente objeto
	r := dispositivo.chamar(mmDeviceActivate,
		uintptr(unsafe.Pointer(&iidClienteDeAudio)),
		1,
		0,
		uintptr(unsafe.Pointer(&cliente)),
	)
	if err := hr(r, "abrir o microfone"); err != nil {
		return nil, err
	}
	c.cliente = cliente

	formato := formatoPCM(TaxaDeAmostragem, CanaisDeVoz)

	duracao := int64(200 * porMilissegundo)
	r = cliente.chamar(acInitialize,
		uintptr(modoCompartilhado),
		uintptr(avisaPorEvento|converteFormato|qualidadePadraoSR),
		uintptr(duracao),
		uintptr(duracao>>32),
		uintptr(unsafe.Pointer(&formato)),
		0,
	)
	if err := hr(r, "configurar a captura"); err != nil {
		return nil, err
	}

	evento, err := windows.CreateEvent(nil, 0, 0, nil)
	if err != nil {
		return nil, fmt.Errorf("criar aviso de buffer: %w", err)
	}
	c.evento = evento

	if err := hr(cliente.chamar(acSetEventHandle, uintptr(evento)), "ligar o aviso"); err != nil {
		return nil, err
	}

	var captor objeto
	r = cliente.chamar(acGetService,
		uintptr(unsafe.Pointer(&iidClienteDeCaptura)),
		uintptr(unsafe.Pointer(&captor)),
	)
	if err := hr(r, "obter o leitor de captura"); err != nil {
		return nil, err
	}
	c.captor = captor

	if err := hr(cliente.chamar(acStart), "iniciar a captura"); err != nil {
		return nil, err
	}
	c.rodando = true

	ok = true
	return c, nil
}

const (
	blocoDescontinuo = 0x1

	blocoSilencioso = 0x2
)

var ErrSemAudio = errors.New("nada disponível agora")

func (c *Captura) Ler(destino []int16) (int, bool, error) {
	if len(c.sobra) > 0 {
		n := copy(destino, c.sobra)
		c.sobra = c.sobra[n:]
		return n, false, nil
	}

	var pacote uint32
	if err := hr(c.captor.chamar(capGetNextPacketSize, uintptr(unsafe.Pointer(&pacote))),
		"consultar o próximo bloco"); err != nil {
		return 0, false, err
	}
	if pacote == 0 {
		return 0, false, ErrSemAudio
	}

	var dados unsafe.Pointer
	var quadros, bandeiras uint32
	r := c.captor.chamar(capGetBuffer,
		uintptr(unsafe.Pointer(&dados)),
		uintptr(unsafe.Pointer(&quadros)),
		uintptr(unsafe.Pointer(&bandeiras)),
		0,
		0,
	)
	if err := hr(r, "pegar o bloco"); err != nil {
		return 0, false, err
	}

	total := int(quadros) * CanaisDeVoz
	origem := unsafe.Slice((*int16)(dados), total)
	if bandeiras&blocoSilencioso != 0 {
		if cap(c.silencio) < total {
			c.silencio = make([]int16, total)
		}
		origem = c.silencio[:total]
		clear(origem)
	}

	n, reserva := repartirBloco(destino, origem, c.reserva)
	c.reserva = reserva
	c.sobra = c.reserva

	if err := hr(c.captor.chamar(capReleaseBuffer, uintptr(quadros)),
		"devolver o bloco"); err != nil {
		return 0, false, err
	}

	return n, bandeiras&blocoDescontinuo != 0, nil
}

func (c *Captura) Esperar(limiteMs uint32) error {
	r, err := windows.WaitForSingleObject(c.evento, limiteMs)
	if err != nil {
		return fmt.Errorf("esperar pelo microfone: %w", err)
	}
	if r == uint32(windows.WAIT_TIMEOUT) {
		return ErrSemAudio
	}
	return nil
}

func (c *Captura) Fechar() {
	if c.rodando {
		c.cliente.chamar(acStop)
		c.rodando = false
	}
	c.captor.soltar()
	c.captor = 0
	if c.evento != 0 {
		windows.CloseHandle(c.evento)
		c.evento = 0
	}
	c.cliente.soltar()
	c.cliente = 0
	c.dispositivo.soltar()
	c.dispositivo = 0
	c.enumerador.soltar()
	c.enumerador = 0
}

func PrenderNaThread() func() {
	runtime.LockOSThread()
	return runtime.UnlockOSThread
}

func (c *Captura) Taxa() int { return TaxaDeAmostragem }
