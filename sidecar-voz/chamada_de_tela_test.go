package main

import (
	"bufio"
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"sync"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

func TestATransmissaoAtravessaDePontaAPonta(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	ctx, cancelar := context.WithTimeout(context.Background(), 40*time.Second)
	defer cancelar()

	ouvinte, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("escutar: %v", err)
	}
	defer ouvinte.Close()
	t.Setenv("ASTRA_QUADROS", ouvinte.Addr().String())
	t.Setenv("ASTRA_QUADROS_SEGREDO", "segredo-de-teste")

	type recebido struct {
		par     string
		largura int
		altura  int
		passo   int
		bytes   int
	}
	chegou := make(chan recebido, 8)
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
			r := recebido{
				largura: int(binary.LittleEndian.Uint32(cab[8:])),
				altura:  int(binary.LittleEndian.Uint32(cab[12:])),
				passo:   int(binary.LittleEndian.Uint32(cab[16:])),
				bytes:   int(binary.LittleEndian.Uint32(cab[20:])),
			}
			par := make([]byte, tamPar)
			if _, err := io.ReadFull(leitor, par); err != nil {
				return
			}
			r.par = string(par)
			if _, err := io.CopyN(io.Discard, leitor, int64(r.bytes)); err != nil {
				return
			}
			select {
			case chegou <- r:
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
	emissor.Ligar(AjustesDaTela{Monitor: 0, Largura: 1280, Altura: 720, Fps: 30, Kbps: 2500})
	defer emissor.Desligar()

	select {
	case r := <-chegou:
		t.Logf("o quadro atravessou: de %q, %dx%d, passo %d, %d bytes",
			r.par, r.largura, r.altura, r.passo, r.bytes)
		if r.par != "A" {
			t.Errorf("o quadro veio marcado como %q, e quem assiste conhece o outro por %q", r.par, "A")
		}
		if r.largura != 1280 || r.altura != 720 {
			t.Errorf("chegou em %dx%d, e foi transmitido em 1280x720", r.largura, r.altura)
		}
		if minimo := r.passo * r.altura * 3 / 2; r.bytes < minimo {
			t.Errorf("vieram %d bytes e NV12 pede %d", r.bytes, minimo)
		}
	case <-time.After(30 * time.Second):
		t.Fatal("nenhum quadro atravessou em 30s: a tela saiu de um lado e não chegou no outro")
	}
}
