package main

import (
	"bufio"
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

func TestOPalcoDecideDeQuemEATela(t *testing.T) {
	a := &App{}

	if !a.assistindo("qualquer-um") {
		t.Error("sem o Astra ter dito nada, a tela tem de ser decodificada")
	}

	nenhum := ""
	a.palco.Store(&nenhum)
	if a.assistindo("qualquer-um") {
		t.Error("com o palco vazio, nenhuma tela deve ser decodificada")
	}
	if a.assistindo("") {
		t.Error("id vazio não é uma pessoa, e não pode casar com o palco vazio")
	}

	quem := "B"
	a.palco.Store(&quem)
	if !a.assistindo("B") {
		t.Error("a tela no palco tem de ser decodificada")
	}
	if a.assistindo("C") {
		t.Error("a tela fora do palco não pode ser decodificada")
	}
}

func TestOComandoDeAssistirTrocaOPalco(t *testing.T) {
	a := &App{}
	ctx := context.Background()

	if err := a.Executar(ctx, Comando{Cmd: CmdAssistir, Par: "B"}); err != nil {
		t.Fatalf("assistir B: %v", err)
	}
	if !a.assistindo("B") || a.assistindo("C") {
		t.Error("o comando não pôs B no palco")
	}

	if err := a.Executar(ctx, Comando{Cmd: CmdAssistir, Par: "C"}); err != nil {
		t.Fatalf("assistir C: %v", err)
	}
	if !a.assistindo("C") || a.assistindo("B") {
		t.Error("o comando não trocou o palco de B para C")
	}

	if err := a.Executar(ctx, Comando{Cmd: CmdAssistir}); err != nil {
		t.Fatalf("assistir ninguém: %v", err)
	}
	if a.assistindo("B") || a.assistindo("C") {
		t.Error("o comando sem par tinha de esvaziar o palco")
	}

	cmd := Comando{Cmd: CmdAssistir, Par: "B", Dados: strings.Repeat("x", 1024)}
	if err := a.Executar(ctx, cmd); err != nil {
		t.Fatalf("assistir B de novo: %v", err)
	}
	cmd.Par = "C"
	if !a.assistindo("B") || a.assistindo("C") {
		t.Error("o palco mudou junto com o comando que o definiu — a cópia sumiu")
	}
}

func TestATelaForaDoPalcoNaoEDecodificada(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	ctx, cancelar := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancelar()

	ouvinte, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("escutar: %v", err)
	}
	defer ouvinte.Close()
	t.Setenv("ASTRA_QUADROS", ouvinte.Addr().String())
	t.Setenv("ASTRA_QUADROS_SEGREDO", "segredo-de-teste")

	chegou := make(chan int, 1)
	var total atomic.Int64
	go func() {
		con, err := ouvinte.Accept()
		if err != nil {
			return
		}
		defer con.Close()
		leitor := bufio.NewReaderSize(con, 1<<16)
		if _, err := leitor.ReadString('\n'); err != nil {
			return
		}
		cab := make([]byte, cabecalhoDoQuadro)
		for {
			if _, err := io.ReadFull(leitor, cab); err != nil {
				return
			}
			if binary.LittleEndian.Uint32(cab[0:]) != marcaDoQuadro {
				return
			}
			tamPar := binary.LittleEndian.Uint32(cab[4:])
			bytes := int(binary.LittleEndian.Uint32(cab[20:]))
			if _, err := io.CopyN(io.Discard, leitor, int64(tamPar)); err != nil {
				return
			}
			if _, err := io.CopyN(io.Discard, leitor, int64(bytes)); err != nil {
				return
			}
			total.Add(1)
			select {
			case chegou <- bytes:
			default:
			}
		}
	}()

	entrega := NovaEntrega()
	if entrega == nil {
		t.Fatal("a entrega não subiu")
	}
	defer entrega.Fechar()

	canoA, escreveA := io.Pipe()
	canoB, escreveB := io.Pipe()

	plateia := NovaPlateia()

	config := webrtc.Configuration{}
	parA, err := NovoPar("B", config, nil, plateia, nil, nil, NewEscritor(escreveA))
	if err != nil {
		t.Fatalf("criar quem transmite: %v", err)
	}
	defer parA.Fechar()

	parB, err := NovoPar("A", config, nil, nil, nil, entrega, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer parB.Fechar()

	noPalco := atomic.Bool{}
	parB.querVer = noPalco.Load

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

	descartes := make(chan string, 4)
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
				if ev.Ev == EvTelaDeOutro && strings.Contains(ev.Msg, "descartados") {
					select {
					case descartes <- ev.Msg:
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
	emissor := NovoEmissor(plateia, NewEscritor(escreveE), nil)

	parA.pedirQuadroChave = emissor.PedirQuadroChave
	emissor.Ligar(AjustesDaTela{Monitor: 0, Largura: 1280, Altura: 720, Fps: 30, Kbps: 2500})
	defer emissor.Desligar()

	select {
	case msg := <-descartes:
		t.Logf("o pacote chegou e foi descartado: %s", msg)
	case <-time.After(40 * time.Second):
		t.Fatal("nenhum relatório de descarte em 40s: ou a transmissão não subiu, " +
			"ou a faixa parou de ser lida — e faixa sem leitor entope o buffer do pion")
	}

	if n := total.Load(); n > 0 {
		t.Fatalf("%d quadros atravessaram o cano sem ninguém olhando", n)
	}

	noPalco.Store(true)
	comeco := time.Now()
	select {
	case n := <-chegou:
		abriu := time.Since(comeco)
		t.Logf("a imagem abriu em %v depois de subir ao palco (%d bytes)",
			abriu.Round(10*time.Millisecond), n)

		if abriu > 2*time.Second {
			t.Errorf("a imagem levou %v para abrir; o pedido de quadro-chave não está sendo atendido",
				abriu.Round(10*time.Millisecond))
		}
	case <-time.After(20 * time.Second):
		t.Fatal("subiu ao palco e a imagem não abriu em 20s")
	}

	antes := total.Load()
	time.Sleep(2 * time.Second)
	if vieram := total.Load() - antes; vieram < 20 {
		t.Errorf("em 2s depois de abrir vieram %d quadros; a 30 por segundo eram para ser ~60", vieram)
	} else {
		t.Logf("a imagem seguiu correndo: %d quadros em 2s", vieram)
	}
}
