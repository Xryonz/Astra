package main

import (
	"runtime"
	"testing"
	"time"
)

func TestAbrirACameraELerQuadros(t *testing.T) {
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

	lista, err := ListarCameras()
	if err != nil {
		t.Fatalf("listar câmeras: %v", err)
	}
	if len(lista) == 0 {
		t.Skip("nenhuma câmera nesta máquina")
	}
	t.Logf("usando: %s", lista[0].Nome)

	cam, err := AbrirCamera(lista[0].Id)
	if err != nil {
		t.Fatalf("abrir a câmera em NV12 %dx%d: %v", CameraLargura, CameraAltura, err)
	}
	defer cam.Fechar()

	for _, f := range cam.FormatosNativos() {
		t.Logf("  formato nativo: %s", f)
	}

	const paraAquecer = 10

	abertura := time.Now()
	quadros, vazios := 0, 0
	var comecouAMedir time.Time

	fim := time.Now().Add(6 * time.Second)
	for time.Now().Before(fim) && quadros < 60 {
		amostra, err := cam.ProximaAmostra()
		if err != nil {
			t.Fatalf("ler quadro %d: %v", quadros, err)
		}
		if amostra == 0 {
			vazios++
			continue
		}
		amostra.soltar()

		quadros++
		if quadros == paraAquecer {
			comecouAMedir = time.Now()
			t.Logf("primeiro quadro utilizavel depois de %v", time.Since(abertura).Round(time.Millisecond))
		}
	}

	if quadros <= paraAquecer {
		t.Fatalf("so %d quadros: a camera abriu e praticamente nao entrega", quadros)
	}

	medidos := quadros - paraAquecer
	decorrido := time.Since(comecouAMedir)
	fps := float64(medidos) / decorrido.Seconds()

	t.Logf("%d quadros em regime, %v, %.1f por segundo (%d leituras vazias)",
		medidos, decorrido.Round(time.Millisecond), fps, vazios)

	if fps < CameraFps/2 {
		t.Errorf("%.1f quadros por segundo: pedimos %d e a camera nao acompanha nem a metade", fps, CameraFps)
	}
}

func TestQuantosQuadrosACameraDaEmCadaTamanho(t *testing.T) {
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

	lista, err := ListarCameras()
	if err != nil || len(lista) == 0 {
		t.Skipf("nenhuma câmera: %v", err)
	}

	for _, tamanho := range []struct{ l, a int }{{1280, 720}, {640, 360}} {
		cam, err := AbrirCameraEm(lista[0].Id, tamanho.l, tamanho.a, CameraFps)
		if err != nil {
			t.Errorf("%dx%d nao abriu: %v", tamanho.l, tamanho.a, err)
			continue
		}

		const aquecer = 10
		quadros := 0
		var marco time.Time
		fim := time.Now().Add(5 * time.Second)
		for time.Now().Before(fim) && quadros < 50 {
			amostra, err := cam.ProximaAmostra()
			if err != nil {
				break
			}
			if amostra == 0 {
				continue
			}
			amostra.soltar()
			quadros++
			if quadros == aquecer {
				marco = time.Now()
			}
		}
		cam.Fechar()

		if quadros <= aquecer {
			t.Errorf("%dx%d entregou so %d quadros", tamanho.l, tamanho.a, quadros)
			continue
		}
		fps := float64(quadros-aquecer) / time.Since(marco).Seconds()
		t.Logf("%dx%d -> %.1f quadros por segundo", tamanho.l, tamanho.a, fps)
	}
}
