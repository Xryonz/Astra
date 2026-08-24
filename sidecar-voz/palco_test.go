package main

// SÓ A TELA QUE ALGUÉM ESTÁ OLHANDO É DECODIFICADA.
//
// São dois testes com ambições bem diferentes, e vale saber qual prova o quê:
//
//	TestOPalcoDecideDeQuemEATela   a REGRA, em memória, sem hardware nenhum
//	TestATelaForaDoPalcoNaoEDecodificada   o CAMINHO, com placa, rede e descompressor
//
// O primeiro roda sempre e é instantâneo. O segundo precisa de monitor e de compressor de
// H.264, e é o único que consegue provar a única coisa que de fato importa: que o pacote
// CHEGA e mesmo assim não vira imagem.

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

// A REGRA, isolada do resto: quem o Astra põe no palco é quem tem a tela decodificada.
//
// O CASO QUE MERECE TESTE É O NULO, e ele é contraintuitivo o bastante para justificar
// este arquivo sozinho: "o Astra ainda não disse nada" tem de valer SIM, não não. Se
// valesse não, rodar `astra-voz.exe` à mão — ou qualquer teste de ponta a ponta que não
// mande `assistir` — simplesmente não abriria imagem, e o motivo não estaria em lugar
// nenhum do registro. É o tipo de padrão que só se descobre depois de uma hora.
func TestOPalcoDecideDeQuemEATela(t *testing.T) {
	a := &App{}

	if !a.assistindo("qualquer-um") {
		t.Error("sem o Astra ter dito nada, a tela tem de ser decodificada")
	}

	// O "ninguém" EXPLÍCITO — que é o que chega quando a sala de voz sai do palco do
	// Astra e a pessoa vai ler uma conversa de texto sem largar a chamada.
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

// O COMANDO QUE VEM PELA PONTE, e não só a regra que ele alimenta.
//
// O que este teste guarda é a CÓPIA dentro de `Executar`. Guardar `&cmd.Par` funcionaria
// igualzinho nos dois primeiros casos abaixo e prenderia o `Comando` inteiro na memória
// pelo tempo que o palco durar — e o `Comando` carrega o campo onde cabe um SDP. É um
// vazamento pequeno, silencioso e que nenhuma asserção de comportamento pegaria; a única
// forma de fixá-lo é um teste que diga que a cópia existe.
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

	// NINGUÉM, que é o comando de sair da sala de voz sem largar a chamada.
	if err := a.Executar(ctx, Comando{Cmd: CmdAssistir}); err != nil {
		t.Fatalf("assistir ninguém: %v", err)
	}
	if a.assistindo("B") || a.assistindo("C") {
		t.Error("o comando sem par tinha de esvaziar o palco")
	}

	// A CÓPIA. Um `Comando` cheio, guardado depois de usado: se o palco apontasse para
	// dentro dele, esta escrita mudaria de quem é a tela no palco.
	cmd := Comando{Cmd: CmdAssistir, Par: "B", Dados: strings.Repeat("x", 1024)}
	if err := a.Executar(ctx, cmd); err != nil {
		t.Fatalf("assistir B de novo: %v", err)
	}
	cmd.Par = "C"
	if !a.assistindo("B") || a.assistindo("C") {
		t.Error("o palco mudou junto com o comando que o definiu — a cópia sumiu")
	}
}

// O CAMINHO INTEIRO, com o pacote chegando de verdade.
//
// Este é o teste que separa "não decodifica" de "não recebe" — e a diferença é tudo. Um
// laço que parasse de LER a faixa também não decodificaria nada, e estaria errado do
// jeito mais caro possível: pacote não consumido se acumula no buffer do pion até o
// processo ficar sem memória, com a pessoa transmitindo do outro lado sem saber.
//
// Por isso a asserção do meio não é um tempo esgotado. É o relatório de segundo dizendo,
// com número, quantos pacotes chegaram e foram descartados. Só depois de ter essa prova é
// que o teste confere que nenhum quadro atravessou o cano.
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

	// DOIS CONTADORES, e a duplicação tem razão de ser: o canal avisa da CHEGADA do
	// primeiro quadro (é o que se espera com prazo), e o total conta os que vieram (é o
	// que prova que a imagem continuou correndo, e não abriu uma vez e parou).
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

	parB, err := NovoPar("A", config, nil, nil, nil, entrega, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer parB.Fechar()

	// O PALCO DESTE TESTE. Começa vazio — é o estado de quem entrou na chamada e está
	// lendo texto enquanto o outro transmite.
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

	// A PROVA DE QUE O PACOTE CHEGOU. Vem do relatório de segundo de `receberTela`.
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
	emissor := NovoEmissor(tela, NewEscritor(escreveE), nil)
	// O PEDIDO DE QUADRO-CHAVE PRECISA CHEGAR NO EMISSOR, e ligá-lo é o que o `App` faz de
	// verdade (ver `abrirPar`). Sem esta linha o arnês mede outro caminho: o PLI sai, cai
	// no vazio, e a imagem só abre no quadro-chave natural — cinco segundos. Foi
	// exatamente o que aconteceu na primeira execução deste teste, e o número (4,21s) não
	// era da fatia, era do arnês.
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

	// COM A PROVA NA MÃO, a asserção que interessa.
	if n := total.Load(); n > 0 {
		t.Fatalf("%d quadros atravessaram o cano sem ninguém olhando", n)
	}

	// E AGORA SOBE AO PALCO. O descompressor abre, o pedido de quadro-chave sai, e a
	// imagem tem de aparecer — sem esperar os cinco segundos do quadro-chave natural.
	noPalco.Store(true)
	comeco := time.Now()
	select {
	case n := <-chegou:
		abriu := time.Since(comeco)
		t.Logf("a imagem abriu em %v depois de subir ao palco (%d bytes)",
			abriu.Round(10*time.Millisecond), n)
		// DOIS SEGUNDOS É O TETO, e o número tem origem: o quadro-chave natural deste
		// compressor sai a cada cinco segundos, e é justamente ele que o pedido de imagem
		// existe para não esperar. Passar de dois segundos quer dizer que o pedido não
		// está chegando — a falha silenciosa que faz a troca de palco parecer travamento.
		if abriu > 2*time.Second {
			t.Errorf("a imagem levou %v para abrir; o pedido de quadro-chave não está sendo atendido",
				abriu.Round(10*time.Millisecond))
		}
	case <-time.After(20 * time.Second):
		t.Fatal("subiu ao palco e a imagem não abriu em 20s")
	}

	// E CONTINUA CORRENDO. Abrir uma vez e parar é um defeito diferente de não abrir, com
	// a mesma cara para quem assiste: a imagem congela no primeiro quadro. A folga é
	// grande de propósito — um terço da taxa transmitida —, porque o que se mede aqui é
	// "a imagem está viva", não a taxa exata.
	antes := total.Load()
	time.Sleep(2 * time.Second)
	if vieram := total.Load() - antes; vieram < 20 {
		t.Errorf("em 2s depois de abrir vieram %d quadros; a 30 por segundo eram para ser ~60", vieram)
	} else {
		t.Logf("a imagem seguiu correndo: %d quadros em 2s", vieram)
	}
}
