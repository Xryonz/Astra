package main

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"unsafe"
)

func TestTamanhoDeSaida(t *testing.T) {
	casos := []struct {
		nome                 string
		largura, altura      int
		esperadaL, esperadaA int
	}{
		{"1080p deitado vira 720p", 1920, 1080, 1280, 720},
		{"1080p em pé vira 720 de largura", 1080, 1920, 720, 1280},
		{"4K vira 720p", 3840, 2160, 1280, 720},
		{"já pequeno fica como está", 640, 360, 640, 360},
		{"ultrawide para no lado maior", 2560, 1080, 1280, 540},
		{"medida ímpar vira par", 1000, 721, 998, 720},
	}
	for _, c := range casos {
		l, a := tamanhoDeSaida(c.largura, c.altura, 720)
		if l != c.esperadaL || a != c.esperadaA {
			t.Errorf("%s: %dx%d deu %dx%d, esperado %dx%d", c.nome, c.largura, c.altura, l, a, c.esperadaL, c.esperadaA)
		}
	}
}

func amostraComDados(t *testing.T, dados []byte, instante, duracao int64) objeto {
	t.Helper()
	var buffer objeto
	r, _, _ := procMFCriarBufferDeMemoria.Call(uintptr(len(dados)), uintptr(unsafe.Pointer(&buffer)))
	if err := hr(r, "criar o buffer"); err != nil {
		t.Fatal(err)
	}
	defer buffer.soltar()
	var ponteiro *byte
	var maximo, atual uint32
	if err := hr(buffer.chamar(bufTrancar, uintptr(unsafe.Pointer(&ponteiro)),
		uintptr(unsafe.Pointer(&maximo)), uintptr(unsafe.Pointer(&atual))), "trancar o buffer"); err != nil {
		t.Fatal(err)
	}
	copy(unsafe.Slice(ponteiro, len(dados)), dados)
	buffer.chamar(bufDestrancar)
	buffer.chamar(bufDefinirTamanho, uintptr(len(dados)))

	var amostra objeto
	r, _, _ = procMFCriarAmostra.Call(uintptr(unsafe.Pointer(&amostra)))
	if err := hr(r, "criar a amostra"); err != nil {
		t.Fatal(err)
	}
	amostra.chamar(amostraSomarBuffer, uintptr(buffer))
	amostra.chamar(amostraDefinirTempo, uintptr(instante))
	amostra.chamar(amostraDefinirDuracao, uintptr(duracao))
	return amostra
}

func gravarVideoSintetico(t *testing.T, caminho string, largura, altura, segundos int) {
	t.Helper()
	const fps, amostrasDeSom, canais = 30, 48000, 2
	taxa := uint64(fps)<<32 | 1

	gravador, err := abrirGravador(caminho)
	if err != nil {
		t.Fatal(err)
	}
	defer gravador.soltar()

	saidaVideo, _ := tipoH264(largura, altura, taxa, 8000)
	defer saidaVideo.soltar()
	entradaVideo, _ := tipoNV12(largura, altura, taxa)
	defer entradaVideo.soltar()
	fluxoVideo, err := somarFluxo(gravador, saidaVideo, entradaVideo)
	if err != nil {
		t.Fatal(err)
	}

	saidaSom, _ := tipoAAC(amostrasDeSom, canais, 128)
	defer saidaSom.soltar()
	entradaSom, _ := tipoPCM(amostrasDeSom, canais)
	defer entradaSom.soltar()
	fluxoSom, err := somarFluxo(gravador, saidaSom, entradaSom)
	if err != nil {
		t.Fatal(err)
	}

	if err := hr(gravador.chamar(gravadorComecar), "começar"); err != nil {
		t.Fatal(err)
	}

	quadro := make([]byte, largura*altura*3/2)
	som := make([]byte, amostrasDeSom/fps*canais*2)
	const duracaoDoQuadro = int64(10_000_000 / fps)
	for i := 0; i < segundos*fps; i++ {
		for y := 0; y < altura; y++ {
			linha := quadro[y*largura : (y+1)*largura]
			for x := range linha {
				linha[x] = byte((x + y + i*8) % 256)
			}
		}
		for j := largura * altura; j < len(quadro); j++ {
			quadro[j] = byte(128 + (j/2+i)%64)
		}
		instante := int64(i) * duracaoDoQuadro
		for _, par := range []struct {
			fluxo uint32
			dados []byte
		}{{fluxoVideo, quadro}, {fluxoSom, som}} {
			amostra := amostraComDados(t, par.dados, instante, duracaoDoQuadro)
			err := hr(gravador.chamar(gravadorEscrever, uintptr(par.fluxo), uintptr(amostra)), "gravar amostra")
			amostra.soltar()
			if err != nil {
				t.Fatal(err)
			}
		}
	}
	if err := hr(gravador.chamar(gravadorFinalizar), "fechar"); err != nil {
		t.Fatal(err)
	}
}

func TestComprimirVideoDeVerdade(t *testing.T) {
	precisaDeVideo(t)
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err := abrirCOM(); err != nil {
		t.Fatal(err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		t.Fatal(err)
	}
	defer fecharMF()

	pasta := t.TempDir()
	original := filepath.Join(pasta, "original.mp4")
	comprimido := filepath.Join(pasta, "comprimido.mp4")
	gravarVideoSintetico(t, original, 1920, 1080, 3)

	ficha, err := SondarVideo(original)
	if err != nil {
		t.Fatal(err)
	}
	if ficha.Largura != 1920 || ficha.Altura != 1080 || !ficha.TemSom || ficha.DuracaoMs < 2900 {
		t.Fatalf("o original saiu errado: %+v", ficha)
	}

	passos := 0
	if err := ComprimirVideo(original, comprimido, AjustesDaCompressao{LadoMenor: 720, Kbps: 2500, KbpsDoSom: 128},
		func(float64) { passos++ }); err != nil {
		t.Fatal(err)
	}

	depois, err := SondarVideo(comprimido)
	if err != nil {
		t.Fatal(err)
	}
	if depois.Largura != 1280 || depois.Altura != 720 || !depois.TemSom || depois.DuracaoMs < 2900 {
		t.Fatalf("o comprimido saiu errado: %+v", depois)
	}
	antes, _ := os.Stat(original)
	agora, _ := os.Stat(comprimido)
	t.Logf("original %d bytes, comprimido %d bytes, %d avisos de andamento", antes.Size(), agora.Size(), passos)
	if agora.Size() >= antes.Size() {
		t.Errorf("o comprimido não ficou menor: %d >= %d", agora.Size(), antes.Size())
	}
	if passos == 0 {
		t.Error("nenhum aviso de andamento")
	}
}
