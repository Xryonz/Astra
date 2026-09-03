package main

import (
	"runtime"
	"testing"
	"time"
)

func TestOndeOTempoVaiEmCadaQuadro(t *testing.T) {
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

	larguraDaTela, alturaDaTela := tela.Tamanho()
	t.Logf("captura: %dx%d", larguraDaTela, alturaDaTela)

	c, err := AbrirCompressor(tela, 1280, 720, 60, 4000)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()
	t.Logf("compressor: %s (na placa=%v)", c.Nome, !c.NaMemoria)

	var (
		esperandoQuadro time.Duration
		esperaVazia     time.Duration
		comprimindo     time.Duration
		comQuadro       int
		semMudanca      int
	)

	comeco := time.Now()
	fim := comeco.Add(6 * time.Second)

	for time.Now().Before(fim) {
		marco := time.Now()
		textura, err := tela.ProximoQuadro(100)
		gasto := time.Since(marco)
		if err != nil {
			t.Fatalf("capturar: %v", err)
		}
		if textura == 0 {
			semMudanca++
			esperaVazia += gasto
			tela.SoltarQuadro()
			continue
		}
		esperandoQuadro += gasto

		marco = time.Now()
		err = c.Comprimir(textura, time.Since(comeco), nil, func([]byte, time.Duration) {})
		comprimindo += time.Since(marco)
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			t.Fatalf("comprimir: %v", err)
		}
		comQuadro++
	}

	decorrido := time.Since(comeco)
	if comQuadro == 0 {
		t.Skip("a tela nao mudou durante o teste; rode com algo em movimento na tela")
	}

	porQuadro := comprimindo / time.Duration(comQuadro)
	t.Logf("em %v: %d quadros com imagem nova, %d sem mudanca",
		decorrido.Round(time.Millisecond), comQuadro, semMudanca)
	capturaPorQuadro := esperandoQuadro / time.Duration(comQuadro)
	t.Logf("ate sair imagem nova: %v por quadro -- ATENCAO: isto e sobretudo ESPERA pela tela mudar,"+
		" nao trabalho da captura. Com a tela parada mede o ritmo do conteudo, nao o teto do pipeline",
		capturaPorQuadro)
	if semMudanca > 0 {
		t.Logf("espera a toa (tela parada): %v no total, %v por tentativa",
			esperaVazia.Round(time.Millisecond), esperaVazia/time.Duration(semMudanca))
	}
	t.Logf("comprimindo: %v no total, %v por quadro", comprimindo.Round(time.Millisecond), porQuadro)
	t.Logf("quadros por segundo entregues: %.1f", float64(comQuadro)/decorrido.Seconds())

	if porQuadro > 0 {
		t.Logf("teto do compressor sozinho: %.0f quadros por segundo", float64(time.Second)/float64(porQuadro))
	}
	t.Log("o teto real do pipeline e o do compressor: a captura so entrega quando a tela muda," +
		" entao com tela parada ela parece lenta sem ser")
	if porQuadro > time.Second/60 {
		t.Errorf("comprimir leva %v por quadro; 60 fps exige no maximo %v", porQuadro, time.Second/60)
	}
}
