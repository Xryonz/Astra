package main

// SONDA: DOIS DEFEITOS DA TELA PARADA, MEDIDOS E AINDA NÃO CORRIGIDOS.
//
// ESTE TESTE FALHA HOJE, E FALHAR É O PONTO. Ele fica atrás do próprio interruptor
// (`ASTRA_TESTE_FIMDATELA=1`) para não pintar a suíte de vermelho, e existe porque a
// reprodução vale mais do que a descrição: quando o conserto vier, a prova de que
// funcionou é este arquivo passando.
//
// ---- Defeito 1: quem para de transmitir fica congelado no palco alheio ----
//
// Apareceu lendo `receberTela` para outra coisa, e é do tipo que nenhum teste de peça
// pega: `ReadRTP` bloqueia sem prazo, e quando o outro lado para de escrever na faixa
// NÃO CHEGA SINAL NENHUM. Não há pacote de "acabou" em RTP — a faixa continua declarada
// no SDP, muda só que nada trafega nela. A última imagem de quem parou fica na tela de
// todo mundo, parecendo ao vivo, até a pessoa sair da chamada. Pior que não mostrar
// nada, porque quem olha não tem como saber que está olhando o passado.
//
// MEDIDO: dez segundos depois de `emissor.Desligar()`, quem assiste continua com a tela
// no ar. É o que este teste reproduz.
//
// O CONSERTO ÓBVIO NÃO SERVE. "Dois segundos sem pacote = acabou" acusaria falsamente
// quem compartilha um documento e para de mexer: com a tela parada o emissor não manda
// pacote nenhum, de propósito (`emissao.go`, o ramo `textura == 0`). E a tela parada é
// justamente o caso de uso de quem compartilha para alguém LER.
//
// ---- Defeito 2, achado no caminho e mais grave: a tela parada nunca abre ----
//
// O pedido de quadro-chave (PLI) é atendido em `emissao.go` DEPOIS do `continue` do
// ramo "nada mudou". Ou seja: com a tela parada, `querChave` fica pendurado em `true` e
// ninguém o atende. Quem entra numa chamada onde alguém já está compartilhando uma tela
// parada vê "abrindo a tela de fulano…" até a pessoa mexer o mouse.
//
// Isto é pior que o defeito 1 e tem conserto local: em vez de `continue`, chamar
// `tela.QuadroAtual` — a função que já existe (nasceu para as miniaturas do seletor) e
// devolve o quadro mesmo sem mudança. O mesmo remédio mantém o fluxo vivo e resolveria
// o defeito 1 de quebra, ao custo de um quadro comprimido a cada poucos segundos de
// tela parada.
//
// A escolha de gastar essa banda é do dono, e por isso isto está numa sonda e não num
// commit de conserto.

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

func TestQuemParaDeTransmitirSaiDoPalcoDeQuemAssiste(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_FIMDATELA") == "" {
		t.Skip("defina ASTRA_TESTE_FIMDATELA=1 — este teste FALHA de propósito, ver o topo do arquivo")
	}
	precisaDeTela(t)
	precisaDeVideo(t)

	ctx, cancelar := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancelar()

	canoA, escreveA := io.Pipe()
	canoB, escreveB := io.Pipe()

	tela, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "teste-tela")
	if err != nil {
		t.Fatalf("criar faixa de tela: %v", err)
	}

	config := webrtc.Configuration{}
	parA, err := NovoPar("B", config, nil, tela, nil, nil, NewEscritor(escreveA))
	if err != nil {
		t.Fatalf("criar quem transmite: %v", err)
	}
	defer parA.Fechar()

	// SEM CANO DE ENTREGA, de propósito: este teste não olha pixel nenhum, só o aviso de
	// que há (e de que deixou de haver) tela. `Mandar` num ponteiro nulo é seguro.
	parB, err := NovoPar("A", config, nil, nil, nil, nil, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer parB.Fechar()

	var diario struct {
		mu     sync.Mutex
		linhas []string
	}
	registrar := func(f string, a ...any) {
		diario.mu.Lock()
		diario.linhas = append(diario.linhas, fmt.Sprintf(f, a...))
		diario.mu.Unlock()
	}
	defer func() {
		diario.mu.Lock()
		defer diario.mu.Unlock()
		for _, l := range diario.linhas {
			t.Log(l)
		}
	}()

	// O AVISO DE TELA, "1" quando há e "0" quando deixou de haver. É a única coisa que
	// este teste observa.
	avisos := make(chan string, 32)
	rotear := func(quem string, de io.Reader, para *Par) {
		linhas := bufio.NewScanner(de)
		linhas.Buffer(make([]byte, 0, 64*1024), 1024*1024)
		for linhas.Scan() {
			var ev Evento
			if json.Unmarshal(linhas.Bytes(), &ev) != nil {
				continue
			}
			if ev.Ev != EvSinal {
				registrar("[%s] %s par=%s tipo=%s v=%s msg=%s", quem, ev.Ev, ev.Par, ev.Tipo, ev.V, ev.Msg)
				if ev.Ev == EvTelaDeOutro && ev.Tipo != "ritmo" {
					select {
					case avisos <- ev.V:
					default:
					}
				}
				continue
			}
			if err := para.Receber(ctx, ev.Tipo, ev.Dados); err != nil {
				registrar("[%s] entregar %s: %v", quem, ev.Tipo, err)
			}
		}
	}
	go rotear("transmite", canoA, parB)
	go rotear("assiste", canoB, parA)

	if err := parA.Oferecer(ctx); err != nil {
		t.Fatalf("oferecer: %v", err)
	}

	canoE, escreveE := io.Pipe()
	go func() {
		linhas := bufio.NewScanner(canoE)
		for linhas.Scan() {
			var ev Evento
			if json.Unmarshal(linhas.Bytes(), &ev) == nil {
				registrar("[emissor] %s tipo=%s v=%s msg=%s", ev.Ev, ev.Tipo, ev.V, ev.Msg)
			}
		}
	}()
	emissor := NovoEmissor(tela, NewEscritor(escreveE))
	parA.pedirQuadroChave = emissor.PedirQuadroChave
	emissor.Ligar(AjustesDaTela{Monitor: 0, Largura: 1280, Altura: 720, Fps: 30, Kbps: 2500})
	defer emissor.Desligar()

	esperar := func(qual, oQue string, prazo time.Duration) {
		t.Helper()
		limite := time.After(prazo)
		for {
			select {
			case v := <-avisos:
				if v == qual {
					return
				}
			case <-limite:
				t.Fatalf("%s não chegou em %v", oQue, prazo)
			}
		}
	}

	esperar("1", `o aviso de "há tela chegando"`, 30*time.Second)
	t.Log("a tela subiu")

	// E AGORA PARA. Do ponto de vista da rede não acontece nada: a faixa continua
	// negociada, os pacotes é que cessam.
	emissor.Desligar()
	comeco := time.Now()
	esperar("0", `o aviso de "a tela acabou"`, 10*time.Second)
	t.Logf("a tela sumiu do palco %v depois de a transmissão parar",
		time.Since(comeco).Round(100*time.Millisecond))
}
