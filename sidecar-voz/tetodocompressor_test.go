package main

import (
	"runtime"
	"testing"
	"time"
)

// QUAL É O TETO DO COMPRESSOR, sem o laço no caminho?
//
// A PERGUNTA QUE ISTO RESPONDE. Medido, o caminho inteiro custa 6,2 ms por quadro em
// 720p e 7,0 ms em 1080p — só 12% de diferença para 2,25 vezes menos pixels. Custo que
// quase não muda com o tamanho não é custo de COMPRIMIR: é latência de ida e volta.
//
// Se for latência, ela some com pipeline (alimentar o quadro seguinte enquanto a placa
// trabalha no atual) e NÃO some reduzindo resolução. Se for trabalho de verdade, é o
// contrário. As duas conclusões levam a otimizações opostas, e escolher errado custa a
// implementação inteira.
//
// COMO SEPARAR: alimentar o compressor com o MESMO quadro, o mais rápido que ele
// aceitar, sem captura no meio. O que sai daqui é a vazão máxima dele. Comparada com os
// ~160 quadros por segundo que o laço atual alcança, a diferença é o que o pipeline
// tem para recuperar.
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

	// UM QUADRO SÓ, capturado uma vez e reaproveitado. A captura sai da conta de
	// propósito: o que se mede aqui é o compressor, e nada mais.
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

	// Uma cópia nossa, porque a da duplicação precisa ser devolvida.
	cpuAntes := TempoDeProcessador()
	comeco := time.Now()
	fim := comeco.Add(2 * time.Second)
	var entradas, saidas int
	for time.Now().Before(fim) {
		err := c.Comprimir(textura, time.Since(comeco), func([]byte) { saidas++ })
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

	// ONDE O TEMPO FICA — e é isto que decide se pipeline vale a pena.
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
