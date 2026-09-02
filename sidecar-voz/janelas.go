package main

import (
	"encoding/base64"
	"fmt"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	procEnumerarJanelas   = user32.NewProc("EnumWindows")
	procJanelaVisivel     = user32.NewProc("IsWindowVisible")
	procTextoDaJanela     = user32.NewProc("GetWindowTextW")
	procTamanhoDoTexto    = user32.NewProc("GetWindowTextLengthW")
	procRetanguloDaJanela = user32.NewProc("GetWindowRect")
	procEstiloEstendido   = user32.NewProc("GetWindowLongPtrW")
	procJanelaMinimizada  = user32.NewProc("IsIconic")

	dwmapi            = windows.NewLazySystemDLL("dwmapi.dll")
	procAtributoDoDwm = dwmapi.NewProc("DwmGetWindowAttribute")
)

const (
	indiceEstiloEstendido = ^uintptr(0) - 19
	estiloDeFerramenta    = 0x00000080
	atributoEncoberta     = 14
	atributoMolduraReal   = 9
	larguraMinimaDaJanela = 160
	alturaMinimaDaJanela  = 120
)

type retanguloDaJanela struct{ Esq, Topo, Dir, Base int32 }

type JanelaDaTela struct {
	Identificador uint64 `json:"id"`
	Nome          string `json:"nome"`
	Largura       int    `json:"largura"`
	Altura        int    `json:"altura"`

	Miniatura string `json:"miniatura,omitempty"`
}

func ListarJanelas() ([]JanelaDaTela, error) {
	achadas := enumerarJanelas()
	for i := range achadas {
		png, err := amostrarJanela(uintptr(achadas[i].Identificador), achadas[i].Largura, achadas[i].Altura)
		if err != nil {
			continue
		}
		achadas[i].Miniatura = base64.StdEncoding.EncodeToString(png)
	}
	return achadas, nil
}

func enumerarJanelas() []JanelaDaTela {
	var lista []JanelaDaTela
	cb := syscall.NewCallback(func(h uintptr, _ uintptr) uintptr {
		j, ok := descreverJanela(h)
		if ok {
			lista = append(lista, j)
		}
		return 1
	})
	procEnumerarJanelas.Call(cb, 0)
	return lista
}

func molduraVisivel(h uintptr) (int, int, bool) {
	var r retanguloDaJanela
	res, _, _ := procAtributoDoDwm.Call(h, atributoMolduraReal,
		uintptr(unsafe.Pointer(&r)), unsafe.Sizeof(r))
	if res != 0 || r.Dir <= r.Esq || r.Base <= r.Topo {
		if ok, _, _ := procRetanguloDaJanela.Call(h, uintptr(unsafe.Pointer(&r))); ok == 0 {
			return 0, 0, false
		}
	}
	return int(r.Dir - r.Esq), int(r.Base - r.Topo), true
}

func descreverJanela(h uintptr) (JanelaDaTela, bool) {
	avisarQueEntendemosDePixel()

	var vazia JanelaDaTela

	if vis, _, _ := procJanelaVisivel.Call(h); vis == 0 {
		return vazia, false
	}
	if min, _, _ := procJanelaMinimizada.Call(h); min != 0 {
		return vazia, false
	}

	estilo, _, _ := procEstiloEstendido.Call(h, uintptr(indiceEstiloEstendido))
	if estilo&estiloDeFerramenta != 0 {
		return vazia, false
	}

	var encoberta uint32
	procAtributoDoDwm.Call(h, atributoEncoberta,
		uintptr(unsafe.Pointer(&encoberta)), unsafe.Sizeof(encoberta))
	if encoberta != 0 {
		return vazia, false
	}

	n, _, _ := procTamanhoDoTexto.Call(h)
	if n == 0 {
		return vazia, false
	}
	buf := make([]uint16, n+1)
	procTextoDaJanela.Call(h, uintptr(unsafe.Pointer(&buf[0])), n+1)
	nome := windows.UTF16ToString(buf)
	if nome == "" {
		return vazia, false
	}

	largura, altura, ok := molduraVisivel(h)
	if !ok {
		return vazia, false
	}
	if largura < larguraMinimaDaJanela || altura < alturaMinimaDaJanela {
		return vazia, false
	}

	return JanelaDaTela{
		Identificador: uint64(h),
		Nome:          nome,
		Largura:       largura,
		Altura:        altura,
	}, true
}

func amostrarJanela(h uintptr, largura, altura int) ([]byte, error) {
	tela, err := AbrirJanela(h, largura, altura)
	if err != nil {
		return nil, err
	}
	defer tela.Fechar()

	l, a := tela.Tamanho()
	var textura objeto
	for tentativa := 0; tentativa < 8 && textura == 0; tentativa++ {
		t, err := tela.ProximoQuadro(120)
		if err != nil {
			return nil, err
		}
		if t == 0 {
			continue
		}
		textura = t
	}
	if textura == 0 {
		return nil, fmt.Errorf("a janela não entregou quadro")
	}
	defer textura.soltar()
	defer tela.SoltarQuadro()

	return miniaturaDe(tela, textura, l, a)
}
