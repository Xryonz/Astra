package main

import (
	"bytes"
	"encoding/base64"
	"fmt"
	"image"
	"image/png"
	"sync"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	user32                = windows.NewLazySystemDLL("user32.dll")
	procDefinirCienciaDPI = user32.NewProc("SetProcessDpiAwarenessContext")
	procCienciaDPIAntiga  = user32.NewProc("SetProcessDPIAware")
)

const cienciaPorMonitorV2 = ^uintptr(3)

var avisarQueEntendemosDePixel = sync.OnceFunc(func() {
	if r, _, _ := procDefinirCienciaDPI.Call(cienciaPorMonitorV2); r != 0 {
		return
	}

	procCienciaDPIAntiga.Call()
})

const dxgiDescricaoDaSaida = 7

type descricaoDaSaida struct {
	Nome            [32]uint16
	Esquerda        int32
	Topo            int32
	Direita         int32
	Base            int32
	LigadoNaArea    int32
	Rotacao         uint32
	_               uint32
	IdentificadorHM uintptr
}

const LarguraDaMiniatura = 256

type MonitorDaTela struct {
	Indice    int    `json:"indice"`
	Nome      string `json:"nome"`
	Largura   int    `json:"largura"`
	Altura    int    `json:"altura"`
	Principal bool   `json:"principal"`

	Miniatura string `json:"miniatura,omitempty"`
}

func ListarMonitores() ([]MonitorDaTela, error) {
	achados, err := enumerarSaidas()
	if err != nil {
		return nil, err
	}
	for i := range achados {
		if png, err := amostrarMonitor(achados[i].Indice); err == nil {
			achados[i].Miniatura = base64.StdEncoding.EncodeToString(png)
		}

	}
	return achados, nil
}

func enumerarSaidas() ([]MonitorDaTela, error) {
	avisarQueEntendemosDePixel()

	var dispositivo, contexto objeto
	var nivel uint32
	r, _, _ := procCriarD3D11.Call(
		0, tipoDeDriverHardware, 0, 0, 0, 0, versaoDoSDKD3D11,
		uintptr(unsafe.Pointer(&dispositivo)),
		uintptr(unsafe.Pointer(&nivel)),
		uintptr(unsafe.Pointer(&contexto)),
	)
	if err := hr(r, "criar dispositivo para listar monitores"); err != nil {
		return nil, err
	}
	if err := naoNulo(dispositivo, "criar dispositivo para listar monitores"); err != nil {
		return nil, err
	}
	defer dispositivo.soltar()
	defer contexto.soltar()

	dispDXGI, err := dispositivo.consultar(&iidDispositivoDXGI)
	if err != nil {
		return nil, fmt.Errorf("o dispositivo não fala DXGI: %w", err)
	}
	defer dispDXGI.soltar()

	var adaptador objeto
	r = dispDXGI.chamar(dxgiPegarAdaptador, uintptr(unsafe.Pointer(&adaptador)))
	if err := hr(r, "achar a placa de vídeo"); err != nil {
		return nil, err
	}
	if err := naoNulo(adaptador, "achar a placa de vídeo"); err != nil {
		return nil, err
	}
	defer adaptador.soltar()

	var lista []MonitorDaTela
	for i := 0; i < 8; i++ {
		var saida objeto
		r = adaptador.chamar(dxgiEnumerarSaidas, uintptr(i), uintptr(unsafe.Pointer(&saida)))
		if uint32(r)&0x80000000 != 0 || saida == 0 {
			break
		}

		var desc descricaoDaSaida
		erroDesc := saida.chamar(dxgiDescricaoDaSaida, uintptr(unsafe.Pointer(&desc)))
		saida.soltar()
		if uint32(erroDesc)&0x80000000 != 0 {
			continue
		}
		if desc.LigadoNaArea == 0 {

			continue
		}

		lista = append(lista, MonitorDaTela{
			Indice:  i,
			Nome:    windows.UTF16ToString(desc.Nome[:]),
			Largura: int(desc.Direita - desc.Esquerda),
			Altura:  int(desc.Base - desc.Topo),

			Principal: desc.Esquerda == 0 && desc.Topo == 0,
		})
	}
	if len(lista) == 0 {
		return nil, fmt.Errorf("nenhum monitor ligado à área de trabalho")
	}
	return lista, nil
}

