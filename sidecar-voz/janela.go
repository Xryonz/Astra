package main

import (
	"fmt"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	combase                    = windows.NewLazySystemDLL("combase.dll")
	procRoInitialize           = combase.NewProc("RoInitialize")
	procRoGetActivationFactory = combase.NewProc("RoGetActivationFactory")
	procCriarString            = combase.NewProc("WindowsCreateString")
	procSoltarString           = combase.NewProc("WindowsDeleteString")

	procD3DDeDispositivo = d3d11.NewProc("CreateDirect3D11DeviceFromDXGIDevice")

	iidItemDeCapturaInterop = guid(0x3628E81B, 0x3CAC, 0x4C60,
		[8]byte{0xB7, 0xF4, 0x23, 0xCE, 0x0E, 0x0C, 0x33, 0x56})
	iidItemDeCaptura = guid(0x79C3F95B, 0x31F7, 0x4EC2,
		[8]byte{0xA4, 0x64, 0x63, 0x2E, 0xF5, 0xD3, 0x07, 0x60})
	iidPiscinaStatics2 = guid(0x589B103F, 0x6BBC, 0x5DF5,
		[8]byte{0xA9, 0x91, 0x02, 0xE2, 0x8B, 0x3B, 0x66, 0xD5})
	iidPiscinaStatics = guid(0x7784056A, 0x67AA, 0x4D53,
		[8]byte{0xAE, 0x54, 0x10, 0x88, 0xD5, 0xA8, 0xCA, 0x21})
	iidAcessoDxgi = guid(0xA9B3D012, 0x3DF2, 0x4EE3,
		[8]byte{0xB8, 0xD1, 0x86, 0x95, 0xF4, 0x57, 0xD3, 0xC1})
	iidFechavel = guid(0x30D5A829, 0x7FA4, 0x4026,
		[8]byte{0x83, 0xBB, 0xD7, 0x5B, 0xAE, 0x4E, 0xA9, 0x9E})
	iidSessaoComBorda = guid(0x2C39AE40, 0x7D2E, 0x5044,
		[8]byte{0x80, 0x4E, 0x8B, 0x67, 0x99, 0xD4, 0xCF, 0x9E})
)

const (
	winrtMultithreaded     = 1
	jaIniciadoDeOutroJeito = 0x80010106

	formatoBGRA8DoWinRT = 87
	quadrosNaPiscina    = 2

	idxCriarParaJanela = 3
	idxRecriarPiscina  = 6
	idxCriarPiscina    = 6
	idxPegarProximo    = 7
	idxCriarSessao     = 10
	idxComecarCaptura  = 6
	idxPegarSuperficie = 6
	idxPegarConteudo   = 8
	idxPegarInterface  = 3
	idxFechar          = 6
	idxDefinirBorda    = 7
)

type tamanhoInt32 struct{ Largura, Altura int32 }

func iniciarWinRT() error {
	r, _, _ := procRoInitialize.Call(winrtMultithreaded)
	if uint32(r) == jaIniciadoDeOutroJeito {
		return nil
	}
	return hr(r, "iniciar o WinRT")
}

func criarHString(s string) (uintptr, error) {
	u, err := windows.UTF16FromString(s)
	if err != nil {
		return 0, err
	}
	var h uintptr
	r, _, _ := procCriarString.Call(
		uintptr(unsafe.Pointer(&u[0])),
		uintptr(len(u)-1),
		uintptr(unsafe.Pointer(&h)),
	)
	if err := hr(r, "criar HSTRING"); err != nil {
		return 0, err
	}
	return h, nil
}

func fabricaDe(classe string, iid *windows.GUID) (objeto, error) {
	h, err := criarHString(classe)
	if err != nil {
		return 0, err
	}
	defer procSoltarString.Call(h)
	var f objeto
	r, _, _ := procRoGetActivationFactory.Call(h,
		uintptr(unsafe.Pointer(iid)),
		uintptr(unsafe.Pointer(&f)),
	)
	if err := hr(r, "fábrica de "+classe); err != nil {
		return 0, err
	}
	return f, naoNulo(f, "fábrica de "+classe)
}

type CapturaDeJanela struct {
	item     objeto
	d3dWinRT objeto
	piscina  objeto
	sessao   objeto

	largura int
	altura  int

	quadroRetido objeto
}

