package main

import (
	"runtime"
	"testing"
	"time"
)

func TestVoltaCompletaDaTela(t *testing.T) {
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

	const largura, altura, fps = 1280, 720, 30
	c, err := AbrirCompressor(tela, largura, altura, fps, 2500)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()
	t.Logf("compressor: %s", c.Nome)

	d, err := AbrirDescompressor(largura, altura)
	if err != nil {
		t.Fatalf("abrir o descompressor: %v", err)
	}
	defer d.Fechar()
	t.Logf("descompressor: %s (traz a própria amostra: %v)", d.Nome, d.trazAmostra)

	var quadrosComprimidos, quadrosDecodificados int
	var ultimo Quadro
	var naoZerados int
	var noDescompressor time.Duration

	ritmo := NovoRitmo(fps)
	comeco := time.Now()
	fim := comeco.Add(4 * time.Second)
	for time.Now().Before(fim) && quadrosDecodificados < 10 {
		ritmo.Esperar()

		textura, err := tela.ProximoQuadro(100)
		if err != nil {
			if _, perdeu := err.(ErroDeAcessoPerdido); perdeu {
				if err := tela.Remontar(0); err != nil {
					t.Fatalf("recuperar a tela: %v", err)
				}
				continue
			}
			t.Fatalf("capturar: %v", err)
		}
		if textura == 0 {
			continue
		}

		var junto []byte
		err = c.Comprimir(textura, time.Since(comeco), func(nal []byte) {
			junto = append(junto, nal...)
		})
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			t.Fatalf("comprimir: %v", err)
		}
		if len(junto) == 0 {
			continue
		}
		quadrosComprimidos++

		antes := time.Now()
		err = d.Decodificar(junto, time.Since(comeco), func(q Quadro) {
			quadrosDecodificados++
			ultimo = q

			naoZerados = 0
			limite := q.Passo * q.Altura
			if limite > len(q.Dados) {
				limite = len(q.Dados)
			}
			for i := 0; i < limite; i += 64 {
				if q.Dados[i] != 0 {
					naoZerados++
				}
			}
		})
		noDescompressor += time.Since(antes)
		if err != nil {
			t.Fatalf("decodificar (depois de %d quadros): %v", quadrosDecodificados, err)
		}
	}

	t.Logf("comprimidos %d, decodificados %d", quadrosComprimidos, quadrosDecodificados)

	if quadrosDecodificados > 0 {
		porQuadro := noDescompressor / time.Duration(quadrosDecodificados)
		t.Logf("custo de decodificar: %v por quadro (%.1f%% de um núcleo a %d fps)",
			porQuadro.Round(time.Microsecond),
			float64(porQuadro)/float64(time.Second/fps)*100, fps)
	}
	if quadrosComprimidos == 0 {
		t.Skip("a tela não mudou o bastante em 4s — mexa numa janela e rode de novo")
	}
	if quadrosDecodificados == 0 {
		t.Fatal("nenhum quadro saiu do descompressor: o H.264 entrou e nada voltou")
	}

	t.Logf("quadro: %dx%d, passo %d, %d bytes (NV12 pede %d)",
		ultimo.Largura, ultimo.Altura, ultimo.Passo, len(ultimo.Dados),
		ultimo.Passo*ultimo.Altura*3/2)

	if ultimo.Largura != largura || ultimo.Altura != altura {
		t.Errorf("o quadro voltou em %dx%d, e foi comprimido em %dx%d",
			ultimo.Largura, ultimo.Altura, largura, altura)
	}
	if ultimo.Passo < ultimo.Largura {
		t.Errorf("passo %d menor que a largura %d: impossível", ultimo.Passo, ultimo.Largura)
	}

	if minimo := ultimo.Passo * ultimo.Altura * 3 / 2; len(ultimo.Dados) < minimo {
		t.Errorf("vieram %d bytes e NV12 em %dx%d com passo %d pede %d",
			len(ultimo.Dados), ultimo.Largura, ultimo.Altura, ultimo.Passo, minimo)
	}
	if naoZerados == 0 {
		t.Error("o quadro voltou inteiro em zero: buffer do tamanho certo e nunca escrito")
	}
}
