package main

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	mfreadwrite = windows.NewLazySystemDLL("mfreadwrite.dll")

	procCriarFonteDoAparelho = mfDll.NewProc("MFCreateDeviceSource")
	procCriarLeitor          = mfreadwrite.NewProc("MFCreateSourceReaderFromMediaSource")
)

const (
	atrDefinirTexto = 25

	leitorPrimeiroVideo = 0xFFFFFFFC

	leitorPegarTipoNativo = 5
	leitorDefinirTipo     = 7
	leitorLerAmostra      = 9

	fonteDesligar = 12
)

const (
	CameraLargura = 1280
	CameraAltura  = 720
	CameraFps     = 30
)

type CameraAberta struct {
	fonte  objeto
	leitor objeto

	Largura int
	Altura  int
}

func AbrirCamera(id string) (*CameraAberta, error) {
	return AbrirCameraEm(id, CameraLargura, CameraAltura, CameraFps)
}

func AbrirCameraEm(id string, largura, altura, fps int) (*CameraAberta, error) {
	var atributos objeto
	r, _, _ := procCriarAtributos.Call(uintptr(unsafe.Pointer(&atributos)), 2)
	if err := hr(r, "criar os atributos da câmera"); err != nil {
		return nil, err
	}
	defer atributos.soltar()

	definirGUID(atributos, &chaveTipoDaFonte, tipoFonteDeVideo)
	if id != "" {
		if err := definirTexto(atributos, &chaveLinkSimbolico, id); err != nil {
			return nil, err
		}
	}

	var fonte objeto
	r, _, _ = procCriarFonteDoAparelho.Call(uintptr(atributos), uintptr(unsafe.Pointer(&fonte)))
	if err := hr(r, "abrir a câmera"); err != nil {
		return nil, err
	}

	var leitor objeto
	r, _, _ = procCriarLeitor.Call(uintptr(fonte), 0, uintptr(unsafe.Pointer(&leitor)))
	if err := hr(r, "criar o leitor da câmera"); err != nil {
		fonte.soltar()
		return nil, err
	}

	c := &CameraAberta{fonte: fonte, leitor: leitor}
	if err := c.pedirNV12(largura, altura, fps); err != nil {
		c.Fechar()
		return nil, err
	}
	return c, nil
}

func definirTexto(a objeto, chave *windows.GUID, valor string) error {
	texto, err := windows.UTF16PtrFromString(valor)
	if err != nil {
		return fmt.Errorf("converter o id da câmera: %w", err)
	}
	r := a.chamar(atrDefinirTexto,
		uintptr(unsafe.Pointer(chave)),
		uintptr(unsafe.Pointer(texto)),
	)
	return hr(r, "guardar o id da câmera nos atributos")
}

func (c *CameraAberta) pedirNV12(largura, altura, fps int) error {
	var tipo objeto
	r, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "criar o tipo pedido à câmera"); err != nil {
		return err
	}
	defer tipo.soltar()

	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formatoNV12)
	definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)
	definirPar(tipo, &chaveTaxaDeQuadros, fps, 1)

	r = c.leitor.chamar(leitorDefinirTipo, leitorPrimeiroVideo, 0, uintptr(tipo))
	if err := hr(r, fmt.Sprintf("pedir NV12 %dx%d à câmera", largura, altura)); err != nil {
		return err
	}

	c.Largura, c.Altura = largura, altura
	return nil
}

func (c *CameraAberta) FormatosNativos() []string {
	formatos := []string{}
	for i := uint32(0); i < 64; i++ {
		var tipo objeto
		r := c.leitor.chamar(leitorPegarTipoNativo,
			leitorPrimeiroVideo, uintptr(i), uintptr(unsafe.Pointer(&tipo)))
		if uint32(r)&0x80000000 != 0 {
			break
		}
		var sub windows.GUID
		if tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)),
			uintptr(unsafe.Pointer(&sub)),
		)&0x80000000 == 0 {
			l, a, _ := parDoAtributo(tipo, &chaveTamanhoDoQuadro)
			formatos = append(formatos, fmt.Sprintf("%s %dx%d", nomeDoFormato(sub), l, a))
		}
		tipo.soltar()
	}
	return formatos
}

func (c *CameraAberta) ProximaAmostra() (objeto, error) {
	var fluxo, bandeiras uint32
	var carimbo int64
	var amostra objeto

	r := c.leitor.chamar(leitorLerAmostra,
		leitorPrimeiroVideo, 0,
		uintptr(unsafe.Pointer(&fluxo)),
		uintptr(unsafe.Pointer(&bandeiras)),
		uintptr(unsafe.Pointer(&carimbo)),
		uintptr(unsafe.Pointer(&amostra)),
	)
	if err := hr(r, "ler da câmera"); err != nil {
		return 0, err
	}
	return amostra, nil
}

func (c *CameraAberta) Fechar() {
	if c.leitor != 0 {
		c.leitor.soltar()
		c.leitor = 0
	}
	if c.fonte != 0 {
		c.fonte.chamar(fonteDesligar)
		c.fonte.soltar()
		c.fonte = 0
	}
}
