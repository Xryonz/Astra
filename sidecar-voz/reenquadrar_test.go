package main

import (
	"runtime"
	"testing"
	"time"
	"unsafe"
)

func TestAPiscinaAcompanhaOTamanhoDaJanela(t *testing.T) {
	precisaDeVideo(t)
	defer PrenderNaThread()()

	j := umaJanelaQualquer(t)
	metadeL, metadeA := (j.Largura/2)&^1, (j.Altura/2)&^1
	if metadeL <= 0 || metadeA <= 0 {
		t.Skip("a janela é pequena demais para pedir metade dela")
	}

	tela, err := AbrirJanela(uintptr(j.Identificador), metadeL, metadeA)
	if err != nil {
		t.Fatalf("abrir a captura de %q: %v", j.Nome, err)
	}
	defer tela.Fechar()

	if l, a := tela.Tamanho(); l != metadeL || a != metadeA {
		t.Fatalf("a piscina abriu %dx%d, pedi %dx%d", l, a, metadeL, metadeA)
	}

	cresceu := false
	texturas := 0
	fim := time.Now().Add(3 * time.Second)
	for time.Now().Before(fim) && texturas < 8 {
		textura, err := tela.ProximoQuadro(200)
		if err != nil {
			t.Fatalf("pegar quadro: %v", err)
		}
		l, a := tela.Tamanho()
		if l != metadeL || a != metadeA {
			cresceu = true
		}
		if textura == 0 {
			continue
		}
		var desc descricaoDeTextura
		textura.chamar(texturaDescricao, uintptr(unsafe.Pointer(&desc)))
		textura.soltar()
		tela.SoltarQuadro()
		if int(desc.Largura) != l || int(desc.Altura) != a {
			t.Fatalf("a textura veio %dx%d com a piscina em %dx%d", desc.Largura, desc.Altura, l, a)
		}
		texturas++
	}

	if !cresceu {
		t.Fatalf("a piscina ficou nos %dx%d que pedi; a janela %q é maior que isso", metadeL, metadeA, j.Nome)
	}
	if texturas == 0 {
		t.Fatal("nenhuma textura em 3s: não dá para afirmar que a piscina nova entrega quadro")
	}
	l, a := tela.Tamanho()
	t.Logf("a piscina foi de %dx%d para %dx%d sozinha e entregou %d texturas nesse tamanho",
		metadeL, metadeA, l, a, texturas)
}

func TestReenquadrarNaoMudaOQueSobeNaRede(t *testing.T) {
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
		t.Skipf("sem tela: %v", err)
	}
	defer tela.Fechar()

	largura, altura := tela.Tamanho()
	saidaL, saidaA := 1280&^1, (altura*1280/largura)&^1
	c, err := AbrirCompressor(tela, saidaL, saidaA, 30, 2500)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()

	menorL, menorA := (largura*3/4)&^1, (altura*3/4)&^1
	if err := c.Reenquadrar(tela.dispositivo, menorL, menorA); err != nil {
		t.Fatalf("reenquadrar para %dx%d: %v", menorL, menorA, err)
	}

	if c.largura != menorL || c.altura != menorA {
		t.Errorf("a entrada ficou em %dx%d, esperava %dx%d", c.largura, c.altura, menorL, menorA)
	}
	if c.saidaL != saidaL || c.saidaA != saidaA {
		t.Errorf("o que sobe mudou para %dx%d — devia continuar %dx%d", c.saidaL, c.saidaA, saidaL, saidaA)
	}
	if c.reduzir == nil {
		t.Fatal("sem encaixe depois de reenquadrar: a entrada não caberia mais na saída")
	}
	if len(c.anel) == 0 {
		t.Fatal("o anel de texturas ficou vazio")
	}
	if c.proximo != 0 {
		t.Errorf("o anel devia recomeçar do zero, está em %d", c.proximo)
	}

	if err := c.Reenquadrar(tela.dispositivo, menorL, menorA); err != nil {
		t.Fatalf("reenquadrar para o mesmo tamanho devia ser nada, veio: %v", err)
	}

	t.Logf("entrada %dx%d → %dx%d, saída fixa em %dx%d", largura, altura, menorL, menorA, saidaL, saidaA)
}
