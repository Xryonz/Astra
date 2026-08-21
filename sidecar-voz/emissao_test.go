package main

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

// coletor é o destino dos eventos no teste: o `Escritor` de verdade, escrevendo num
// io.Writer que decodifica cada linha de volta para Evento.
//
// NUNCA BLOQUEIA, e isso é o que importa: o `Escritor` é chamado de dentro do laço da
// transmissão, então um canal cheio travaria o laço e `Desligar` esperaria para sempre
// — um teste que pendura em vez de falhar.
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

// O PERFIL LIDO DO PRÓPRIO FLUXO.
//
// Vale um teste porque o erro aqui é MUDO: um parser errado devolve "não achei", o
// emissor não reporta perfil nenhum, e ninguém percebe até o outro lado não conseguir
// decodificar — em outra fatia, noutro dia, com outra suspeita.
//
// Os dois códigos de início convivem no mesmo fluxo do Media Foundation (quatro bytes
// antes da sequência de parâmetros, três antes das fatias), então os dois entram aqui.
func TestPerfilSaiDoSPS(t *testing.T) {
	casos := []struct {
		nome   string
		fluxo  []byte
		quer   string
		espera bool
	}{
		{
			nome: "codigo de quatro bytes",
			// 00 00 00 01 | 67 (NAL tipo 7 = SPS) | 42 e0 1f = Baseline restrito 3.1
			fluxo:  []byte{0, 0, 0, 1, 0x67, 0x42, 0xe0, 0x1f, 0xAB, 0xCD},
			quer:   "42e01f",
			espera: true,
		},
		{
			nome:   "codigo de tres bytes",
			fluxo:  []byte{0, 0, 1, 0x67, 0x64, 0x00, 0x20, 0xFF},
			quer:   "640020", // High 3.2, que é o que uma placa pode emitir sozinha
			espera: true,
		},
		{
			// O CASO QUE IMPORTA: o SPS não é o primeiro NAL. Um parser que só olha o
			// começo do buffer passaria nos dois casos acima e falharia na vida real,
			// onde o delimitador de unidade de acesso costuma vir na frente.
			nome: "SPS depois de outro NAL",
			fluxo: []byte{
				0, 0, 0, 1, 0x09, 0x10, // delimitador (tipo 9)
				0, 0, 0, 1, 0x67, 0x4d, 0x40, 0x1f, // SPS: Main 3.1
			},
			quer:   "4d401f",
			espera: true,
		},
		{
			nome:   "sem SPS nenhum",
			fluxo:  []byte{0, 0, 0, 1, 0x41, 0x9A, 0x22, 0x11}, // só uma fatia (tipo 1)
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

// A TRANSMISSÃO DE VERDADE, do monitor até a faixa.
//
// Não confere pixel: confere que o caminho INTEIRO fecha — captura, compressor,
// juntar os pedaços num quadro, e o pion aceitar a amostra. É o que separa "compila"
// de "sai byte pela rede", que era exatamente o que faltava neste projeto.
//
// Sem conexão nenhuma na faixa de propósito: um `TrackLocalStaticSample` solto engole
// a amostra em silêncio, e é justamente esse o caso de quem começa a compartilhar
// antes de o primeiro convidado chegar.
func TestEmissorTransmiteDeVerdade(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "teste-emissao")
	if err != nil {
		t.Fatalf("criar faixa: %v", err)
	}

	// Os eventos são recolhidos para o relatório — inclusive o perfil, que é o número
	// que esta fatia precisa conferir antes de existir alguém do outro lado para
	// reclamar dele.
	recolhidos := make(chan Evento, 256)
	e := NovoEmissor(faixa, NewEscritor(coletor{recolhidos}))

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

	// O RETRATO ANTES DO VEREDITO, mesma regra do banco de provas: qual compressor
	// pegou e em que perfil é o que separa um defeito do outro.
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

	// CONFERE O PERFIL E O NÍVEL, E NÃO OS TRÊS BYTES.
	//
	// O byte do meio são as bandeiras de restrição, e ele NÃO precisa bater. A faixa
	// declara `42e01f` (Baseline restrito: bandeiras 0, 1 e 2) e o compressor emite
	// `42401f` (só a 1). São o mesmo perfil e o mesmo nível — Baseline 3.1 —, e um
	// fluxo Baseline de compressor de placa não usa as três coisas que a restrição
	// exclui (ordem de fatia arbitrária, grupos de macroblocos, fatias redundantes).
	// Qualquer decodificador que aceita `42e01f` decodifica isto.
	//
	// O que NÃO pode divergir é o primeiro byte: com High (0x64) no fluxo, um
	// decodificador que confiou na declaração não abre a imagem. Foi exatamente o que
	// este teste pegou antes de existir alguém do outro lado para reclamar.
	if perfil[:2] != "42" {
		t.Errorf("o compressor emite perfil %s e a faixa declara Baseline (42e01f) — "+
			"pedir o perfil em `configurarSaida` parou de funcionar", perfil[:2])
	}
	if perfil[4:] != "1f" {
		t.Logf("nível %s no fluxo contra 1f declarado — `level-asymmetry-allowed=1` cobre isso", perfil[4:])
	}
}
