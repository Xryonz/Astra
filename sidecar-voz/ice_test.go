package main

import (
	"context"
	"io"
	"strings"
	"testing"

	"github.com/pion/webrtc/v4"
)

func TestOPadraoDeIceNaoDependeDeUmServidorSo(t *testing.T) {
	cfg := configuracaoPadrao()

	if len(cfg.ICEServers) != 1 {
		t.Fatalf("esperava um bloco de STUN, vieram %d", len(cfg.ICEServers))
	}
	if n := len(cfg.ICEServers[0].URLs); n < 2 {
		t.Errorf("um STUN só é ponto único de falha; a lista tem %d", n)
	}

	pc, err := webrtc.NewPeerConnection(cfg)
	if err != nil {
		t.Fatalf("o pion recusou a configuração padrão de ICE: %v", err)
	}
	_ = pc.Close()
}

func TestAConfiguracaoDeIceVindaDoServidor(t *testing.T) {
	novo := func() *App { return &App{config: configuracaoPadrao()} }

	t.Run("comando vazio mantém o padrão", func(t *testing.T) {
		a := novo()
		antes := len(a.config.ICEServers[0].URLs)
		a.aplicarConfig(Comando{Cmd: CmdConfig})
		if len(a.config.ICEServers) != 1 || len(a.config.ICEServers[0].URLs) != antes {
			t.Errorf("configuração vazia não pode apagar o padrão: %+v", a.config.ICEServers)
		}
	})

	t.Run("só TURN preserva o STUN padrão", func(t *testing.T) {
		a := novo()
		a.aplicarConfig(Comando{
			Cmd:  CmdConfig,
			Turn: []ServTurn{{URL: "turn:relay.astra:3478", User: "quem", Senha: "segredo"}},
		})

		if len(a.config.ICEServers) != 2 {
			t.Fatalf("esperava STUN padrão mais o TURN, vieram %d blocos", len(a.config.ICEServers))
		}
		if len(a.config.ICEServers[0].URLs) != len(stunPadrao) {
			t.Errorf("o TURN não pode apagar o STUN: %v", a.config.ICEServers[0].URLs)
		}
		if a.config.ICEServers[1].Username != "quem" || a.config.ICEServers[1].Credential != "segredo" {
			t.Errorf("a credencial do TURN não chegou: %+v", a.config.ICEServers[1])
		}
	})

	t.Run("o STUN do servidor substitui o padrão", func(t *testing.T) {
		a := novo()
		a.aplicarConfig(Comando{Cmd: CmdConfig, Stun: []string{"stun:meu.servidor:3478"}})

		got := a.config.ICEServers[0].URLs
		if len(got) != 1 || got[0] != "stun:meu.servidor:3478" {
			t.Errorf("esperava apenas o STUN do servidor, veio %v", got)
		}
	})

	t.Run("TURN sem endereço não vira servidor", func(t *testing.T) {
		a := novo()
		a.aplicarConfig(Comando{Cmd: CmdConfig, Turn: []ServTurn{{URL: "", User: "quem"}}})

		if len(a.config.ICEServers) != 1 {
			t.Errorf("TURN sem endereço não pode virar servidor: %+v", a.config.ICEServers)
		}
	})

	t.Run("o que o servidor manda é aceito pelo pion", func(t *testing.T) {
		a := novo()
		a.aplicarConfig(Comando{
			Cmd:  CmdConfig,
			Stun: []string{"stun:stun.l.google.com:19302"},
			Turn: []ServTurn{{URL: "turn:relay.astra:3478?transport=udp", User: "quem", Senha: "segredo"}},
		})

		pc, err := webrtc.NewPeerConnection(a.config)
		if err != nil {
			t.Fatalf("o pion recusou a configuração montada: %v", err)
		}
		_ = pc.Close()
	})
}

func TestALinhaDeConfigQueODesktopEscreve(t *testing.T) {
	a := &App{saida: NewEscritor(io.Discard), config: configuracaoPadrao()}

	linha := `{"cmd":"config",` +
		`"stun":["stun:um.servidor:3478","stun:dois.servidor:3478"],` +
		`"turn":[{"url":"turn:relay.astra:3478?transport=udp",` +
		`"user":"1800003600:u_123","senha":"YWJjZGVmZ2hpamtsbW5vcA=="}]}`

	if err := a.Servir(context.Background(), strings.NewReader(linha+"\n")); err != nil {
		t.Fatalf("servir a linha: %v", err)
	}

	if len(a.config.ICEServers) != 2 {
		t.Fatalf("esperava STUN e TURN, vieram %d blocos: %+v", len(a.config.ICEServers), a.config.ICEServers)
	}
	if got := a.config.ICEServers[0].URLs; len(got) != 2 || got[0] != "stun:um.servidor:3478" {
		t.Errorf("o STUN da linha não chegou inteiro: %v", got)
	}

	turn := a.config.ICEServers[1]
	if len(turn.URLs) != 1 || turn.URLs[0] != "turn:relay.astra:3478?transport=udp" {
		t.Errorf("o endereço do TURN não chegou: %v", turn.URLs)
	}
	if turn.Username != "1800003600:u_123" {
		t.Errorf("o usuário temporário não chegou: %q", turn.Username)
	}
	if turn.Credential != "YWJjZGVmZ2hpamtsbW5vcA==" {
		t.Errorf("a senha temporária não chegou: %q", turn.Credential)
	}

	pc, err := webrtc.NewPeerConnection(a.config)
	if err != nil {
		t.Fatalf("o pion recusou o que veio da linha: %v", err)
	}
	_ = pc.Close()
}

func TestOCaminhoDoGeloTemNome(t *testing.T) {
	casos := []struct {
		local, remoto webrtc.ICECandidateType
		espera        string
	}{
		{webrtc.ICECandidateTypeHost, webrtc.ICECandidateTypeHost, "direto, pela rede local"},
		{webrtc.ICECandidateTypeSrflx, webrtc.ICECandidateTypeSrflx, "direto, atravessando o NAT"},
		{webrtc.ICECandidateTypeRelay, webrtc.ICECandidateTypeSrflx, "por relay TURN"},
		{webrtc.ICECandidateTypeSrflx, webrtc.ICECandidateTypeRelay, "por relay TURN"},
	}
	for _, c := range casos {
		if got := caminhoDoGelo(c.local, c.remoto); got != c.espera {
			t.Errorf("%s/%s: esperava %q, veio %q", c.local, c.remoto, c.espera, got)
		}
	}
}
