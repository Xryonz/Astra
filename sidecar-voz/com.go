package main

import (
	"fmt"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	idxQueryInterface = 0
	idxAddRef         = 1
	idxRelease        = 2
)

type objeto uintptr

func (o objeto) metodo(indice int) uintptr {
	vt := *(**[64]uintptr)(unsafe.Pointer(o))
	return vt[indice]
}

func (o objeto) chamar(indice int, args ...uintptr) uintptr {
	todos := make([]uintptr, 0, len(args)+1)
	todos = append(todos, uintptr(o))
	todos = append(todos, args...)
	r, _, _ := syscall.SyscallN(o.metodo(indice), todos...)
	return r
}

func (o objeto) consultar(iid *windows.GUID) (objeto, error) {
	const consultarInterface = 0
	var outra objeto
	r := o.chamar(consultarInterface,
		uintptr(unsafe.Pointer(iid)),
		uintptr(unsafe.Pointer(&outra)),
	)
	if err := hr(r, "consultar interface"); err != nil {
		return 0, err
	}
	if outra == 0 {
		return 0, fmt.Errorf("consultar interface: respondeu sucesso sem entregar a interface")
	}
	return outra, nil
}

func naoNulo(o objeto, oQueFazia string) error {
	if o == 0 {
		return fmt.Errorf("%s: respondeu sucesso sem entregar o objeto", oQueFazia)
	}
	return nil
}

func (o objeto) soltar() {
	if o == 0 {
		return
	}
	o.chamar(idxRelease)
}

func hr(codigo uintptr, oQueFazia string) error {
	c := uint32(codigo)
	if c&0x80000000 == 0 {
		return nil
	}
	return fmt.Errorf("%s: %w", oQueFazia, windows.Errno(c))
}

func guid(d1 uint32, d2, d3 uint16, d4 [8]byte) windows.GUID {
	return windows.GUID{Data1: d1, Data2: d2, Data3: d3, Data4: d4}
}

var (
	clsidEnumeradorDeDispositivos = guid(0xBCDE0395, 0xE52F, 0x467C,
		[8]byte{0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E})

	iidEnumeradorDeDispositivos = guid(0xA95664D2, 0x9614, 0x4F35,
		[8]byte{0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6})

	iidClienteDeAudio = guid(0x1CB9AD4C, 0xDBFA, 0x4C32,
		[8]byte{0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2})

	iidClienteDeCaptura = guid(0xC8ADBD64, 0xE71E, 0x48A0,
		[8]byte{0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17})

	iidClienteDeSaida = guid(0xF294ACFC, 0x3146, 0x4483,
		[8]byte{0xA7, 0xBF, 0xAD, 0xDC, 0xA7, 0xC2, 0x60, 0xE2})

	chaveNomeAmigavel = chaveDePropriedade{
		conjunto: guid(0xA45C254E, 0xDF1C, 0x4EFD,
			[8]byte{0x80, 0x20, 0x67, 0xD1, 0x46, 0xA8, 0x50, 0xE0}),
		id: 14,
	}
)

type chaveDePropriedade struct {
	conjunto windows.GUID
	id       uint32
}

type propvariant struct {
	tipo     uint16
	_        [3]uint16
	ponteiro uintptr
	_        uintptr
}

const tipoTextoLargo = 31

const (
	mmEnumAudioEndpoints        = 3
	mmGetDefaultAudioEndpoint   = 4
	_mmGetDevice                = 5
	_mmRegisterEndpointNotify   = 6
	_mmUnregisterEndpointNotify = 7

	mmDeviceActivate  = 3
	mmDeviceAbrirLoja = 4
	mmDeviceGetId     = 5
	_mmDeviceGetSt    = 6

	colContar = 3
	colItem   = 4

	_lojaContar  = 3
	_lojaChave   = 4
	lojaLer      = 5
	lojaEscrever = 6
	_lojaFirmar  = 7

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

	capGetBuffer         = 3
	capReleaseBuffer     = 4
	capGetNextPacketSize = 5

	renGetBuffer     = 3
	renReleaseBuffer = 4
)

const (
	sentidoSaida   = 0
	sentidoEntrada = 1

	papelComunicacao = 2
)

const (
	modoCompartilhado = 0

	avisaPorEvento = 0x00040000

	converteFormato   = 0x80000000
	qualidadePadraoSR = 0x08000000
)

const porMilissegundo = 10000

func abrirCOM() error {
	const rpcMudouModo = 0x80010106
	r, _, _ := procCoInitializeEx.Call(0, 0)
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

func criar(clsid, iid *windows.GUID) (objeto, error) {
	const contextoNoProcesso = 1
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

func liberarMemoriaDoCOM(p uintptr) {
	if p != 0 {
		procCoTaskMemFree.Call(p)
	}
}

type formatoDeOnda struct {
	Tipo        uint16
	Canais      uint16
	Amostras    uint32
	BytesPorSeg uint32
	Alinhamento uint16
	BitsPorAmos uint16
	Extra       uint16
}

const tamanhoDoFormatoDeOnda = 18

func formatoPCM(taxa, canais int) formatoDeOnda {
	const pcm = 1
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
