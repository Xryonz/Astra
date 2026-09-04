package main

import (
	"bytes"
	"encoding/json"
	"testing"

	lksdk "github.com/livekit/server-sdk-go/v2"
)

func TestQuandoAdiantaVoltarParaSala(t *testing.T) {
	casos := []struct {
		motivo lksdk.DisconnectionReason
		volta  bool
	}{
		{lksdk.Failed, true},
		{lksdk.OtherReason, true},
		{lksdk.LeaveRequested, false},
		{lksdk.DuplicateIdentity, false},
		{lksdk.ParticipantRemoved, false},
		{lksdk.RoomClosed, false},
		{lksdk.RejectedByUser, false},
		{lksdk.UserUnavailable, false},
	}

	for _, c := range casos {
		if got := adiantaVoltar(c.motivo); got != c.volta {
			t.Errorf("adiantaVoltar(%q) = %v; queria %v", c.motivo, got, c.volta)
		}
	}
}

func salaDeTeste(t *testing.T) (*Sala, *bytes.Buffer) {
	t.Helper()
	var papel bytes.Buffer
	s := NovaSala(NewEscritor(&papel), nil, nil)
	return s, &papel
}

func ultimoEvento(t *testing.T, papel *bytes.Buffer) Evento {
	t.Helper()
	dec := json.NewDecoder(bytes.NewReader(papel.Bytes()))
	var ultimo Evento
	achou := false
	for {
		var ev Evento
		if err := dec.Decode(&ev); err != nil {
			break
		}
		ultimo, achou = ev, true
	}
	if !achou {
		t.Fatal("o sidecar não mandou evento nenhum")
	}
	return ultimo
}

func TestQuedaNaRedePedeVoltaEDerrubaATransmissao(t *testing.T) {
	s, papel := salaDeTeste(t)

	faixa, err := lksdk.NewLocalTrack(CapacidadeH264)
	if err != nil {
		t.Fatalf("criar faixa de tela: %v", err)
	}
	s.sala = lksdk.NewRoom(nil)
	s.tela, s.telaFina = faixa, faixa

	s.aoCairDaSala(lksdk.Failed)

	ev := ultimoEvento(t, papel)
	if ev.Ev != EvEstado || ev.V != "disconnected" {
		t.Fatalf("evento inesperado: %+v", ev)
	}
	if ev.Tipo != "queda" {
		t.Errorf("tipo = %q; queria \"queda\" para que o desktop tente voltar", ev.Tipo)
	}

	if s.tela != nil || s.telaFina != nil || s.pubTela != nil {
		t.Error("a tela continuou publicada; o emissor seguiria comprimindo para ninguém")
	}
}

func TestSairDePropositoNaoPedeVolta(t *testing.T) {
	s, papel := salaDeTeste(t)
	s.sala = lksdk.NewRoom(nil)

	s.aoCairDaSala(lksdk.LeaveRequested)

	if ev := ultimoEvento(t, papel); ev.Tipo != "fim" {
		t.Errorf("tipo = %q; queria \"fim\" — voltar depois de sair reabriria a chamada sozinha", ev.Tipo)
	}
}

func TestQuedaSemSalaNaoPedeVolta(t *testing.T) {
	s, papel := salaDeTeste(t)

	s.aoCairDaSala(lksdk.Failed)

	if ev := ultimoEvento(t, papel); ev.Tipo != "fim" {
		t.Errorf("tipo = %q; queria \"fim\" — sem sala não há para onde voltar", ev.Tipo)
	}
}
