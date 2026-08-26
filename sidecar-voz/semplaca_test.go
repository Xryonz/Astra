package main

import (
	"runtime"
	"testing"
	"time"
)

func TestTransmitirSemPlaca(t *testing.T) {
	precisaDeTela(t)

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
		t.Skipf("sem tela para capturar: %v", err)
	}
	defer tela.Fechar()
	largura, altura := tela.Tamanho()

	lista, err := ProcurarCompressores()
	if err != nil {
		t.Fatalf("procurar compressores: %v", err)
	}
	defer SoltarCompressores(lista)

	var c *Compressor
	var recusas []string
	for _, cand := range lista {
		fala, _ := cand.FalaD3D11()
		if fala {
			continue
		}
		c, err = amarrar(cand, tela, largura, altura, 1280, 720, 30, 2500, true)
		if err == nil {
			break
		}
		recusas = append(recusas, cand.Nome+": "+err.Error())
		c = nil
	}
	if c == nil {
		t.Fatalf("nenhum compressor de software amarrou na memória: %v", recusas)
	}
	defer c.Fechar()

	t.Logf("compressor %q, entrada %s, na memória: %v", c.Nome, c.Formato, c.NaMemoria)
	if !c.NaMemoria {
		t.Fatal("amarrou mas não marcou NaMemoria")
	}
	if c.Formato != "NV12" {
		t.Fatalf("esperava NV12 na entrada, veio %q", c.Formato)
	}
	if c.reduzir == nil {
		t.Fatal("sem redimensionador: nada converteria ARGB32 em NV12")
	}

	var quadros, bytes, comChave int
	comeco := time.Now()
	receber := func(pronto []byte, _ time.Duration) {
		quadros++
		bytes += len(pronto)
		if temQuadroChave(pronto) {
			comChave++
		}
	}

	for i := 0; i < 60; i++ {
		textura, err := tela.ProximoQuadro(50)
		if err != nil {
			t.Fatalf("capturar: %v", err)
		}
		if textura == 0 {

			if err := c.Drenar(receber); err != nil {
				t.Fatalf("colher o que sobrou: %v", err)
			}
			continue
		}
		if err := c.Comprimir(textura, time.Since(comeco), receber); err != nil {
			t.Fatalf("comprimir o quadro %d: %v", i, err)
		}
		textura.soltar()
		tela.SoltarQuadro()
	}

	if err := c.Drenar(receber); err != nil {
		t.Fatalf("colher o último: %v", err)
	}

	if quadros == 0 {
		t.Fatal("o caminho de software não devolveu H.264 nenhum")
	}
	if comChave == 0 {
		t.Error("nenhum quadro-chave: quem entrasse na sala olharia para o nada para sempre")
	}

	media := c.Custos.Media()
	t.Logf("%d quadros, %d bytes, %d com quadro-chave", quadros, bytes, comChave)
	t.Logf("  copiar na placa      %8.2fms", ms(media.Copia))
	t.Logf("  converter para NV12  %8.2fms", ms(media.Reducao))
	t.Logf("  comprimir na CPU     %8.2fms", ms(media.Compressao))
	t.Logf("  ler o H.264          %8.2fms", ms(media.Leitura))
	custo := media.Copia + media.Reducao + media.Compressao + media.Leitura
	t.Logf("  TOTAL                %8.2fms  -> teto de %.0f quadros por segundo",
		ms(custo), float64(time.Second)/float64(custo))
}

func ms(d time.Duration) float64 { return float64(d.Microseconds()) / 1000 }
