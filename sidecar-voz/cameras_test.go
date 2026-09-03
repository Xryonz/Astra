package main

import (
	"runtime"
	"strings"
	"testing"
)

func TestListarCamerasNaoExplodeSemCamera(t *testing.T) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Skipf("sem COM nesta máquina: %v", err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		t.Skipf("sem Media Foundation: %v", err)
	}
	defer fecharMF()

	lista, err := ListarCameras()
	if err != nil {
		t.Fatalf("listar câmeras: %v", err)
	}

	t.Logf("%d câmera(s)", len(lista))
	for _, c := range lista {
		if c.Nome == "" {
			t.Errorf("câmera sem nome (id %q): a chave do nome amigável está errada", c.Id)
		}
		if !strings.HasPrefix(c.Id, `\\?\`) {
			t.Errorf("id %q não parece um link simbólico de dispositivo", c.Id)
		}
		t.Logf("  %s", c.Nome)
	}

	vistos := map[string]bool{}
	for _, c := range lista {
		if vistos[c.Id] {
			t.Errorf("id repetido: %q — a lista serve de chave na interface", c.Id)
		}
		vistos[c.Id] = true
	}
}
