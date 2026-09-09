package main

import (
	"os"
	"runtime"
	"testing"
	"time"

	"golang.org/x/sys/windows"
)

func cpuGasta(t *testing.T) time.Duration {
	t.Helper()
	var criacao, saida, nucleo, usuario windows.Filetime
	if err := windows.GetProcessTimes(windows.CurrentProcess(),
		&criacao, &saida, &nucleo, &usuario); err != nil {
		t.Fatalf("ler o tempo de processador: %v", err)
	}
	cem := func(f windows.Filetime) time.Duration {
		return time.Duration(uint64(f.HighDateTime)<<32|uint64(f.LowDateTime)) * 100
	}
	return cem(nucleo) + cem(usuario)
}

func TestSondaCustoDeCpu(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_CPU") == "" {
		t.Skip("ASTRA_SONDA_CPU nao definida — sonda de investigacao, nao roda no dia a dia")
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

	medir := func(largura, altura, fps, kbps int) {
		c, err := AbrirCompressor(tela, largura, altura, fps, kbps)
		if err != nil {
			t.Fatalf("abrir o compressor: %v", err)
		}
		defer c.Fechar()

		bytes, saidas := 0, 0
		receber := func(pronto []byte, _ time.Duration) {
			bytes += len(pronto)
			saidas++
		}

		ritmo := NovoRitmo(fps)
		for i := 0; i < fps/2; i++ {
			ritmo.Esperar()
			_ = c.Comprimir(prontos[i%preparados].textura, time.Duration(i)*time.Second/time.Duration(fps), nil, receber)
		}

		antesCpu, antes := cpuGasta(t), time.Now()
		voltas := fps * 5
		for i := 0; i < voltas; i++ {
			ritmo.Esperar()
			if err := c.Comprimir(prontos[i%preparados].textura,
				time.Duration(i)*time.Second/time.Duration(fps), nil, receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
		}
		_ = c.Drenar(receber)

		gastoCpu, decorrido := cpuGasta(t)-antesCpu, time.Since(antes)

		t.Logf("%dx%d @%d a %d kbps · %s", largura, altura, fps, kbps, c.Nome)
		t.Logf("   entregou %.0f quadros/s · %.1f Mbps",
			float64(voltas)/decorrido.Seconds(), float64(bytes)*8/decorrido.Seconds()/1_000_000)
		t.Logf("   processador: %.1f%% de UM núcleo (%.0fms de CPU em %.1fs)",
			100*gastoCpu.Seconds()/decorrido.Seconds(),
			float64(gastoCpu.Microseconds())/1000, decorrido.Seconds())
	}

	medir(1920, 1080, 60, 8000)
	medir(1280, 720, 60, 4000)
	medir(960, 540, 60, 1200)
}
