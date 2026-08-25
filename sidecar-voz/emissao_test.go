package main

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

type coletor struct{ para chan Evento }

func (c coletor) Write(p []byte) (int, error) {
	var ev Evento
	if json.Unmarshal(p, &ev) == nil {
		select {
		case c.para <- ev:
		default:
		}
	}
	return len(p), nil
}

func TestPerfilSaiDoSPS(t *testing.T) {
	casos := []struct {
		nome   string
		fluxo  []byte
		quer   string
		espera bool
	}{
		{
			nome: "codigo de quatro bytes",

			fluxo:  []byte{0, 0, 0, 1, 0x67, 0x42, 0xe0, 0x1f, 0xAB, 0xCD},
			quer:   "42e01f",
			espera: true,
		},
		{
			nome:   "codigo de tres bytes",
			fluxo:  []byte{0, 0, 1, 0x67, 0x64, 0x00, 0x20, 0xFF},
			quer:   "640020",
			espera: true,
		},
		{

			nome: "SPS depois de outro NAL",
			fluxo: []byte{
				0, 0, 0, 1, 0x09, 0x10,
				0, 0, 0, 1, 0x67, 0x4d, 0x40, 0x1f,
			},
			quer:   "4d401f",
			espera: true,
		},
		{
			nome:   "sem SPS nenhum",
			fluxo:  []byte{0, 0, 0, 1, 0x41, 0x9A, 0x22, 0x11},
			espera: false,
		},
		{
			nome:   "curto demais para ter perfil",
			fluxo:  []byte{0, 0, 0, 1, 0x67},
			espera: false,
		},
	}

	for _, c := range casos {
		t.Run(c.nome, func(t *testing.T) {
			veio, ok := perfilDoSPS(c.fluxo)
			if ok != c.espera {
				t.Fatalf("achou=%v, esperava=%v (veio %q)", ok, c.espera, veio)
			}
			if ok && veio != c.quer {
				t.Fatalf("perfil %q, esperava %q", veio, c.quer)
			}
		})
	}
}

func TestEmissorTransmiteDeVerdade(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "teste-emissao")
	if err != nil {
		t.Fatalf("criar faixa: %v", err)
	}

	recolhidos := make(chan Evento, 256)
	e := NovoEmissor(faixa, NewEscritor(coletor{recolhidos}), nil)

	e.Ligar(AjustesDaTela{Monitor: 0, Largura: 1280, Altura: 720, Fps: 30, Kbps: 2500})
	time.Sleep(3 * time.Second)
	e.Desligar()

	close(recolhidos)
	var subiu, perfil, ritmo string
	var erro string
	for ev := range recolhidos {
		switch {
		case ev.Ev == EvErro:
			erro = ev.Msg
		case ev.Ev == EvTransmissao && ev.Tipo == "perfil":
			perfil = ev.Msg
		case ev.Ev == EvTransmissao && ev.Tipo == "ritmo":
			ritmo = ev.Msg
		case ev.Ev == EvTransmissao && ev.V == "1" && ev.Tipo != "":
			subiu = ev.Tipo + " " + ev.Msg
		}
	}

	t.Logf("subiu: %s", subiu)
	t.Logf("perfil no fluxo: %s (a faixa declara 42e01f)", perfil)
	t.Logf("ritmo: %s", ritmo)

	if erro != "" {
		t.Fatalf("a transmissão parou: %s", erro)
	}
	if subiu == "" {
		t.Fatal("o emissor nunca confirmou que subiu")
	}
	if ritmo == "" {
		t.Skip("nenhum relatório de ritmo em 3s — a tela estava parada demais; mexa numa janela e rode de novo")
	}
	if perfil == "" {
		t.Fatal("nenhum SPS no fluxo: o outro lado não teria como começar a decodificar")
	}

	if perfil[:2] != "42" {
		t.Errorf("o compressor emite perfil %s e a faixa declara Baseline (42e01f) — "+
			"pedir o perfil em `configurarSaida` parou de funcionar", perfil[:2])
	}
	if perfil[4:] != "1f" {
		t.Logf("nível %s no fluxo contra 1f declarado — `level-asymmetry-allowed=1` cobre isso", perfil[4:])
	}
}
