package main

import (
	"fmt"
	"os"
	"syscall"
	"testing"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	combase                    = windows.NewLazySystemDLL("combase.dll")
	procRoInitialize           = combase.NewProc("RoInitialize")
	procRoUninitialize         = combase.NewProc("RoUninitialize")
	procRoGetActivationFactory = combase.NewProc("RoGetActivationFactory")
	procCriarString            = combase.NewProc("WindowsCreateString")
	procSoltarString           = combase.NewProc("WindowsDeleteString")

	procEnumWindows     = user32.NewProc("EnumWindows")
	procVisivel         = user32.NewProc("IsWindowVisible")
	procTextoDaJanela   = user32.NewProc("GetWindowTextW")
	procTamanhoDoTexto  = user32.NewProc("GetWindowTextLengthW")
	procRetanguloJanela = user32.NewProc("GetWindowRect")

	procD3DDeDispositivo   = d3d11.NewProc("CreateDirect3D11DeviceFromDXGIDevice")
	iidItemDeCapturaInterop = guid(0x3628E81B, 0x3CAC, 0x4C60,
		[8]byte{0xB7, 0xF4, 0x23, 0xCE, 0x0E, 0x0C, 0x33, 0x56})
	iidItemDeCaptura = guid(0x79C3F95B, 0x31F7, 0x4EC2,
		[8]byte{0xA4, 0x64, 0x63, 0x2E, 0xF5, 0xD3, 0x07, 0x60})
	iidPiscinaStatics = guid(0x7784056A, 0x67AA, 0x4D53,
		[8]byte{0xAE, 0x54, 0x10, 0x88, 0xD5, 0xA8, 0xCA, 0x21})
	iidPiscinaStatics2 = guid(0x589B103F, 0x6BBC, 0x5DF5,
		[8]byte{0xA9, 0x91, 0x02, 0xE2, 0x8B, 0x3B, 0x66, 0xD5})
	iidPiscina = guid(0x24EB6D22, 0x1975, 0x422E,
		[8]byte{0x82, 0xE7, 0x78, 0x0D, 0xBD, 0x8D, 0xDF, 0x24})
	iidSessaoDeCaptura = guid(0x814E42A9, 0xF70F, 0x4AD7,
		[8]byte{0x93, 0x9B, 0xFD, 0xDC, 0xC6, 0xEB, 0x88, 0x0D})
	iidQuadroDeCaptura = guid(0xFA50C623, 0x38DA, 0x4B32,
		[8]byte{0xAC, 0xF3, 0xFA, 0x97, 0x34, 0xAD, 0x80, 0x0E})
	iidAcessoDxgi = guid(0xA9B3D012, 0x3DF2, 0x4EE3,
		[8]byte{0xB8, 0xD1, 0x86, 0x95, 0xF4, 0x57, 0xD3, 0xC1})
	iidDispositivoDxgi = guid(0x54EC77FA, 0x1377, 0x44E6,
		[8]byte{0x8C, 0x32, 0x88, 0xFD, 0x5F, 0x44, 0xC8, 0x4C})
)

type tamanhoInt32 struct{ Largura, Altura int32 }

type retangulo struct{ Esq, Topo, Dir, Base int32 }

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

func primeiraJanelaVisivel(t *testing.T) (uintptr, string, int32, int32) {
	var achada uintptr
	var titulo string
	cb := syscall.NewCallback(func(h uintptr, _ uintptr) uintptr {
		if achada != 0 {
			return 0
		}
		vis, _, _ := procVisivel.Call(h)
		if vis == 0 {
			return 1
		}
		n, _, _ := procTamanhoDoTexto.Call(h)
		if n < 3 {
			return 1
		}
		buf := make([]uint16, n+1)
		procTextoDaJanela.Call(h, uintptr(unsafe.Pointer(&buf[0])), n+1)
		nome := windows.UTF16ToString(buf)
		var r retangulo
		procRetanguloJanela.Call(h, uintptr(unsafe.Pointer(&r)))
		if r.Dir-r.Esq < 200 || r.Base-r.Topo < 200 {
			return 1
		}
		achada, titulo = h, nome
		return 0
	})
	procEnumWindows.Call(cb, 0)
	if achada == 0 {
		t.Skip("nenhuma janela visível com título e tamanho razoável")
	}
	var r retangulo
	procRetanguloJanela.Call(achada, uintptr(unsafe.Pointer(&r)))
	return achada, titulo, r.Dir - r.Esq, r.Base - r.Topo
}

