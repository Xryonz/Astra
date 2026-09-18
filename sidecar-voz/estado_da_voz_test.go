package main

import (
	"encoding/json"
	"testing"

	lksdk "github.com/livekit/server-sdk-go/v2"
)

func recolherEstados(t *testing.T, carga []byte, de string) []Evento {
	t.Helper()
	recolhidos := make(chan Evento, 16)
	s := &Sala{saida: NewEscritor(coletor{recolhidos})}
	s.aoChegarRecado(lksdk.UserData(carga), lksdk.DataReceiveParams{SenderIdentity: de})
	close(recolhidos)

	var saiu []Evento
	for ev := range recolhidos {
		saiu = append(saiu, ev)
	}
	return saiu
}

func TestRecadoDeEstadoViraDoisEventos(t *testing.T) {
	carga, err := json.Marshal(recadoDeEstado{Astra: recadoDeEstadoDaVoz, Mudo: true, Surdo: false})
	if err != nil {
		t.Fatalf("montar o recado: %v", err)
	}

	eventos := recolherEstados(t, carga, "alguem")
	if len(eventos) != 2 {
		t.Fatalf("saíram %d eventos, esperava 2 (mudo e surdo)", len(eventos))
	}

	visto := map[string]string{}
	for _, ev := range eventos {
		if ev.Ev != EvEstadoDaVoz {
			t.Errorf("evento %q, esperava %q", ev.Ev, EvEstadoDaVoz)
		}
		if ev.Par != "alguem" {
			t.Errorf("o evento veio de %q, esperava %q", ev.Par, "alguem")
		}
		visto[ev.Tipo] = ev.V
	}
	if visto["mudo"] != "1" {
		t.Errorf("mudo saiu %q, esperava 1", visto["mudo"])
	}
	if visto["surdo"] != "0" {
		t.Errorf("surdo saiu %q, esperava 0", visto["surdo"])
	}
}

func TestRecadoDeOutroAppNaoViraEvento(t *testing.T) {
	casos := map[string][]byte{
		"json de outra coisa": []byte(`{"tipo":"chat","texto":"oi"}`),
		"sem a marca do astra": func() []byte {
			b, _ := json.Marshal(map[string]any{"mudo": true, "surdo": true})
			return b
		}(),
		"nem e json": []byte("isto nao e json"),
		"vazio":      nil,
	}

	for nome, carga := range casos {
		if eventos := recolherEstados(t, carga, "alguem"); len(eventos) != 0 {
			t.Errorf("%s: saíram %d eventos, não podia sair nenhum", nome, len(eventos))
		}
	}
}

func TestRecadoSemRemetenteEIgnorado(t *testing.T) {
	carga, _ := json.Marshal(recadoDeEstado{Astra: recadoDeEstadoDaVoz, Mudo: true})
	if eventos := recolherEstados(t, carga, ""); len(eventos) != 0 {
		t.Errorf("saíram %d eventos sem saber de quem; não dá para marcar ninguém", len(eventos))
	}
}
