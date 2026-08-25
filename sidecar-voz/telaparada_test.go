package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

func TestQuemParaDeTransmitirSaiDoPalcoDeQuemAssiste(t *testing.T) {
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
	emissor := NovoEmissor(tela, NewEscritor(escreveE), nil)
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

	emissor.Desligar()
	comeco := time.Now()
	esperar("0", `o aviso de "a tela acabou"`, 15*time.Second)
	levou := time.Since(comeco)
	t.Logf("a tela sumiu do palco %v depois de a transmissão parar", levou.Round(100*time.Millisecond))

	if levou < silencioQueEncerra/2 {
		t.Errorf("sumiu em %v, muito antes do silêncio de %v: a faixa deixou de ser lida",
			levou.Round(10*time.Millisecond), silencioQueEncerra)
	}
	if levou > silencioQueEncerra+3*time.Second {
		t.Errorf("levou %v para sumir, e o silêncio que encerra é %v",
			levou.Round(10*time.Millisecond), silencioQueEncerra)
	}
}

func TestATransmissaoVivaNaoSomeDoPalco(t *testing.T) {
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
	parB, err := NovoPar("A", config, nil, nil, nil, nil, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer parB.Fechar()

	var mu sync.Mutex
	var relatorios []string
	apagou := make(chan struct{}, 1)
	subiu := make(chan struct{}, 1)
	rotear := func(de io.Reader, para *Par) {
		linhas := bufio.NewScanner(de)
		linhas.Buffer(make([]byte, 0, 64*1024), 1024*1024)
		for linhas.Scan() {
			var ev Evento
			if json.Unmarshal(linhas.Bytes(), &ev) != nil {
				continue
			}
			if ev.Ev == EvSinal {
				_ = para.Receber(ctx, ev.Tipo, ev.Dados)
				continue
			}
			if ev.Ev != EvTelaDeOutro {
				continue
			}
			mu.Lock()
			relatorios = append(relatorios, ev.Tipo+" v="+ev.V+" "+ev.Msg)
			mu.Unlock()
			switch {
			case ev.V == "0":
				select {
				case apagou <- struct{}{}:
				default:
				}
			case ev.Tipo == "faixa":
				select {
				case subiu <- struct{}{}:
				default:
				}
			}
		}
	}
	go rotear(canoA, parB)
	go rotear(canoB, parA)

	if err := parA.Oferecer(ctx); err != nil {
		t.Fatalf("oferecer: %v", err)
	}

	canoE, escreveE := io.Pipe()
	go func() {
		linhas := bufio.NewScanner(canoE)
		for linhas.Scan() {
		}
	}()
	emissor := NovoEmissor(tela, NewEscritor(escreveE), nil)
	parA.pedirQuadroChave = emissor.PedirQuadroChave
	emissor.Ligar(AjustesDaTela{Monitor: 0, Largura: 1280, Altura: 720, Fps: 30, Kbps: 2500})
	defer emissor.Desligar()

	select {
	case <-subiu:
	case <-time.After(30 * time.Second):
		t.Fatal("a tela nunca subiu")
	}

	select {
	case <-apagou:
		mu.Lock()
		defer mu.Unlock()
		t.Fatalf("a tela sumiu do palco com a transmissão no ar. Relatórios:\n  %s",
			strings.Join(relatorios, "\n  "))
	case <-time.After(silencioQueEncerra + 3*time.Second):
	}

	mu.Lock()
	defer mu.Unlock()
	t.Logf("a tela ficou no ar os %v inteiros. Último relatório: %s",
		silencioQueEncerra+3*time.Second, relatorios[len(relatorios)-1])
}
