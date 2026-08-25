package main

import (
	"runtime"
	"sync"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	sOK              = 0
	eNaoTemInterface = 0x80004002
	ePonteiro        = 0x80004003
)

type tabelaDeBuffer struct {
	consultarInterface  uintptr
	somarRef            uintptr
	soltarRef           uintptr
	definirTamanho      uintptr
	tamanhoMaximo       uintptr
	pegarBufferETamanho uintptr
}

type BufferDeMidia struct {
	tabela *tabelaDeBuffer

	dados   []byte
	usado   uint32
	fixador runtime.Pinner
	refs    int32
}

var (
	registroDeBuffers sync.Map
	tabelaUnica       *tabelaDeBuffer
	umaVezSo          sync.Once

	fixadorDaTabela runtime.Pinner
)

func montarTabela() {
	umaVezSo.Do(func() {
		tabelaUnica = &tabelaDeBuffer{
			consultarInterface:  syscall.NewCallback(consultarInterface),
			somarRef:            syscall.NewCallback(somarRef),
			soltarRef:           syscall.NewCallback(soltarRef),
			definirTamanho:      syscall.NewCallback(definirTamanho),
			tamanhoMaximo:       syscall.NewCallback(tamanhoMaximo),
			pegarBufferETamanho: syscall.NewCallback(pegarBufferETamanho),
		}

		fixadorDaTabela.Pin(tabelaUnica)
	})
}

func NovoBufferDeMidia(capacidade int) *BufferDeMidia {
	montarTabela()
	b := &BufferDeMidia{
		tabela: tabelaUnica,
		dados:  make([]byte, capacidade),
		refs:   1,
	}

	b.fixador.Pin(b)
	b.fixador.Pin(&b.dados[0])
	registroDeBuffers.Store(b.Ponteiro(), b)
	return b
}

func (b *BufferDeMidia) Ponteiro() uintptr { return uintptr(unsafe.Pointer(b)) }

func (b *BufferDeMidia) Usado() int { return int(b.usado) }

func (b *BufferDeMidia) Conteudo() []byte { return b.dados[:b.usado] }

func (b *BufferDeMidia) Zerar() { b.usado = 0 }

func (b *BufferDeMidia) Fechar() {
	registroDeBuffers.Delete(b.Ponteiro())
	b.fixador.Unpin()
}

func doRegistro(this uintptr) *BufferDeMidia {
	if v, ok := registroDeBuffers.Load(this); ok {
		return v.(*BufferDeMidia)
	}
	return nil
}

var iidBufferDeMidia = guid(0x59EFF8B9, 0x938C, 0x4A26,
	[8]byte{0x82, 0xF2, 0x95, 0xCB, 0x84, 0xCD, 0xC8, 0x37})

var iidDesconhecido = guid(0x00000000, 0x0000, 0x0000,
	[8]byte{0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46})

func consultarInterface(this, riid, ppv uintptr) uintptr {
	if ppv == 0 {
		return ePonteiro
	}
	b := doRegistro(this)
	if b == nil {
		return eNaoTemInterface
	}
	pedido := (*[16]byte)(unsafe.Pointer(riid))
	if *pedido == comoBytes(iidBufferDeMidia) || *pedido == comoBytes(iidDesconhecido) {
		*(*uintptr)(unsafe.Pointer(ppv)) = this
		b.refs++
		return sOK
	}

	*(*uintptr)(unsafe.Pointer(ppv)) = 0
	return eNaoTemInterface
}

func somarRef(this uintptr) uintptr {
	b := doRegistro(this)
	if b == nil {
		return 0
	}
	b.refs++
	return uintptr(b.refs)
}

func soltarRef(this uintptr) uintptr {
	b := doRegistro(this)
	if b == nil {
		return 0
	}
	if b.refs > 0 {
		b.refs--
	}
	return uintptr(b.refs)
}

func definirTamanho(this, tamanho uintptr) uintptr {
	b := doRegistro(this)
	if b == nil {
		return ePonteiro
	}
	if int(tamanho) > len(b.dados) {
		return ePonteiro
	}
	b.usado = uint32(tamanho)
	return sOK
}

func tamanhoMaximo(this, destino uintptr) uintptr {
	if destino == 0 {
		return ePonteiro
	}
	b := doRegistro(this)
	if b == nil {
		return ePonteiro
	}
	*(*uint32)(unsafe.Pointer(destino)) = uint32(len(b.dados))
	return sOK
}

func pegarBufferETamanho(this, ondeBuffer, ondeTamanho uintptr) uintptr {
	b := doRegistro(this)
	if b == nil {
		return ePonteiro
	}

	if ondeBuffer != 0 {
		*(*uintptr)(unsafe.Pointer(ondeBuffer)) = uintptr(unsafe.Pointer(&b.dados[0]))
	}
	if ondeTamanho != 0 {
		*(*uint32)(unsafe.Pointer(ondeTamanho)) = b.usado
	}
	return sOK
}

func comoBytes(g windows.GUID) [16]byte {
	return *(*[16]byte)(unsafe.Pointer(&g))
}
