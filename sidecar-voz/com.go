package main

// COM À MÃO, SEM CGO.
//
// O áudio do Windows (WASAPI) e o cancelamento de eco (Voice Capture DSP) só
// existem como objetos COM. A saída usual seria cgo; aqui não, pelo mesmo motivo do
// Opus — manter o build como `go build` puro.
//
// COMO COM FUNCIONA, para quem for mexer nisto depois: um objeto COM é um ponteiro
// para um ponteiro para uma tabela de funções (a "vtable"). Chamar o método N é ler
// o ponteiro N dessa tabela e pular para ele, passando o próprio objeto como
// primeiro argumento — é `this` explícito, exatamente como C++ faz por baixo.
//
// TRÊS REGRAS QUE NÃO SE QUEBRAM AQUI, e as três já custaram caro em outros
// projetos:
//
//  1. O ÍNDICE É CONTADO A PARTIR DE IUnknown. Todo objeto COM começa com
//     QueryInterface(0), AddRef(1) e Release(2). O primeiro método próprio de
//     qualquer interface é o índice 3. Errar o índice não dá erro: chama a função
//     errada, com os argumentos errados, e trava.
//  2. COM TEM AFINIDADE DE THREAD. Quem inicializa o apartamento tem que ser a
//     mesma thread que usa os objetos — daí `runtime.LockOSThread()` em toda
//     goroutine que toca nisto. Sem isso o Go troca a goroutine de thread no meio e
//     o comportamento vira aleatório.
//  3. TODO OBJETO OBTIDO PRECISA DE Release. Não há coletor de lixo do outro lado
//     da fronteira.

