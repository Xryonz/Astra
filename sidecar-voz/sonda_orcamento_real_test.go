package main

import (
	"runtime"
	"sort"
	"testing"
	"time"
	"unsafe"
)

type telaSintetica struct {
	textura  objeto
	contexto objeto
	largura  int
	altura   int
	quadro   []byte
	n        int
}

func abrirTelaSintetica(t *testing.T, tela *Tela) *telaSintetica {
	t.Helper()

	largura, altura := tela.Tamanho()
	desc := descricaoDeTextura{
		Largura:       uint32(largura),
		Altura:        uint32(altura),
		Niveis:        1,
		Camadas:       1,
		Formato:       formatoBGRA,
		AmostrasConta: 1,
		Uso:           usoDeLeitura,
		AcessoDaCPU:   cpuPodeEscrever,
	}

	var textura objeto
	r := tela.dispositivo.chamar(d3dCriarTextura2D,
		uintptr(unsafe.Pointer(&desc)), 0, uintptr(unsafe.Pointer(&textura)))
	if err := hr(r, "criar a textura sintética"); err != nil {
		t.Fatalf("%v", err)
	}

	return &telaSintetica{
		textura:  textura,
		contexto: tela.contexto,
		largura:  largura,
		altura:   altura,
		quadro:   make([]byte, largura*altura*4),
	}
}

func (s *telaSintetica) pintar(repintar bool) {
	linha := s.largura * 4

	if repintar || s.n == 0 {
		for i := 0; i < len(s.quadro); i += 4 {
			s.quadro[i], s.quadro[i+1], s.quadro[i+2], s.quadro[i+3] = 0x24, 0x20, 0x1E, 0xFF
		}
		desvio := (s.n * 37) % 19
		for y := 40 + desvio; y < s.altura-40; y += 26 {
			largo := 200 + (y*97)%(s.largura-400)
			for x := 60; x < 60+largo; x++ {
				if (x/7+y)%11 == 0 {
					continue
				}
				i := y*linha + x*4
				s.quadro[i], s.quadro[i+1], s.quadro[i+2] = 0xEB, 0xE4, 0xE4
			}
		}
	}

	altoDoBloco, largoDoBloco := 180, 320
	x0 := (s.n * 23) % (s.largura - largoDoBloco - 1)
	y0 := s.altura/2 - altoDoBloco/2
	for y := y0; y < y0+altoDoBloco; y++ {
		for x := x0; x < x0+largoDoBloco; x++ {
			i := y*linha + x*4
			v := byte((x*3 + y*5 + s.n*11) % 251)
			s.quadro[i], s.quadro[i+1], s.quadro[i+2] = v, v/2, 0xFF-v
		}
	}

	s.n++
}

func (s *telaSintetica) enviar() error {
	var mapa mapaDaTextura
	res := s.contexto.chamar(d3dMapear, uintptr(s.textura), 0, mapaDeEscrita, 0,
		uintptr(unsafe.Pointer(&mapa)))
	if err := hr(res, "abrir a textura sintética para escrita"); err != nil {
		return err
	}

	linha := s.largura * 4
	for y := 0; y < s.altura; y++ {
		destino := unsafe.Slice((*byte)(unsafe.Pointer(mapa.Dados+uintptr(y)*uintptr(mapa.PassoLinha))), linha)
		copy(destino, s.quadro[y*linha:(y+1)*linha])
	}

	s.contexto.chamar(d3dDesmapear, uintptr(s.textura), 0)
	return nil
}

func (s *telaSintetica) fechar() { s.textura.soltar() }

func TestSondaOrcamentoComTelaRealista(t *testing.T) {
	precisaDeMedicaoDeOrcamento(t)
	precisaDeTela(t)
	precisaDeVideo(t)

	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		t.Fatalf("iniciar Media Foundation: %v", err)
	}
	defer fecharMF()

	tela, err := AbrirTela(0)
	if err != nil {
		t.Fatalf("abrir a tela: %v", err)
	}
	defer tela.Fechar()

	const (
		kbps        = 2500
		porSegundo  = 8
		segundos    = 8
		repintarCada = 2 * time.Second
	)

	medir := func(fpsDeclarado int) (mbps float64, maior int, acimaDoOrcamento int, tamanhos []int) {
		c, err := AbrirCompressor(tela, 1280, 720, fpsDeclarado, kbps)
		if err != nil {
			t.Fatalf("abrir o compressor a %d/s declarados: %v", fpsDeclarado, err)
		}
		defer c.Fechar()

		sintetica := abrirTelaSintetica(t, tela)
		defer sintetica.fechar()

		orcamento := kbps * 1000 / fpsDeclarado / 8

		bytes := 0
		receber := func(pronto []byte, _ time.Duration) {
			bytes += len(pronto)
			tamanhos = append(tamanhos, len(pronto))
			if len(pronto) > maior {
				maior = len(pronto)
			}
			if len(pronto) > orcamento {
				acimaDoOrcamento++
			}
		}

		intervalo := time.Second / porSegundo
		comeco := time.Now()
		fim := comeco.Add(segundos * time.Second)
		proximaRepintura := comeco.Add(repintarCada)

		for agora := time.Now(); agora.Before(fim); agora = time.Now() {
			volta := time.Now()
			repintar := volta.After(proximaRepintura)
			if repintar {
				proximaRepintura = volta.Add(repintarCada)
			}
			sintetica.pintar(repintar)
			if err := sintetica.enviar(); err != nil {
				t.Fatalf("desenhar: %v", err)
			}
			if err := c.Comprimir(sintetica.textura, time.Since(comeco), nil, receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
			if espera := intervalo - time.Since(volta); espera > 0 {
				time.Sleep(espera)
			}
		}
		_ = c.Drenar(receber)

		mbps = float64(bytes) * 8 / time.Since(comeco).Seconds() / 1_000_000
		return mbps, maior, acimaDoOrcamento, tamanhos
	}

	mediana := func(v []int) int {
		if len(v) == 0 {
			return 0
		}
		c := append([]int(nil), v...)
		sort.Ints(c)
		return c[len(c)/2]
	}

	comoEHoje, maiorHoje, acimaHoje, tamHoje := medir(30)
	comoSeria, maiorFix, acimaFix, tamFix := medir(porSegundo)

	t.Logf("tela realista, alimentada a %d/s, contratados %d kbps", porSegundo, kbps)
	t.Logf("")
	t.Logf("HOJE (declarando 30/s, orçamento %d bytes/quadro):", kbps*1000/30/8)
	t.Logf("  %.2f Mbps · maior quadro %d B · mediana %d B · %d quadros acima do orçamento",
		comoEHoje, maiorHoje, mediana(tamHoje), acimaHoje)
	t.Logf("COM O CONSERTO (declarando %d/s, orçamento %d bytes/quadro):", porSegundo, kbps*1000/porSegundo/8)
	t.Logf("  %.2f Mbps · maior quadro %d B · mediana %d B · %d quadros acima do orçamento",
		comoSeria, maiorFix, mediana(tamFix), acimaFix)
	t.Logf("")

	ganho := comoSeria / comoEHoje
	t.Logf("VEREDITO: o conserto entregaria %.1fx os bits de hoje neste conteúdo", ganho)
	if ganho < 1.2 {
		t.Logf("  ou seja: o teto por quadro NÃO estava apertando — o conteúdo não queria mais bits")
	} else {
		t.Logf("  ou seja: o teto por quadro ESTAVA apertando de verdade")
	}
}