func AbrirCapturaDeJanela(dispositivo objeto, janela uintptr, largura, altura int) (*CapturaDeJanela, error) {
	if janela == 0 {
		return nil, fmt.Errorf("janela sem identificador")
	}
	if largura <= 0 || altura <= 0 {
		return nil, fmt.Errorf("a janela informou tamanho %dx%d", largura, altura)
	}
	if err := iniciarWinRT(); err != nil {
		return nil, err
	}

	c := &CapturaDeJanela{largura: largura &^ 1, altura: altura &^ 1}
	pronto := false
	defer func() {
		if !pronto {
			c.Fechar()
		}
	}()

	interop, err := fabricaDe("Windows.Graphics.Capture.GraphicsCaptureItem", &iidItemDeCapturaInterop)
	if err != nil {
		return nil, err
	}
	defer interop.soltar()

	r := interop.chamar(idxCriarParaJanela, janela,
		uintptr(unsafe.Pointer(&iidItemDeCaptura)),
		uintptr(unsafe.Pointer(&c.item)),
	)
	if err := hr(r, "transformar a janela em item de captura"); err != nil {
		return nil, err
	}
	if err := naoNulo(c.item, "transformar a janela em item de captura"); err != nil {
		return nil, err
	}

	dxgi, err := dispositivo.consultar(&iidDispositivoDXGI)
	if err != nil {
		return nil, fmt.Errorf("o dispositivo não fala DXGI: %w", err)
	}
	defer dxgi.soltar()

	r, _, _ = procD3DDeDispositivo.Call(uintptr(dxgi), uintptr(unsafe.Pointer(&c.d3dWinRT)))
	if err := hr(r, "embrulhar o dispositivo de vídeo para o WinRT"); err != nil {
		return nil, err
	}

	statics, err := fabricaDe("Windows.Graphics.Capture.Direct3D11CaptureFramePool", &iidPiscinaStatics2)
	if err != nil {
		statics, err = fabricaDe("Windows.Graphics.Capture.Direct3D11CaptureFramePool", &iidPiscinaStatics)
		if err != nil {
			return nil, err
		}
	}
	defer statics.soltar()

	tamanho := uintptr(uint32(c.largura)) | uintptr(uint32(c.altura))<<32
	r = statics.chamar(idxCriarPiscina,
		uintptr(c.d3dWinRT),
		uintptr(formatoBGRA8DoWinRT),
		quadrosNaPiscina,
		tamanho,
		uintptr(unsafe.Pointer(&c.piscina)),
	)
	if err := hr(r, "criar a piscina de quadros"); err != nil {
		return nil, err
	}

	r = c.piscina.chamar(idxCriarSessao, uintptr(c.item), uintptr(unsafe.Pointer(&c.sessao)))
	if err := hr(r, "abrir a sessão de captura"); err != nil {
		return nil, err
	}

	if comBorda, err := c.sessao.consultar(&iidSessaoComBorda); err == nil {
		comBorda.chamar(idxDefinirBorda, 0)
		comBorda.soltar()
	}

	if err := hr(c.sessao.chamar(idxComecarCaptura), "começar a captura da janela"); err != nil {
		return nil, err
	}

	pronto = true
	return c, nil
}

func (c *CapturaDeJanela) Tamanho() (int, int) { return c.largura, c.altura }

func (c *CapturaDeJanela) ProximoQuadro(limiteMs uint32) (objeto, error) {
	if c.quadroRetido != 0 {
		return 0, fmt.Errorf("quadro anterior não foi devolvido")
	}

	fim := time.Now().Add(time.Duration(limiteMs) * time.Millisecond)
	for {
		var quadro objeto
		r := c.piscina.chamar(idxPegarProximo, uintptr(unsafe.Pointer(&quadro)))
		if err := hr(r, "pegar o próximo quadro da janela"); err != nil {
			return 0, err
		}
		if quadro != 0 {
			c.quadroRetido = quadro
			if l, a, ok := tamanhoDoConteudo(quadro); ok && (l != c.largura || a != c.altura) {
				c.SoltarQuadro()
				return 0, c.reenquadrar(l, a)
			}
			return c.texturaDe(quadro)
		}
		if !time.Now().Before(fim) {
			return 0, nil
		}
		time.Sleep(2 * time.Millisecond)
	}
}

func tamanhoDoConteudo(quadro objeto) (int, int, bool) {
	var t tamanhoInt32
	if quadro.chamar(idxPegarConteudo, uintptr(unsafe.Pointer(&t)))&0x80000000 != 0 {
		return 0, 0, false
	}
	l, a := int(t.Largura)&^1, int(t.Altura)&^1
	if l <= 0 || a <= 0 {
		return 0, 0, false
	}
	return l, a, true
}

func (c *CapturaDeJanela) reenquadrar(largura, altura int) error {
	tamanho := uintptr(uint32(largura)) | uintptr(uint32(altura))<<32
	r := c.piscina.chamar(idxRecriarPiscina,
		uintptr(c.d3dWinRT),
		uintptr(formatoBGRA8DoWinRT),
		quadrosNaPiscina,
		tamanho,
	)
	if err := hr(r, fmt.Sprintf("refazer a piscina em %dx%d", largura, altura)); err != nil {
		return err
	}
	c.largura, c.altura = largura, altura
	return nil
}

func (c *CapturaDeJanela) texturaDe(quadro objeto) (objeto, error) {
	var superficie objeto
	if err := hr(quadro.chamar(idxPegarSuperficie, uintptr(unsafe.Pointer(&superficie))), "abrir a superfície do quadro"); err != nil {
		c.SoltarQuadro()
		return 0, err
	}
	defer superficie.soltar()

	acesso, err := superficie.consultar(&iidAcessoDxgi)
	if err != nil {
		c.SoltarQuadro()
		return 0, fmt.Errorf("a superfície não expõe DXGI: %w", err)
	}
	defer acesso.soltar()

	var textura objeto
	r := acesso.chamar(idxPegarInterface,
		uintptr(unsafe.Pointer(&iidTextura2D)),
		uintptr(unsafe.Pointer(&textura)),
	)
	if err := hr(r, "tirar a textura do quadro"); err != nil {
		c.SoltarQuadro()
		return 0, err
	}
	return textura, nil
}

func (c *CapturaDeJanela) SoltarQuadro() {
	if c.quadroRetido == 0 {
		return
	}
	if fechavel, err := c.quadroRetido.consultar(&iidFechavel); err == nil {
		fechavel.chamar(idxFechar)
		fechavel.soltar()
	}
	c.quadroRetido.soltar()
	c.quadroRetido = 0
}

func (c *CapturaDeJanela) Fechar() {
	if c == nil {
		return
	}
	c.SoltarQuadro()
	if c.sessao != 0 {
		if fechavel, err := c.sessao.consultar(&iidFechavel); err == nil {
			fechavel.chamar(idxFechar)
			fechavel.soltar()
		}
	}
	c.sessao.soltar()
	c.piscina.soltar()
	c.d3dWinRT.soltar()
	c.item.soltar()
	c.sessao, c.piscina, c.d3dWinRT, c.item = 0, 0, 0, 0
}