func amostrarMonitor(indice int) ([]byte, error) {
	tela, err := AbrirTela(indice)
	if err != nil {
		return nil, err
	}
	defer tela.Fechar()

	largura, altura := tela.Tamanho()
	if largura <= 0 || altura <= 0 {
		return nil, fmt.Errorf("o monitor %d não informou tamanho", indice)
	}

	var textura objeto
	for tentativa := 0; tentativa < 6; tentativa++ {
		t, err := tela.QuadroAtual(120)
		if err != nil {
			return nil, err
		}
		if t != 0 {
			if tentativa == 0 {

				t.soltar()
				tela.SoltarQuadro()
				continue
			}
			textura = t
			break
		}
		tela.SoltarQuadro()
	}
	if textura == 0 {
		return nil, fmt.Errorf("o monitor %d não entregou quadro", indice)
	}
	defer textura.soltar()
	defer tela.SoltarQuadro()

	leitura := descricaoDeTextura{
		Largura: uint32(largura), Altura: uint32(altura),
		Niveis: 1, Camadas: 1, Formato: formatoBGRA, AmostrasConta: 1,
		Uso: usoDeLeitura, AcessoDaCPU: cpuPodeLer,
	}
	var destino objeto
	r := tela.dispositivo.chamar(d3dCriarTextura2D,
		uintptr(unsafe.Pointer(&leitura)), 0, uintptr(unsafe.Pointer(&destino)))
	if err := hr(r, "criar a textura de leitura da miniatura"); err != nil {
		return nil, err
	}
	if err := naoNulo(destino, "criar a textura de leitura da miniatura"); err != nil {
		return nil, err
	}
	defer destino.soltar()

	tela.contexto.chamar(d3dCopiarTudo, uintptr(destino), uintptr(textura))

	var mapa mapaDaTextura
	r = tela.contexto.chamar(d3dMapear,
		uintptr(destino), 0, mapaDeLeitura, 0, uintptr(unsafe.Pointer(&mapa)))
	if err := hr(r, "abrir a cópia para leitura"); err != nil {
		return nil, err
	}
	if mapa.Dados == 0 {
		return nil, fmt.Errorf("o Map devolveu ponteiro nulo sem erro")
	}
	img := encolher(mapa.Dados, int(mapa.PassoLinha), largura, altura, LarguraDaMiniatura)
	tela.contexto.chamar(d3dDesmapear, uintptr(destino), 0)

	var buf bytes.Buffer

	cod := png.Encoder{CompressionLevel: png.BestSpeed}
	if err := cod.Encode(&buf, img); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

const AmostrasPorLado = 3

func encolher(dados uintptr, passo, largura, altura, alvoL int) *image.RGBA {
	if alvoL > largura {
		alvoL = largura
	}
	alvoA := altura * alvoL / largura
	if alvoA < 1 {
		alvoA = 1
	}
	fonte := unsafe.Slice((*byte)(unsafe.Pointer(dados)), passo*altura)
	img := image.NewRGBA(image.Rect(0, 0, alvoL, alvoA))

	for y := 0; y < alvoA; y++ {
		for x := 0; x < alvoL; x++ {
			var somaB, somaG, somaR, quantos int
			for sy := 0; sy < AmostrasPorLado; sy++ {

				oy := ((y*AmostrasPorLado+sy)*2 + 1) * altura / (2 * alvoA * AmostrasPorLado)
				if oy >= altura {
					oy = altura - 1
				}
				linha := oy * passo
				for sx := 0; sx < AmostrasPorLado; sx++ {
					ox := ((x*AmostrasPorLado+sx)*2 + 1) * largura / (2 * alvoL * AmostrasPorLado)
					if ox >= largura {
						ox = largura - 1
					}
					p := linha + ox*4
					if p+2 >= len(fonte) {
						continue
					}

					somaB += int(fonte[p])
					somaG += int(fonte[p+1])
					somaR += int(fonte[p+2])
					quantos++
				}
			}
			if quantos == 0 {
				continue
			}
			d := img.PixOffset(x, y)
			img.Pix[d] = byte(somaR / quantos)
			img.Pix[d+1] = byte(somaG / quantos)
			img.Pix[d+2] = byte(somaB / quantos)
			img.Pix[d+3] = 255
		}
	}
	return img
}
