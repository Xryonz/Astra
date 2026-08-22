package main

// A PROVA DO SELETOR DE TELA.
//
// Duas coisas que só a máquina responde, e nenhuma delas é adivinhável:
//
//  1. os índices de vtable e o arranjo do `DXGI_OUTPUT_DESC`. Índice errado em COM não
//     dá erro — chama outra função. Arranjo errado desloca os campos e devolve números
//     que parecem plausíveis. A prova é o NOME sair legível (`\\.\DISPLAY1`) e o tamanho
//     bater com a resolução de verdade: as duas coisas juntas só acontecem se a struct
//     inteira estiver certa.
//  2. a miniatura tem imagem de verdade, e não um retângulo preto. Este é o caso que a
//     área de trabalho parada produzia antes de `QuadroAtual` existir.
//
//	ASTRA_TESTE_TELA=1 go test -run Monitores -v

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"
)

func TestListarMonitores(t *testing.T) {
	precisaDeTela(t)

	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	comeco := time.Now()
	lista, err := ListarMonitores()
	if err != nil {
		t.Fatalf("listar monitores: %v", err)
	}
	gasto := time.Since(comeco)

	if len(lista) == 0 {
		t.Fatal("nenhum monitor: o seletor abriria vazio")
	}

	principais := 0
	comMiniatura := 0
	for _, m := range lista {
		png, _ := base64.StdEncoding.DecodeString(m.Miniatura)
		t.Logf("%d: %-14s %dx%d principal=%v miniatura=%d bytes",
			m.Indice, m.Nome, m.Largura, m.Altura, m.Principal, len(png))

		// O NOME PROVA A STRUCT INTEIRA. `\\.\DISPLAY1` só sai legível se os 32
		// caracteres largos estiverem no lugar certo; qualquer deslocamento devolve
		// lixo ou vazio, e aí o tamanho lido a seguir também estaria errado.
		if m.Nome == "" {
			t.Errorf("monitor %d sem nome: o arranjo do DXGI_OUTPUT_DESC está errado", m.Indice)
		}
		if m.Largura < 320 || m.Altura < 240 {
			t.Errorf("monitor %d com tamanho implausível (%dx%d): campos deslocados",
				m.Indice, m.Largura, m.Altura)
		}
		if m.Principal {
			principais++
		}
		if len(png) > 0 {
			comMiniatura++
			if len(png) < 500 {
				t.Errorf("monitor %d: miniatura de %d bytes é pequena demais para ter imagem",
					m.Indice, len(png))
			}
		}
	}

	// EXATAMENTE UM PRINCIPAL. Zero significa que a regra de origem (0,0) não está
	// valendo; mais de um significa que os campos do retângulo estão deslocados.
	if principais != 1 {
		t.Errorf("%d monitores marcados como principal, esperava 1", principais)
	}

	// A ÁREA DE TRABALHO PARADA É O CASO NORMAL na hora de escolher qual tela
	// compartilhar. Se nenhuma miniatura sair, o seletor mostra retângulos vazios
	// justamente quando mais precisa mostrar imagem.
	if comMiniatura == 0 {
		t.Error("nenhuma miniatura: o seletor não teria como mostrar qual tela é qual")
	}

	t.Logf("%d monitores, %d com miniatura, em %v", len(lista), comMiniatura, gasto)

	// SALVA PARA OLHAR. Miniatura com azul e vermelho trocados, ou de cabeça para
	// baixo, passa em qualquer asserção que eu escreva — é defeito que só o olho pega,
	// e por isso os arquivos ficam onde dá para abrir.
	destino := t.TempDir()
	if d := os.Getenv("ASTRA_MINIATURAS"); d != "" {
		destino = d
	}
	for _, m := range lista {
		png, err := base64.StdEncoding.DecodeString(m.Miniatura)
		if err != nil || len(png) == 0 {
			continue
		}
		caminho := filepath.Join(destino, "monitor-"+itoa(m.Indice)+".png")
		if err := os.WriteFile(caminho, png, 0o644); err != nil {
			t.Logf("não consegui salvar %s: %v", caminho, err)
			continue
		}
		t.Logf("miniatura salva em %s", caminho)
	}
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b []byte
	for n > 0 {
		b = append([]byte{byte('0' + n%10)}, b...)
		n /= 10
	}
	return string(b)
}
