package main

import (
	"runtime"
	"testing"
	"time"
)

func TestAjustarBandaSemReabrirOCompressor(t *testing.T) {
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
		bandaLarga   = 8000
		bandaApertada = 1000
		porSegundo   = 15
		segundos     = 3
	)

	c, err := AbrirCompressor(tela, 1280, 720, 60, bandaLarga)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()

	sintetica := abrirTelaSintetica(t, tela)
	defer sintetica.fechar()

	comeco := time.Now()
	medir := func() float64 {
		bytes := 0
		receber := func(pronto []byte, _ time.Duration) { bytes += len(pronto) }

		intervalo := time.Second / porSegundo
		inicio := time.Now()
		fim := inicio.Add(segundos * time.Second)
		for agora := time.Now(); agora.Before(fim); agora = time.Now() {
			volta := time.Now()
			sintetica.pintar(true)
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
		return float64(bytes) * 8 / time.Since(inicio).Seconds() / 1000
	}

	medir()
	larga := medir()

	if !c.AjustarBanda(bandaApertada) {
		t.Skipf("o compressor %q nao aceita mudar a banda em servico", c.Nome)
	}

	medir()
	apertada := medir()

	t.Logf("compressor %q · %.0f kbps contratados deram %.0f · depois de apertar para %d deram %.0f",
		c.Nome, float64(bandaLarga), larga, bandaApertada, apertada)

	if apertada >= larga/2 {
		t.Errorf("apertar a banda de %d para %d kbps em servico nao mudou o que sai: %.0f -> %.0f kbps",
			bandaLarga, bandaApertada, larga, apertada)
	}
}
