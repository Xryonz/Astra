package main

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

	if principais != 1 {
		t.Errorf("%d monitores marcados como principal, esperava 1", principais)
	}

	if comMiniatura == 0 {
		t.Error("nenhuma miniatura: o seletor não teria como mostrar qual tela é qual")
	}

	t.Logf("%d monitores, %d com miniatura, em %v", len(lista), comMiniatura, gasto)

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
