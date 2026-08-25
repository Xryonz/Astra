package main

import (
	"os"
	"runtime"
	"strings"
	"testing"
)

func precisaDeVideo(t *testing.T) {
	t.Helper()
	if os.Getenv("ASTRA_TESTE_VIDEO") == "" {
		t.Skip("defina ASTRA_TESTE_VIDEO=1 (precisa de placa de vídeo de verdade)")
	}
}

func TestAcharCompressorDeH264(t *testing.T) {
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

	lista, err := ProcurarCompressores()
	if err != nil {
		t.Fatalf("procurar: %v", err)
	}
	defer SoltarCompressores(lista)

	if len(lista) == 0 {
		t.Fatal("nenhum compressor de H.264 -- sem isto nao ha transmissao por este caminho")
	}

	comD3D11 := 0
	for _, c := range lista {
		fala, err := c.FalaD3D11()
		t.Logf("%-46s D3D11=%v err=%v", c.Nome, fala, err)
		if c.Nome == "" {
			t.Error("compressor sem nome -- indice do GetAllocatedString errado")
		}
		if fala {
			comD3D11++
		}
	}

	if comD3D11 == 0 {
		t.Errorf("nenhum dos %d compressores aceita textura de video -- o quadro teria que descer pra CPU", len(lista))
	}
}

func TestLigarOCompressor(t *testing.T) {
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

	lista, err := ProcurarCompressores()
	if err != nil || len(lista) == 0 {
		t.Skipf("nada para ligar: %v", err)
	}
	defer SoltarCompressores(lista)

	escolhido := lista[0]
	for _, c := range lista {
		if fala, _ := c.FalaD3D11(); fala {
			escolhido = c
			break
		}
	}

	transformador, err := escolhido.Montar()
	if err != nil {
		t.Fatalf("ligar %q: %v", escolhido.Nome, err)
	}
	defer transformador.soltar()

	if transformador == 0 {
		t.Fatal("ligou sem erro e devolveu nulo")
	}
	t.Logf("ligou: %s", escolhido.Nome)
}

func TestRelatorioNuncaSaiVazio(t *testing.T) {
	precisaDeVideo(t)
	texto, err := RelatarCompressores()
	if err != nil {
		t.Fatalf("relatar: %v", err)
	}
	if strings.TrimSpace(texto) == "" {
		t.Fatal("relatorio vazio -- inutil para diagnostico")
	}
	t.Logf("\n%s", texto)
}

func TestFormatosQueOCompressorAceita(t *testing.T) {
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

	lista, err := ProcurarCompressores()
	if err != nil || len(lista) == 0 {
		t.Skipf("nada para perguntar: %v", err)
	}
	defer SoltarCompressores(lista)

	achouAlgum := false
	for _, c := range lista {
		formatos, err := c.FormatosQueAceita()
		if err != nil {
			t.Logf("%-46s nao respondeu: %v", c.Nome, err)
			continue
		}
		if len(formatos) > 0 {
			achouAlgum = true
		}
		t.Logf("%-46s %v", c.Nome, formatos)
	}
	if !achouAlgum {
		t.Error("nenhum compressor listou formato de entrada -- indice do GetInputAvailableType errado, ou falta destrancar")
	}
}
