package main

import (
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	mfplat             = windows.NewLazySystemDLL("mfplat.dll")
	procMFStartup      = mfplat.NewProc("MFStartup")
	procMFShutdown     = mfplat.NewProc("MFShutdown")
	procMFTEnumEx      = mfplat.NewProc("MFTEnumEx")
	procMFCriarTipo    = mfplat.NewProc("MFCreateMediaType")
	procMFCriarAmostra = mfplat.NewProc("MFCreateSample")
)

var (
	catCompressorDeVideo = guid(0xF79EAC7D, 0xE545, 0x4387,
		[8]byte{0xBD, 0xEE, 0xD6, 0x47, 0xD7, 0xBD, 0xE4, 0x2A})

	tipoMaiorVideo = guid(0x73646976, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	formatoH264 = guid(0x34363248, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	formatoNV12 = guid(0x3231564E, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	chaveNomeDoCompressor = guid(0x314FFBAE, 0x5B41, 0x4C95,
		[8]byte{0x9C, 0x19, 0x4E, 0x7D, 0x58, 0x6F, 0xAC, 0xE3})

	chaveTipoMaior = guid(0x48EBA18E, 0xF8C9, 0x4687,
		[8]byte{0xBF, 0x11, 0x0A, 0x74, 0xC9, 0xF9, 0x6A, 0x8F})

	chaveBandaMedia = guid(0x20332624, 0xFB0D, 0x4D9E,
		[8]byte{0xBD, 0x0D, 0xCB, 0xF6, 0x78, 0x6C, 0x10, 0x2E})

	chaveTamanhoDoQuadro = guid(0x1652C33D, 0xD6B2, 0x4012,
		[8]byte{0xB8, 0x34, 0x72, 0x03, 0x08, 0x49, 0xA3, 0x7D})

	chaveTaxaDeQuadros = guid(0xC459A2E8, 0x3D2C, 0x4E44,
		[8]byte{0xB1, 0x32, 0xFE, 0xE5, 0x15, 0x6C, 0x7B, 0xB0})

	chaveEntrelacamento = guid(0xE2724BB8, 0xE676, 0x4806,
		[8]byte{0xB4, 0xB2, 0xA8, 0xD6, 0xEF, 0xB4, 0x4C, 0xCD})

	chaveDoEspacamento = guid(0xC16EB52B, 0x73A1, 0x476F,
		[8]byte{0x8D, 0x62, 0x83, 0x9D, 0x6A, 0x02, 0x06, 0x52})

	chavePerfil = guid(0xAD76A80B, 0x2D5C, 0x4E0B,
		[8]byte{0xB3, 0x75, 0x64, 0xE5, 0x20, 0x13, 0x70, 0x36})

	chaveSubtipo = guid(0xF7E34C9A, 0x42E8, 0x4714,
		[8]byte{0xB7, 0x4B, 0xCB, 0x29, 0xD7, 0x2C, 0x35, 0xE5})

	chaveDestrancar = guid(0xE5666D6B, 0x3422, 0x4EB6,
		[8]byte{0xA4, 0x21, 0xDA, 0x7D, 0xB1, 0xF8, 0xE2, 0x07})

	chaveFalaD3D11 = guid(0x206B4FC8, 0xFCF9, 0x4C51,
		[8]byte{0xAF, 0xE3, 0x97, 0x64, 0x36, 0x9E, 0x33, 0xA0})
)

const (
	atrPegarUINT32       = 7
	atrPegarGUID         = 10
	atrPegarTextoAlocado = 13
	_atrContar           = 30
	atrDefinirUINT32     = 21
	atrDefinirUINT64     = 22
	atrDefinirGUID       = 24
	ativarObjeto         = 33
	_desligarObjeto      = 34
)

const (
	_transLimitesDeFluxo = 3
	_transContarFluxos   = 4
	_transIdsDeFluxo     = 5
	_transInfoDaEntrada  = 6
	transInfoDaSaida     = 7
	transPegarAtributos  = 8
	transTipoDeEntrada   = 13
	_transTipoDeSaida    = 14
	transDefinirEntrada  = 15
	transDefinirSaida    = 16
	transMandarRecado    = 23
	transEntrarQuadro    = 24
	transSairQuadro      = 25
)

const (
	mftSincrono      = 0x00000001
	mftAssincrono    = 0x00000002
	mftHardware      = 0x00000004
	mftOrdenaEFiltra = 0x00000040
)

type tipoRegistrado struct {
	Maior   windows.GUID
	Formato windows.GUID
}

const progressivo = 2

const perfilBaseline = 66

const versaoDoMF = 0x00020070

func abrirMF() error {
	const semSocket = 1
	r, _, _ := procMFStartup.Call(versaoDoMF, semSocket)
	return hr(r, "iniciar o Media Foundation")
}

func fecharMF() { procMFShutdown.Call() }

type CompressorDisponivel struct {
	Nome     string
	ativador objeto
}

func ProcurarCompressores() ([]CompressorDisponivel, error) {
	entrada := tipoRegistrado{Maior: tipoMaiorVideo, Formato: formatoNV12}
	saida := tipoRegistrado{Maior: tipoMaiorVideo, Formato: formatoH264}

	var lista *objeto
	var quantos uint32
	r, _, _ := procMFTEnumEx.Call(
		uintptr(unsafe.Pointer(&catCompressorDeVideo)),

		uintptr(mftHardware|mftSincrono|mftAssincrono|mftOrdenaEFiltra),
		uintptr(unsafe.Pointer(&entrada)),
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&lista)),
		uintptr(unsafe.Pointer(&quantos)),
	)
	if err := hr(r, "procurar compressores"); err != nil {
		return nil, err
	}
	if quantos == 0 {
		return nil, nil
	}

	defer liberarMemoriaDoCOM(uintptr(unsafe.Pointer(lista)))

	achados := make([]CompressorDisponivel, 0, quantos)
	vetor := unsafe.Slice(lista, quantos)
	for _, a := range vetor {
		achados = append(achados, CompressorDisponivel{
			Nome:     textoDoAtributo(a, &chaveNomeDoCompressor),
			ativador: a,
		})
	}
	return achados, nil
}

func (c CompressorDisponivel) FalaD3D11() (bool, error) {
	t, err := c.Montar()
	if err != nil {
		return false, err
	}
	defer t.soltar()

	var atributos objeto
	r := t.chamar(transPegarAtributos, uintptr(unsafe.Pointer(&atributos)))
	if err := hr(r, "ler os atributos do compressor"); err != nil {
		return false, err
	}
	defer atributos.soltar()

	return numeroDoAtributo(atributos, &chaveFalaD3D11) != 0, nil
}

func SoltarCompressores(lista []CompressorDisponivel) {
	for _, c := range lista {
		c.ativador.soltar()
	}
}

func (c CompressorDisponivel) Montar() (objeto, error) {

	iid := guid(0xBF94C121, 0x5B05, 0x4E6F,
		[8]byte{0x80, 0x00, 0xBA, 0x59, 0x89, 0x61, 0x41, 0x4D})
	var t objeto
	r := c.ativador.chamar(ativarObjeto,
		uintptr(unsafe.Pointer(&iid)),
		uintptr(unsafe.Pointer(&t)),
	)
	if err := hr(r, "ligar o compressor "+c.Nome); err != nil {
		return 0, err
	}
	return t, nil
}

func numeroDoAtributo(a objeto, chave *windows.GUID) uint32 {
	var v uint32
	r := a.chamar(atrPegarUINT32,
		uintptr(unsafe.Pointer(chave)),
		uintptr(unsafe.Pointer(&v)),
	)
	if uint32(r)&0x80000000 != 0 {
		return 0
	}
	return v
}

func textoDoAtributo(a objeto, chave *windows.GUID) string {
	var ponteiro *uint16
	var tamanho uint32
	r := a.chamar(atrPegarTextoAlocado,
		uintptr(unsafe.Pointer(chave)),
		uintptr(unsafe.Pointer(&ponteiro)),
		uintptr(unsafe.Pointer(&tamanho)),
	)
	if uint32(r)&0x80000000 != 0 || ponteiro == nil {
		return ""
	}
	defer liberarMemoriaDoCOM(uintptr(unsafe.Pointer(ponteiro)))
	return windows.UTF16PtrToString(ponteiro)
}

func configurarSaida(t objeto, largura, altura, fps, kbps int) error {
	var tipo objeto
	r, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "criar o tipo de saída"); err != nil {
		return err
	}
	defer tipo.soltar()

	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formatoH264)

	definirNumero(tipo, &chaveBandaMedia, uint32(kbps*1000))
	definirNumero(tipo, &chaveEntrelacamento, progressivo)

	definirNumero(tipo, &chavePerfil, perfilBaseline)

	definirNumero(tipo, &chaveDoEspacamento, uint32(fps*2))

	definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)
	definirPar(tipo, &chaveTaxaDeQuadros, fps, 1)

	r = t.chamar(transDefinirSaida, 0, uintptr(tipo), 0)
	return hr(r, fmt.Sprintf("definir a saída em %dx%d @%d", largura, altura, fps))
}

