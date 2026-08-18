package main

// UMA INTERFACE COM IMPLEMENTADA DENTRO DO GO.
//
// Em todo o resto deste projeto o Go é CLIENTE de COM: pega um objeto do Windows e
// chama métodos nele. Aqui o sentido se inverte — o cancelador de eco exige que nós
// forneçamos um `IMediaBuffer`, e é ELE quem chama os nossos métodos.
//
// COMO UM OBJETO COM É POR DENTRO: um ponteiro para uma tabela de funções (a
// "vtable"), e é só isso. Quem recebe o objeto lê o primeiro campo, acha a tabela, e
// chama a função no índice que quer. Então basta montar uma tabela dessas com
// ponteiros para funções Go — e é o que `syscall.NewCallback` produz.
//
// TRÊS ARMADILHAS, e as três derrubam o processo se ignoradas:
//
//  1. O primeiro campo TEM de ser o ponteiro da tabela, e nada pode vir antes. O
//     Windows não sabe nada do nosso struct: ele lê os primeiros oito bytes do
//     endereço que demos e salta para lá.
//  2. O objeto não pode ser MOVIDO nem coletado enquanto o Windows tem o endereço.
//     Daí o `runtime.Pinner`.
//  3. Voltar do endereço para o objeto Go exige um registro próprio. O `this` que
//     chega no callback é um número; converter número em ponteiro Go e usar é
//     exatamente o que o coletor de lixo não garante.

import (
	"runtime"
	"sync"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

// HRESULTs que devolvemos.
const (
	sOK             = 0
	eNaoTemInterface = 0x80004002 // E_NOINTERFACE
	ePonteiro        = 0x80004003 // E_POINTER
)

// A tabela de funções do IMediaBuffer, na ordem exata de declaração: os três do
// IUnknown primeiro, depois os três próprios.
type tabelaDeBuffer struct {
	consultarInterface uintptr
	somarRef           uintptr
	soltarRef          uintptr
	definirTamanho     uintptr
	tamanhoMaximo      uintptr
	pegarBufferETamanho uintptr
}

// BufferDeMidia é o nosso IMediaBuffer.
//
// O `tabela` PRECISA ser o primeiro campo — ver a armadilha 1 lá em cima.
type BufferDeMidia struct {
	tabela *tabelaDeBuffer

	dados   []byte
	usado   uint32
	fixador runtime.Pinner
	refs    int32
}

// O registro que traduz endereço de volta para objeto.
//
// Um mapa e não conversão direta de ponteiro: o endereço que o Windows devolve é um
// inteiro, e ressuscitar um ponteiro Go a partir de inteiro é justamente o que as
// regras do coletor proíbem. O mapa mantém uma referência viva de verdade.
var (
	registroDeBuffers sync.Map // uintptr -> *BufferDeMidia
	tabelaUnica       *tabelaDeBuffer
	umaVezSo          sync.Once

	// O FIXADOR DA TABELA PRECISA VIVER TANTO QUANTO O PINO, e isto já foi um
	// defeito aqui: começou como variável local dentro da função que monta a tabela.
	//
	// O `runtime.Pinner` guarda um finalizador que PANICA se ele for coletado ainda
	// segurando pinos — "found leaking pinned pointer". Ou seja, o Go grita em vez
	// de deixar o ponteiro solto virar corrupção silenciosa mais tarde. Como a
	// tabela vive enquanto o processo viver, o fixador dela também tem de viver.
	fixadorDaTabela runtime.Pinner
)

func montarTabela() {
	umaVezSo.Do(func() {
		tabelaUnica = &tabelaDeBuffer{
			consultarInterface: syscall.NewCallback(consultarInterface),
			somarRef:           syscall.NewCallback(somarRef),
			soltarRef:          syscall.NewCallback(soltarRef),
			definirTamanho:     syscall.NewCallback(definirTamanho),
			tamanhoMaximo:      syscall.NewCallback(tamanhoMaximo),
			pegarBufferETamanho: syscall.NewCallback(pegarBufferETamanho),
		}
		// A tabela também não pode andar: o ponteiro dela vive dentro de cada objeto
		// que entregamos ao Windows.
		fixadorDaTabela.Pin(tabelaUnica)
	})
}

// NovoBufferDeMidia cria um buffer com a capacidade pedida.
func NovoBufferDeMidia(capacidade int) *BufferDeMidia {
	montarTabela()
	b := &BufferDeMidia{
		tabela: tabelaUnica,
		dados:  make([]byte, capacidade),
		refs:   1,
	}
	// Fixa o objeto E o vetor de bytes: os dois endereços vão para o Windows.
	b.fixador.Pin(b)
	b.fixador.Pin(&b.dados[0])
	registroDeBuffers.Store(b.Ponteiro(), b)
	return b
}

// Ponteiro é o endereço que se entrega ao Windows.
func (b *BufferDeMidia) Ponteiro() uintptr { return uintptr(unsafe.Pointer(b)) }

// Usado é quanto o cancelador escreveu na última volta.
func (b *BufferDeMidia) Usado() int { return int(b.usado) }

// Conteudo é o que foi escrito, e só isso.
func (b *BufferDeMidia) Conteudo() []byte { return b.dados[:b.usado] }

// Zerar prepara para a próxima volta. O cancelador ESCREVE a partir do fim atual,
// então não zerar faria o buffer encher e parar de receber áudio depois de alguns
// quadros — silêncio que só aparece um segundo depois de começar.
func (b *BufferDeMidia) Zerar() { b.usado = 0 }

// Fechar solta o objeto. Depois disto o endereço não vale mais.
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

// ---- os seis métodos ----

// IID_IMediaBuffer {59eff8b9-938c-4a26-82f2-95cb84cdc837}
var iidBufferDeMidia = guid(0x59EFF8B9, 0x938C, 0x4A26,
	[8]byte{0x82, 0xF2, 0x95, 0xCB, 0x84, 0xCD, 0xC8, 0x37})

// IID_IUnknown {00000000-0000-0000-C000-000000000046}
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
	// Recusar direito importa: um `ppv` deixado com lixo faz quem perguntou usar um
	// endereço aleatório como se fosse objeto.
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

// A CONTAGEM NÃO LIBERA NADA, e isso é deliberado.
//
// Quem cria o buffer somos nós, e quem o destrói é o nosso `Fechar` — o ciclo de
// vida é conhecido e curto (uma captura). Deixar o Windows liberar memória Go pela
// contagem de referências seria entregar a um estranho a decisão de quando o
// coletor pode agir.
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
	// Os dois destinos são OPCIONAIS pelo contrato do COM: quem só quer o tamanho
	// passa nulo no outro. Escrever num nulo derruba o processo.
	if ondeBuffer != 0 {
		*(*uintptr)(unsafe.Pointer(ondeBuffer)) = uintptr(unsafe.Pointer(&b.dados[0]))
	}
	if ondeTamanho != 0 {
		*(*uint32)(unsafe.Pointer(ondeTamanho)) = b.usado
	}
	return sOK
}

// comoBytes achata um GUID para comparar sem campo a campo.
func comoBytes(g windows.GUID) [16]byte {
	return *(*[16]byte)(unsafe.Pointer(&g))
}
