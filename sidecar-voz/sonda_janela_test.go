package main

import (
	"os"
	"testing"
	"time"
	"unsafe"
)

func umaJanelaQualquer(t *testing.T) JanelaDaTela {
	lista := enumerarJanelas()
	if len(lista) == 0 {
		t.Skip("nenhuma janela capturável nesta máquina agora")
	}
	return lista[0]
}

func TestEnumerarJanelasSoTrazOQueDaParaMostrar(t *testing.T) {
	lista := enumerarJanelas()
	if len(lista) == 0 {
		t.Skip("nenhuma janela aberta")
	}
	t.Logf("%d janela(s) capturável(is)", len(lista))
	for _, j := range lista {
		if j.Identificador == 0 {
			t.Error("janela sem identificador entrou na lista")
		}
		if j.Nome == "" {
			t.Error("janela sem nome entrou na lista: ninguém escolhe o que não tem nome")
		}
		if j.Largura < larguraMinimaDaJanela || j.Altura < alturaMinimaDaJanela {
			t.Errorf("%q entrou com %dx%d, abaixo do mínimo", j.Nome, j.Largura, j.Altura)
		}
	}
	if len(lista) <= 6 {
		for _, j := range lista {
			t.Logf("  %s (%dx%d)", j.Nome, j.Largura, j.Altura)
		}
	}
}

func TestCapturarUmaJanelaDeVerdade(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_VIDEO") == "" {
		t.Skip("defina ASTRA_TESTE_VIDEO=1 (precisa de placa de vídeo de verdade)")
	}
	defer PrenderNaThread()()

	j := umaJanelaQualquer(t)
	t.Logf("janela: %q, %dx%d", j.Nome, j.Largura, j.Altura)

	tela, err := AbrirJanela(uintptr(j.Identificador), j.Largura, j.Altura)
	if err != nil {
		t.Fatalf("abrir a captura da janela: %v", err)
	}
	defer tela.Fechar()

	l, a := tela.Tamanho()
	if l != j.Largura&^1 || a != j.Altura&^1 {
		t.Errorf("Tamanho() devolveu %dx%d, esperava %dx%d", l, a, j.Largura&^1, j.Altura&^1)
	}

	var quadros int
	var formato uint32
	fim := time.Now().Add(3 * time.Second)
	for time.Now().Before(fim) && quadros < 12 {
		textura, err := tela.ProximoQuadro(200)
		if err != nil {
			t.Fatalf("pegar quadro: %v", err)
		}
		if textura == 0 {
			continue
		}
		if formato == 0 {
			var desc descricaoDeTextura
			textura.chamar(texturaDescricao, uintptr(unsafe.Pointer(&desc)))
			formato = desc.Formato
			agoraL, agoraA := tela.Tamanho()
			if int(desc.Largura) != agoraL || int(desc.Altura) != agoraA {
				t.Errorf("a textura veio %dx%d, a piscina está em %dx%d", desc.Largura, desc.Altura, agoraL, agoraA)
			}
		}
		textura.soltar()
		tela.SoltarQuadro()
		quadros++
	}

	if quadros == 0 {
		t.Fatal("nenhum quadro em 3s")
	}
	if formato != formatoBGRA {
		t.Errorf("formato %d, esperava %d (o mesmo que o compressor já recebe do DXGI)", formato, formatoBGRA)
	}
	t.Logf("%d quadros, formato %d — o compressor recebe isto sem nenhuma adaptação", quadros, formato)
}

func TestJanelaInvalidaNaoDerruba(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_VIDEO") == "" {
		t.Skip("defina ASTRA_TESTE_VIDEO=1 (precisa de placa de vídeo de verdade)")
	}
	defer PrenderNaThread()()

	if _, err := AbrirJanela(0, 800, 600); err == nil {
		t.Error("janela zero devia falhar, e falhar com mensagem")
	}
	if _, err := AbrirJanela(0xDEADBEEF, 800, 600); err == nil {
		t.Error("identificador que não é janela devia falhar em vez de travar")
	}
}

func TestMiniaturaDeJanela(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_VIDEO") == "" {
		t.Skip("defina ASTRA_TESTE_VIDEO=1 (precisa de placa de vídeo de verdade)")
	}
	defer PrenderNaThread()()

	j := umaJanelaQualquer(t)
	png, err := amostrarJanela(uintptr(j.Identificador), j.Largura, j.Altura)
	if err != nil {
		t.Fatalf("amostrar %q: %v", j.Nome, err)
	}
	if len(png) < 100 {
		t.Fatalf("a miniatura saiu com %d bytes", len(png))
	}
	if string(png[1:4]) != "PNG" {
		t.Fatalf("a miniatura não é PNG: % x", png[:8])
	}
	t.Logf("miniatura de %q: %d bytes", j.Nome, len(png))
}