func (c CompressorDisponivel) FormatosQueAceita() ([]string, error) {
	t, err := c.Montar()
	if err != nil {
		return nil, err
	}
	defer t.soltar()
	if err := destrancarSeAssincrono(t); err != nil {
		return nil, err
	}

	if err := configurarSaida(t, 1280, 720, 60, 4000); err != nil {
		return nil, err
	}

	formatos := []string{}
	for i := uint32(0); i < 64; i++ {
		var tipo objeto
		r := t.chamar(transTipoDeEntrada, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))

		if uint32(r)&0x80000000 != 0 {
			break
		}
		var sub windows.GUID
		if tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)),
			uintptr(unsafe.Pointer(&sub)),
		)&0x80000000 == 0 {
			formatos = append(formatos, nomeDoFormato(sub))
		}
		tipo.soltar()
	}
	return formatos, nil
}

func destrancarSeAssincrono(t objeto) error {
	var atributos objeto
	if r := t.chamar(transPegarAtributos, uintptr(unsafe.Pointer(&atributos))); uint32(r)&0x80000000 != 0 {
		return nil
	}
	defer atributos.soltar()
	atributos.chamar(atrDefinirUINT32, uintptr(unsafe.Pointer(&chaveDestrancar)), 1)
	return nil
}

func nomeDoFormato(g windows.GUID) string {
	molde := [8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71}
	if g.Data2 != 0x0000 || g.Data3 != 0x0010 || g.Data4 != molde {
		return g.String()
	}
	switch g.Data1 {
	case 21:
		return "ARGB32"
	case 22:
		return "RGB32"
	}

	quatro := [4]byte{byte(g.Data1), byte(g.Data1 >> 8), byte(g.Data1 >> 16), byte(g.Data1 >> 24)}
	for _, b := range quatro {
		if b < 0x20 || b > 0x7E {
			return fmt.Sprintf("formato %d", g.Data1)
		}
	}
	return string(quatro[:])
}

func RelatarCompressores() (string, error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err := abrirCOM(); err != nil {
		return "", err
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return "", err
	}
	defer fecharMF()

	lista, err := ProcurarCompressores()
	if err != nil {
		return "", err
	}
	defer SoltarCompressores(lista)

	if len(lista) == 0 {
		return "nenhum compressor de H.264 nesta máquina", nil
	}
	texto := ""
	for _, c := range lista {
		fala, err := c.FalaD3D11()
		switch {

		case err != nil:
			texto += fmt.Sprintf("%-46s não ligou: %v\n", c.Nome, err)
		case fala:
			texto += fmt.Sprintf("%-46s fala D3D11 (o quadro fica na placa)\n", c.Nome)
		default:
			texto += fmt.Sprintf("%-46s NÃO fala D3D11 (o quadro teria que descer)\n", c.Nome)
		}
	}
	return texto, nil
}