import (
	"fmt"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

// Os três primeiros índices de qualquer vtable COM.
const (
	idxQueryInterface = 0
	idxAddRef         = 1
	idxRelease        = 2
)

// objeto é um ponteiro para uma interface COM. Fica sem tipo de propósito: o que
// dá segurança aqui não é o tipo em Go, é o índice certo na tabela — e um tipo
// próprio por interface daria a falsa impressão de que o compilador está
// conferindo alguma coisa.
type objeto uintptr

// tabela devolve a vtable do objeto.
func (o objeto) tabela() **uintptr {
	return (**uintptr)(unsafe.Pointer(o))
}

// metodo lê o endereço do método `indice`.
func (o objeto) metodo(indice int) uintptr {
	vt := *(**[64]uintptr)(unsafe.Pointer(o))
	return vt[indice]
}

// chamar invoca o método `indice` passando o próprio objeto como primeiro
// argumento, que é a convenção de chamada de COM.
func (o objeto) chamar(indice int, args ...uintptr) uintptr {
	todos := make([]uintptr, 0, len(args)+1)
	todos = append(todos, uintptr(o))
	todos = append(todos, args...)
	r, _, _ := syscall.SyscallN(o.metodo(indice), todos...)
	return r
}

// soltar chama Release. Seguro em ponteiro nulo, porque desmontar coisa pela metade
// é o caso normal quando algo falha no meio da abertura.
func (o objeto) soltar() {
	if o == 0 {
		return
	}
	o.chamar(idxRelease)
}

// hr transforma um HRESULT em erro.
//
// HRESULT é um inteiro de 32 bits em que o bit mais alto ligado significa falha.
// O texto vem do próprio Windows, que costuma explicar melhor que qualquer tabela
// que eu escrevesse aqui — e alguns erros de áudio (dispositivo em uso, formato
// recusado) têm mensagem boa.
func hr(codigo uintptr, oQueFazia string) error {
	c := uint32(codigo)
	if c&0x80000000 == 0 {
		return nil
	}
	return fmt.Errorf("%s: %w", oQueFazia, windows.Errno(c))
}

// guid monta um windows.GUID a partir da forma escrita na documentação da
// Microsoft. Escrever assim, em vez de campo a campo, é o que permite conferir cada
// identificador contra a documentação de olho, sem reordenar bytes na cabeça.
func guid(d1 uint32, d2, d3 uint16, d4 [8]byte) windows.GUID {
	return windows.GUID{Data1: d1, Data2: d2, Data3: d3, Data4: d4}
}

// ---------------------------------------------------------------------------
// Identificadores do subsistema de áudio.

var (
	// CLSID_MMDeviceEnumerator {BCDE0395-E52F-467C-8E3D-C4579291692E}
	clsidEnumeradorDeDispositivos = guid(0xBCDE0395, 0xE52F, 0x467C,
		[8]byte{0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E})

	// IID_IMMDeviceEnumerator {A95664D2-9614-4F35-A746-DE8DB63617E6}
	iidEnumeradorDeDispositivos = guid(0xA95664D2, 0x9614, 0x4F35,
		[8]byte{0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6})

	// IID_IAudioClient {1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}
	iidClienteDeAudio = guid(0x1CB9AD4C, 0xDBFA, 0x4C32,
		[8]byte{0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2})

	// IID_IAudioCaptureClient {C8ADBD64-E71E-48A0-A4DE-185C395CD317}
	iidClienteDeCaptura = guid(0xC8ADBD64, 0xE71E, 0x48A0,
		[8]byte{0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17})

	// IID_IAudioRenderClient {F294ACFC-3146-4483-A7BF-ADDCA7C260E2}
	iidClienteDeSaida = guid(0xF294ACFC, 0x3146, 0x4483,
		[8]byte{0xA7, 0xBF, 0xAD, 0xDC, 0xA7, 0xC2, 0x60, 0xE2})
)

// Índices de vtable. Cada lista segue a ORDEM DE DECLARAÇÃO no cabeçalho da
// Microsoft, que é o que define a tabela — não a ordem alfabética nem a da
// documentação em HTML, que às vezes difere.
const (
	// IMMDeviceEnumerator (mmdeviceapi.h)
	mmEnumAudioEndpoints        = 3
	mmGetDefaultAudioEndpoint   = 4
	_mmGetDevice                = 5
	_mmRegisterEndpointNotify   = 6
	_mmUnregisterEndpointNotify = 7

	// IMMDevice (mmdeviceapi.h)
	mmDeviceActivate = 3
	_mmDeviceOpenPS  = 4
	mmDeviceGetId    = 5
	_mmDeviceGetSt   = 6

	// IAudioClient (audioclient.h)
	acInitialize         = 3
	acGetBufferSize      = 4
	_acGetStreamLatency  = 5
	acGetCurrentPadding  = 6
	_acIsFormatSupported = 7
	acGetMixFormat       = 8
	acGetDevicePeriod    = 9
	acStart              = 10
	acStop               = 11
	_acReset             = 12
	acSetEventHandle     = 13
	acGetService         = 14

	// IAudioCaptureClient (audioclient.h)
	capGetBuffer         = 3
	capReleaseBuffer     = 4
	capGetNextPacketSize = 5

	// IAudioRenderClient (audioclient.h)
	renGetBuffer     = 3
	renReleaseBuffer = 4
)

// Papéis e sentidos de dispositivo (mmdeviceapi.h).
const (
	sentidoSaida   = 0 // eRender
	sentidoEntrada = 1 // eCapture

	// eCommunications, e não eConsole, de propósito: é o dispositivo que a pessoa
	// escolheu no Windows PARA CONVERSAR. Quem tem uma placa de som boa para
	// música e um headset para call espera que o app de conversa use o headset.
	papelComunicacao = 2
)

// Modo e bandeiras do IAudioClient::Initialize (audioclient.h).
const (
	modoCompartilhado = 0 // AUDCLNT_SHAREMODE_SHARED

	// AUDCLNT_STREAMFLAGS_EVENTCALLBACK — o Windows avisa quando há buffer pronto,
	// em vez de o programa ficar perguntando. É o que permite latência baixa sem
	// queimar um núcleo num laço de espera.
	avisaPorEvento = 0x00040000

	// AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM + SRC_DEFAULT_QUALITY: deixa o Windows
	// reamostrar para o formato que pedimos. Sem isto, seria preciso aceitar a taxa
	// nativa do aparelho (44,1 kHz em muita placa) e reamostrar na mão para os 48
	// kHz que o Opus quer — trabalho que o sistema já faz bem.
	converteFormato   = 0x80000000
	qualidadePadraoSR = 0x08000000
)

// tamanhoDeReferencia é a unidade de tempo do WASAPI: 100 nanossegundos. Um buffer
// de 20ms são 200000 destas.
const porMilissegundo = 10000

// abrirCOM prepara o apartamento COM da thread atual.
//
// Multithreaded (MTA) porque o áudio roda em goroutine própria, sem bomba de
// mensagens de janela — apartamento de thread única exigiria uma, e não temos.
//
// RPC_E_CHANGED_MODE significa que a thread já está num apartamento de outro tipo.
// Isso não é falha para nós: os objetos continuam utilizáveis.
func abrirCOM() error {
	const rpcMudouModo = 0x80010106
	r, _, _ := procCoInitializeEx.Call(0, 0 /* COINIT_MULTITHREADED */)
	if uint32(r) == rpcMudouModo {
		return nil
	}
	return hr(r, "iniciar COM")
}

var (
	ole32              = windows.NewLazySystemDLL("ole32.dll")
	procCoInitializeEx = ole32.NewProc("CoInitializeEx")
	procCoUninitialize = ole32.NewProc("CoUninitialize")
	procCoCreateInst   = ole32.NewProc("CoCreateInstance")
	procCoTaskMemFree  = ole32.NewProc("CoTaskMemFree")
)

func fecharCOM() { procCoUninitialize.Call() }

// criar instancia um objeto COM pelo identificador de classe.
func criar(clsid, iid *windows.GUID) (objeto, error) {
	const contextoNoProcesso = 1 // CLSCTX_INPROC_SERVER
	var obj objeto
	r, _, _ := procCoCreateInst.Call(
		uintptr(unsafe.Pointer(clsid)),
		0,
		contextoNoProcesso,
		uintptr(unsafe.Pointer(iid)),
		uintptr(unsafe.Pointer(&obj)),
	)
	if err := hr(r, "criar objeto COM"); err != nil {
		return 0, err
	}
	return obj, nil
}

// liberarMemoriaDoCOM devolve memória que o Windows alocou para nós (formatos de
// áudio, identificadores de dispositivo). Quem aloca é quem manda no alocador, e
// aqui quem alocou foi o COM.
func liberarMemoriaDoCOM(p uintptr) {
	if p != 0 {
		procCoTaskMemFree.Call(p)
	}
}

// formatoDeOnda é o WAVEFORMATEX do Windows.
//
// O CAMPO `Extra` NÃO É ENFEITE: quando ele é diferente de zero, a estrutura de
// verdade é maior (WAVEFORMATEXTENSIBLE) e tem 22 bytes a mais logo depois. Ler ou
// copiar só estes campos, nesse caso, perde informação — é por isso que o formato
// devolvido pelo Windows é sempre tratado por ponteiro, e nunca copiado.
type formatoDeOnda struct {
	Tipo        uint16
	Canais      uint16
	Amostras    uint32
	BytesPorSeg uint32
	Alinhamento uint16
	BitsPorAmos uint16
	Extra       uint16
}

// formatoPCM monta o formato que queremos: PCM de 16 bits, o que o Opus consome.
func formatoPCM(taxa, canais int) formatoDeOnda {
	const pcm = 1 // WAVE_FORMAT_PCM
	bloco := uint16(canais * 2)
	return formatoDeOnda{
		Tipo:        pcm,
		Canais:      uint16(canais),
		Amostras:    uint32(taxa),
		BytesPorSeg: uint32(taxa) * uint32(bloco),
		Alinhamento: bloco,
		BitsPorAmos: 16,
		Extra:       0,
	}
}
