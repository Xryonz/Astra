package main

import (
	"os"
	"runtime"
	"strings"
	"testing"
)

// SONDA DO COMPRESSOR DE VÍDEO.
//
// A pergunta que decide a arquitetura da transmissão é uma só: existe nesta máquina um
// compressor de H.264 que aceite a textura ONDE A CAPTURA A DEIXA, na placa? Se sim, o
// caminho custa 0,07 núcleo. Se não, o quadro precisa descer e volta a custar 0,84 —
// doze vezes mais, medido.
//
// Perguntar antes de montar o cano é a lição do cancelador de eco, onde supor a
// resposta custou uma tarde.

func precisaDeVideo(t *testing.T) {
	t.Helper()
	if os.Getenv("ASTRA_TESTE_VIDEO") == "" {
		t.Skip("defina ASTRA_TESTE_VIDEO=1 (precisa de placa de vídeo de verdade)")
	}
}

// A PROVA DE QUE OS ÍNDICES DO IMFAttributes ESTÃO CERTOS.
//
// São 30 métodos herdados antes de `GetCount` e 33 antes de `ActivateObject` — uma
// contagem que ninguém confere de olho. Mas o NOME do compressor sai do índice 13, e
// nome legível ("NVIDIA H.264 Encoder MFT", "Intel® Quick Sync Video H.264 Encoder
// MFT") só sai se a tabela inteira estiver certa: qualquer erro de índice devolveria
// lixo, string vazia, ou travaria.
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

	// ESTE É O NÚMERO QUE IMPORTA. Zero aqui não é falha do teste: é a resposta de
	// que nesta máquina o caminho barato não existe, e a transmissão teria de descer
	// o quadro. Vale saber com todas as letras em vez de descobrir pelo ventilador.
	if comD3D11 == 0 {
		t.Errorf("nenhum dos %d compressores aceita textura de video -- o quadro teria que descer pra CPU", len(lista))
	}
}

// Ligar o compressor é diferente de encontrá-lo: o ativador pode listar uma coisa que
// o driver depois recusa a instanciar (placa em uso por outro programa, driver a meio
// caminho de uma atualização). Separado porque o remédio é outro.
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

	// O primeiro que fala D3D11; sem nenhum, o primeiro da lista.
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

// O relatório é o que uma pessoa com problema de transmissão vai mandar junto do
// relato, então ele precisa dizer alguma coisa mesmo quando não há nada.
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
