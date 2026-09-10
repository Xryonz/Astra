package main

import (
	"runtime"
	"testing"
)

func TestAberturaPulaOCompressorCondenado(t *testing.T) {
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

	primeiro, err := AbrirCompressor(tela, 1280, 720, 30, 2500)
	if err != nil {
		t.Fatalf("abrir o primeiro compressor: %v", err)
	}
	nome := primeiro.Nome
	primeiro.Fechar()

	segundo, err := AbrirCompressorEvitando(tela, 1280, 720, 30, 2500, map[string]bool{nome: true})
	if err != nil {
		t.Skipf("nesta máquina só o %s serve, então não dá para provar a troca aqui: %v", nome, err)
	}
	defer segundo.Fechar()

	if segundo.Nome == nome {
		t.Errorf("pedi para evitar %q e a abertura devolveu ele mesmo — a transmissão reabriria "+
			"no compressor que acabou de falhar", nome)
	}
	t.Logf("%s condenado, a abertura caiu em %s", nome, segundo.Nome)
}

func TestCompressorQueMorreCedoEhCondenado(t *testing.T) {
	condenados := map[string]bool{}

	if !condenar(condenados, "Intel", 2) {
		t.Fatal("um compressor que morreu no quadro 2 precisa ser condenado; se ele continuar " +
			"elegível, a abertura escolhe o mesmo de novo e a transmissão entra em ciclo")
	}
	if !condenados["Intel"] {
		t.Error("condenar disse que sim mas não anotou o nome, então a próxima abertura o escolhe de novo")
	}
}

func TestCompressorQueJaTrabalhouGanhaOutraChance(t *testing.T) {
	condenados := map[string]bool{}

	if condenar(condenados, "NVIDIA", quadrosQueProvamOCompressor) {
		t.Error("um compressor que entregou os quadros e só depois falhou não é o culpado; " +
			"condená-lo empurra a transmissão para um compressor pior pelo resto da sessão")
	}
	if len(condenados) != 0 {
		t.Errorf("nada devia ter sido anotado, e foi: %v", condenados)
	}
}

func TestOLimiteDaCondenacao(t *testing.T) {
	condenados := map[string]bool{}

	if !condenar(condenados, "quase", quadrosQueProvamOCompressor-1) {
		t.Errorf("com %d quadros ainda é morte precoce", quadrosQueProvamOCompressor-1)
	}
	if condenar(condenados, "passou", quadrosQueProvamOCompressor+1) {
		t.Errorf("com %d quadros já é falha tardia", quadrosQueProvamOCompressor+1)
	}
}

func TestCondenadoSegueCondenado(t *testing.T) {
	condenados := map[string]bool{"Intel": true}

	condenar(condenados, "Intel", quadrosQueProvamOCompressor)

	if !condenados["Intel"] {
		t.Error("a condenação de uma volta anterior sumiu do conjunto: o compressor quebrado " +
			"volta a ser elegível assim que sobreviver a uma volta longa")
	}
}
