package main

import (
	"os"
	"runtime"
	"testing"
	"time"
)

func TestSondaPrioridade(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_PRIORIDADE") == "" {
		t.Skip("ASTRA_SONDA_PRIORIDADE nao definida — sonda de investigacao, nao roda no dia a dia")
	}
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

	const preparados = 12
	prontos := make([]*telaSintetica, preparados)
	for i := range prontos {
		prontos[i] = abrirTelaSintetica(t, tela)
		prontos[i].n = i * 7
		prontos[i].pintar(true)
		if err := prontos[i].enviar(); err != nil {
			t.Fatalf("preparar o quadro %d: %v", i, err)
		}
	}
	defer func() {
		for _, p := range prontos {
			p.fechar()
		}
	}()
	defer func() { _ = definirEstrangulamento(0, 0) }()

	const largura, altura, fps, kbps = 1920, 1080, 60, 8000
	medir := func(nome string, controle, estado uint32) {
		if err := definirEstrangulamento(controle, estado); err != nil {
			t.Fatalf("%s: mudar a prioridade: %v", nome, err)
		}
		c, err := AbrirCompressor(tela, largura, altura, fps, kbps)
		if err != nil {
			t.Fatalf("abrir o compressor: %v", err)
		}
		defer c.Fechar()

		receber := func([]byte, time.Duration) {}
		ritmo := NovoRitmo(fps)
		for i := 0; i < fps; i++ {
			ritmo.Esperar()
			_ = c.Comprimir(prontos[i%preparados].textura, time.Duration(i)*time.Second/time.Duration(fps), nil, receber)
		}

		antesCpu, antes := cpuGasta(t), time.Now()
		ultimo, pior := antes, time.Duration(0)
		voltas := fps * 10
		for i := 0; i < voltas; i++ {
			ritmo.Esperar()
			if err := c.Comprimir(prontos[i%preparados].textura,
				time.Duration(i)*time.Second/time.Duration(fps), nil, receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
			agora := time.Now()
			if d := agora.Sub(ultimo); d > pior {
				pior = d
			}
			ultimo = agora
		}
		_ = c.Drenar(receber)
		gastoCpu, decorrido := cpuGasta(t)-antesCpu, time.Since(antes)

		t.Logf("%s · %.1f quadros/s · pior intervalo %.1fms · processador %.1f%% de UM núcleo",
			nome, float64(voltas)/decorrido.Seconds(), float64(pior.Microseconds())/1000,
			100*gastoCpu.Seconds()/decorrido.Seconds())
	}

	medir("padrão do Windows", 0, 0)
	medir("econômico, como app escondido", estrangularVelocidade, estrangularVelocidade)
	medir("prioridade total", estrangularVelocidade, 0)
	medir("econômico de novo", estrangularVelocidade, estrangularVelocidade)
}