func TestSondaCapturaDeJanela(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_VIDEO") == "" {
		t.Skip("defina ASTRA_TESTE_VIDEO=1 (precisa de placa de vídeo de verdade)")
	}
	defer PrenderNaThread()()

	r, _, _ := procRoInitialize.Call(1)
	if uint32(r) == 0x80010106 {
		t.Log("PASSO 0: o runtime já estava iniciado nesta thread com outro modelo")
	} else if err := hr(r, "RoInitialize"); err != nil {
		t.Fatalf("PASSO 0 FALHOU (o WinRT nem sobe): %v", err)
	} else {
		defer procRoUninitialize.Call()
	}
	t.Log("PASSO 0 ok: WinRT iniciado")

	interop, err := fabricaDe("Windows.Graphics.Capture.GraphicsCaptureItem", &iidItemDeCapturaInterop)
	if err != nil {
		t.Fatalf("PASSO 1 FALHOU (sem a fábrica de item de captura): %v", err)
	}
	defer interop.soltar()
	t.Log("PASSO 1 ok: IGraphicsCaptureItemInterop obtido")

	janela, titulo, largura, altura := primeiraJanelaVisivel(t)
	t.Logf("janela escolhida: %q, %dx%d", titulo, largura, altura)

	const criarParaJanela = 3
	var item objeto
	r = interop.chamar(criarParaJanela, janela,
		uintptr(unsafe.Pointer(&iidItemDeCaptura)),
		uintptr(unsafe.Pointer(&item)),
	)
	if err := hr(r, "CreateForWindow"); err != nil {
		t.Fatalf("PASSO 2 FALHOU (não deu para virar item de captura): %v", err)
	}
	defer item.soltar()
	t.Log("PASSO 2 ok: a janela virou um GraphicsCaptureItem")

	tela, err := AbrirTela(0)
	if err != nil {
		t.Fatalf("abrir a captura de tela (só para pegar o dispositivo D3D): %v", err)
	}
	defer tela.Fechar()

	dxgi, err := tela.dispositivo.consultar(&iidDispositivoDxgi)
	if err != nil {
		t.Fatalf("PASSO 3 FALHOU (o dispositivo D3D não fala DXGI): %v", err)
	}
	defer dxgi.soltar()

	var d3dWinRT objeto
	r, _, _ = procD3DDeDispositivo.Call(uintptr(dxgi), uintptr(unsafe.Pointer(&d3dWinRT)))
	if err := hr(r, "CreateDirect3D11DeviceFromDXGIDevice"); err != nil {
		t.Fatalf("PASSO 3 FALHOU (não deu para embrulhar o dispositivo para o WinRT): %v", err)
	}
	defer d3dWinRT.soltar()
	t.Log("PASSO 3 ok: o MESMO dispositivo D3D da transmissão foi embrulhado para o WinRT")

	statics, err := fabricaDe("Windows.Graphics.Capture.Direct3D11CaptureFramePool", &iidPiscinaStatics2)
	livre := true
	if err != nil {
		statics, err = fabricaDe("Windows.Graphics.Capture.Direct3D11CaptureFramePool", &iidPiscinaStatics)
		livre = false
		if err != nil {
			t.Fatalf("PASSO 4 FALHOU (sem a fábrica da piscina de quadros): %v", err)
		}
	}
	defer statics.soltar()
	t.Logf("PASSO 4 ok: fábrica da piscina obtida (CreateFreeThreaded disponível: %v)", livre)

	const criar = 6
	const formatoBGRA8 = 87
	var piscina objeto
	tamanhoEmRegistrador := uintptr(uint32(largura)) | uintptr(uint32(altura))<<32
	r = statics.chamar(criar,
		uintptr(d3dWinRT),
		uintptr(formatoBGRA8),
		2,
		tamanhoEmRegistrador,
		uintptr(unsafe.Pointer(&piscina)),
	)
	if err := hr(r, "criar a piscina"); err != nil {
		t.Fatalf("PASSO 5 FALHOU (não deu para criar a piscina de quadros): %v", err)
	}
	defer piscina.soltar()
	t.Log("PASSO 5 ok: piscina de quadros criada")

	const criarSessao = 10
	var sessao objeto
	r = piscina.chamar(criarSessao, uintptr(item), uintptr(unsafe.Pointer(&sessao)))
	if err := hr(r, "CreateCaptureSession"); err != nil {
		t.Fatalf("PASSO 6 FALHOU (não deu para abrir a sessão): %v", err)
	}
	defer sessao.soltar()

	const comecar = 6
	if err := hr(sessao.chamar(comecar), "StartCapture"); err != nil {
		t.Fatalf("PASSO 6 FALHOU (a sessão não começou): %v", err)
	}
	t.Log("PASSO 6 ok: sessão de captura iniciada")

	const pegarQuadro = 7
	var quadro objeto
	fim := time.Now().Add(3 * time.Second)
	for time.Now().Before(fim) {
		r = piscina.chamar(pegarQuadro, uintptr(unsafe.Pointer(&quadro)))
		if err := hr(r, "TryGetNextFrame"); err != nil {
			t.Fatalf("PASSO 7 FALHOU: %v", err)
		}
		if quadro != 0 {
			break
		}
		time.Sleep(30 * time.Millisecond)
	}
	if quadro == 0 {
		t.Fatal("PASSO 7 FALHOU: nenhum quadro em 3s (a janela pode estar minimizada)")
	}
	defer quadro.soltar()
	t.Log("PASSO 7 ok: um quadro chegou")

	const pegarSuperficie = 6
	var superficie objeto
	if err := hr(quadro.chamar(pegarSuperficie, uintptr(unsafe.Pointer(&superficie))), "get_Surface"); err != nil {
		t.Fatalf("PASSO 8 FALHOU: %v", err)
	}
	defer superficie.soltar()

	acesso, err := superficie.consultar(&iidAcessoDxgi)
	if err != nil {
		t.Fatalf("PASSO 8 FALHOU (a superfície não expõe DXGI): %v", err)
	}
	defer acesso.soltar()

	const pegarInterface = 3
	var textura objeto
	r = acesso.chamar(pegarInterface,
		uintptr(unsafe.Pointer(&iidTextura2D)),
		uintptr(unsafe.Pointer(&textura)),
	)
	if err := hr(r, "GetInterface(ID3D11Texture2D)"); err != nil {
		t.Fatalf("PASSO 8 FALHOU: %v", err)
	}
	defer textura.soltar()

	var desc descricaoDeTextura
	const pegarDescricao = 10
	textura.chamar(pegarDescricao, uintptr(unsafe.Pointer(&desc)))

	t.Log("PASSO 8 ok: a textura D3D11 saiu do quadro")
	t.Logf("")
	t.Logf("RESPOSTA: dá para capturar UMA JANELA a partir do Go.")
	t.Logf("  janela .......... %q", titulo)
	t.Logf("  textura ......... %dx%d, formato %d", desc.Largura, desc.Altura, desc.Formato)
	t.Logf("  mesmo formato do que o compressor já recebe? %v",
		desc.Formato == formatoBGRA)
	if desc.Largura != uint32(largura) || desc.Altura != uint32(altura) {
		t.Logf("  ATENÇÃO: a textura (%dx%d) não bate com o retângulo da janela (%dx%d)",
			desc.Largura, desc.Altura, largura, altura)
	}
	fmt.Fprintln(os.Stderr, "sonda de janela concluída")
}
