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

// A TRANSMISSÃO INTEIRA, DE PONTA A PONTA, DENTRO DE UM PROCESSO.
//
// Este é o teste que faltava. Os outros provam peças — o compressor comprime, o
// descompressor descomprime, o cano entrega no formato certo. Este prova o CAMINHO, que
// é onde moram os defeitos que nenhuma peça sozinha revela:
//
//	tela -> compressor -> faixa -> RTP -> rede -> remontador -> descompressor -> cano
//
// O que só aparece aqui: se o perfil declarado no SDP casa com o que o compressor emite,
// se os cinquenta pacotes de um quadro-chave são remontados na ordem, se o
// descompressor aceita EXATAMENTE o que este compressor produz, e se o quadro chega do
// outro lado com a forma certa. Cada um desses já tinha como estar errado com todas as
// peças passando nos testes delas.
//
// Dois pares no mesmo processo, sinalizando por canos — é o arranjo que o teste de voz
// já usa, e é o servidor do Astra reduzido ao essencial.
func TestATransmissaoAtravessaDePontaAPonta(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	ctx, cancelar := context.WithTimeout(context.Background(), 40*time.Second)
	defer cancelar()

	// O ASTRA DE MENTIRA: escuta o cano de quadros e conta o que chegou.
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

	tela, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "teste-tela")
	if err != nil {
		t.Fatalf("criar faixa de tela: %v", err)
	}

	config := webrtc.Configuration{}
	// QUEM TRANSMITE NÃO PRECISA DE ENTREGA e quem assiste não precisa de faixa: é o
	// arranjo real, e é justamente o assimétrico que quebra negociação quando algo
	// está declarado a mais ou a menos.
	parA, err := NovoPar("B", config, nil, tela, nil, nil, NewEscritor(escreveA))
	if err != nil {
		t.Fatalf("criar quem transmite: %v", err)
	}
	defer parA.Fechar()

	parB, err := NovoPar("A", config, nil, nil, nil, entrega, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer parB.Fechar()

	// O DIÁRIO É RECOLHIDO E IMPRESSO NO FIM, e não gritado de dentro das goroutines.
	//
	// `t.Logf` chamado depois que o teste termina faz o Go PANICAR — e as goroutines
	// aqui continuam vivas durante o desligamento das conexões, que é justamente quando
	// mais eventos saem. Custou uma medição inteira: seis execuções contadas como falha
	// quando o que falhava era o arnês, e o caminho de mídia estava correto.
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
				// TUDO QUE NÃO É ENVELOPE VIRA REGISTRO. Quando este teste falha, a
				// resposta quase sempre está num evento que o roteador estava jogando
				// fora: o estado da conexão, ou o erro de quem não achou descompressor.
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

	// A TRANSMISSÃO COMEÇA JUNTO COM A CONEXÃO, e não depois de ela fechar. É o que
	// acontece de verdade — a pessoa aperta o botão e o ICE ainda está negociando — e
	// exercita o caso em que os primeiros quadros são escritos numa faixa sem destino.
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
