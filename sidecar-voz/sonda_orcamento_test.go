package main

import (
	"math/rand"
	"os"
	"runtime"
	"testing"
	"time"
	"unsafe"
)

const (
	cpuPodeEscrever = 0x10000
	mapaDeEscrita   = 2
)

type telaDeRuido struct {
	textura objeto
	contexto objeto
	largura int
	altura  int
	fonte   []byte
	giro    int
}

func abrirTelaDeRuido(t *testing.T, tela *Tela) *telaDeRuido {
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
	if err := hr(r, "criar a textura de ruído"); err != nil {
		t.Fatalf("%v", err)
	}

	fonte := make([]byte, largura*4*2)
	rand.New(rand.NewSource(1)).Read(fonte)

	return &telaDeRuido{textura: textura, contexto: tela.contexto, largura: largura, altura: altura, fonte: fonte}
}

func (r *telaDeRuido) proximo() error {
	var mapa mapaDaTextura
	res := r.contexto.chamar(d3dMapear, uintptr(r.textura), 0, mapaDeEscrita, 0,
		uintptr(unsafe.Pointer(&mapa)))
	if err := hr(res, "abrir a textura de ruído para escrita"); err != nil {
		return err
	}

	linha := r.largura * 4
	for y := 0; y < r.altura; y++ {
		inicio := (r.giro + y*7) % r.largura * 4
		destino := unsafe.Slice((*byte)(unsafe.Pointer(mapa.Dados+uintptr(y)*uintptr(mapa.PassoLinha))), linha)
		copy(destino, r.fonte[inicio:inicio+linha])
	}
	r.giro = (r.giro + 13) % r.largura

	r.contexto.chamar(d3dDesmapear, uintptr(r.textura), 0)
	return nil
}

func (r *telaDeRuido) fechar() { r.textura.soltar() }

func precisaDeMedicaoDeOrcamento(t *testing.T) {
	t.Helper()
	if os.Getenv("ASTRA_MEDIR_ORCAMENTO") == "" {
		t.Skip("defina ASTRA_MEDIR_ORCAMENTO=1 (medição lenta; é o registro de como se chegou à taxa variável)")
	}
}

func TestSondaOrcamentoDeBitsPorRitmo(t *testing.T) {
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
		fpsDeclarado = 30
		kbps         = 2500
	)

	medir := func(porSegundo int) float64 {
		c, err := AbrirCompressor(tela, 1280, 720, fpsDeclarado, kbps)
		if err != nil {
			t.Fatalf("abrir o compressor: %v", err)
		}
		defer c.Fechar()

		ruido := abrirTelaDeRuido(t, tela)
		defer ruido.fechar()

		intervalo := time.Second / time.Duration(porSegundo)
		bytes, quadros := 0, 0
		receber := func(pronto []byte, _ time.Duration) {
			bytes += len(pronto)
			quadros++
		}

		comeco := time.Now()
		aquecer := comeco.Add(2 * time.Second)
		fim := aquecer.Add(5 * time.Second)
		bytesDepoisDoAquecimento, comecoDaConta := 0, time.Time{}

		for agora := time.Now(); agora.Before(fim); agora = time.Now() {
			volta := time.Now()
			if err := ruido.proximo(); err != nil {
				t.Fatalf("desenhar ruído: %v", err)
			}
			if err := c.Comprimir(ruido.textura, time.Since(comeco), nil, receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
			if comecoDaConta.IsZero() && time.Now().After(aquecer) {
				comecoDaConta = time.Now()
				bytesDepoisDoAquecimento = bytes
			}
			if espera := intervalo - time.Since(volta); espera > 0 {
				time.Sleep(espera)
			}
		}
		_ = c.Drenar(receber)

		if comecoDaConta.IsZero() {
			t.Fatal("a medição não passou do aquecimento")
		}
		decorrido := time.Since(comecoDaConta).Seconds()
		mbps := float64(bytes-bytesDepoisDoAquecimento) * 8 / decorrido / 1_000_000

		t.Logf("alimentando %d/s (declarado %d/s): %.2f Mbps · %d quadros no total",
			porSegundo, fpsDeclarado, mbps, quadros)
		return mbps
	}

	noRitmo := medir(fpsDeclarado)
	devagar := medir(8)

	alvo := float64(kbps) / 1000
	t.Logf("")
	t.Logf("contratado: %.2f Mbps", alvo)
	t.Logf("  alimentando no ritmo declarado: %.2f Mbps (%.0f%% do contratado)", noRitmo, noRitmo/alvo*100)
	t.Logf("  alimentando a 8/s:              %.2f Mbps (%.0f%% do contratado)", devagar, devagar/alvo*100)
	t.Logf("  se o orçamento fosse POR QUADRO com fps nominal, o segundo daria ~%.2f Mbps", alvo*8/fpsDeclarado)

	if devagar < noRitmo*0.6 {
		t.Logf("VEREDITO: o orçamento SEGUE O NÚMERO DE QUADROS, não o tempo — alimentar devagar desperdiça banda contratada")
	} else {
		t.Logf("VEREDITO: o orçamento segue o TEMPO — não há desperdício, e a taxa baixa da tela parada é correta")
	}
}
