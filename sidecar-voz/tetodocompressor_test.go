package main

import (
	"runtime"
	"testing"
	"time"
)

func TestOTetoDoCompressorSemOLaco(t *testing.T) {
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

	const fps = 60
	c, err := AbrirCompressor(tela, 1280, 720, fps, 2500)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()
	t.Logf("compressor: %s (assincrono=%v)", c.Nome, c.Assincrono)

	var textura objeto
	prazo := time.Now().Add(3 * time.Second)
	for time.Now().Before(prazo) {
		textura, err = tela.ProximoQuadro(100)
		if err == nil && textura != 0 {
			break
		}
	}
	if textura == 0 {
		t.Skip("a tela não mudou em 3s — mexa numa janela e rode de novo")
	}

	cpuAntes := TempoDeProcessador()
	comeco := time.Now()
	fim := comeco.Add(2 * time.Second)
	var entradas, saidas int
	for time.Now().Before(fim) {
		err := c.Comprimir(textura, time.Since(comeco), tela.SoltarQuadro, func([]byte, time.Duration) { saidas++ })
		if err != nil {
			t.Fatalf("comprimir (depois de %d): %v", entradas, err)
		}
		entradas++
	}
	duracao := time.Since(comeco)
	cpu := TempoDeProcessador() - cpuAntes
	textura.soltar()
	tela.SoltarQuadro()

	porEntrada := duracao / time.Duration(max(entradas, 1))
	t.Logf("TETO: %d entradas e %d saídas em %v", entradas, saidas, duracao.Round(time.Millisecond))
	t.Logf("  %.0f quadros/s alimentados, %v por entrega", float64(entradas)/duracao.Seconds(), porEntrada.Round(time.Microsecond))
	t.Logf("  processador: %.3f núcleos", float64(cpu)/float64(duracao))
	t.Logf("  o orçamento de 60/s é 16,67ms por quadro")

	m := c.Custos.Media()
	t.Logf("POR QUADRO, esperando:")
	t.Logf("  ele pedir entrada   %8.0fus  (a placa OCUPADA — só some comprimindo menos)",
		float64(m.PedidoDeEntrada.Microseconds()))
	t.Logf("  a saída ficar pronta %7.0fus  (NÓS parados — some com pipeline)",
		float64(m.SaidaPronta.Microseconds()))
	t.Logf("  copiar %.0fus | ler os NALs %.0fus",
		float64(m.Copia.Microseconds()), float64(m.Leitura.Microseconds()))

	if entradas == 0 {
		t.Fatal("o compressor não aceitou nem um quadro")
	}
}
