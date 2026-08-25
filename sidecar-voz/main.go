package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/signal"
	"sync"
	"sync/atomic"

	"github.com/pion/webrtc/v4"
)

var marcaDeOrdem = []byte{0xEF, 0xBB, 0xBF}

func main() {

	ctx, parar := signal.NotifyContext(context.Background(), os.Interrupt)
	defer parar()

	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeOpus, "audio", "astra-microfone")
	if err != nil {
		fmt.Fprintf(os.Stderr, "criar faixa de microfone: %v\n", err)
		os.Exit(1)
	}

	tela, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "astra-tela")
	if err != nil {
		fmt.Fprintf(os.Stderr, "criar faixa de tela: %v\n", err)
		os.Exit(1)
	}

	escritor := NewEscritor(os.Stdout)
	misturador := NovoMisturador()

	dllOpus := os.Getenv("ASTRA_OPUS_DLL")
	if dllOpus == "" {
		dllOpus = "opus-0.dll"
	}

	motor := NovoMotor(faixa, misturador, escritor, dllOpus)
	if err := motor.Ligar(ctx); err != nil {

		fmt.Fprintf(os.Stderr, "ligar o motor de áudio: %v\n", err)
		escritor.Manda(Evento{Ev: EvErro, Msg: "áudio indisponível: " + err.Error()})
		os.Exit(1)
	}

	entrega := NovaEntrega()

	app := &App{
		saida:      escritor,
		faixa:      faixa,
		tela:       tela,
		emissor:    NovoEmissor(tela, escritor, entrega),
		entrega:    entrega,
		misturador: misturador,
		motor:      motor,
		pares:      make(map[string]*Par),

		config: webrtc.Configuration{
			ICEServers: []webrtc.ICEServer{{URLs: []string{"stun:stun.l.google.com:19302"}}},
		},
	}
	defer app.Fechar()

	app.saida.Manda(Evento{Ev: EvPronto})

	if err := app.Servir(ctx, os.Stdin); err != nil && !errors.Is(err, io.EOF) {
		fmt.Fprintf(os.Stderr, "leitura da ponte terminou: %v\n", err)
		os.Exit(1)
	}
}

type App struct {
	saida      *Escritor
	faixa      *webrtc.TrackLocalStaticSample
	tela       *webrtc.TrackLocalStaticSample
	emissor    *Emissor
	entrega    *EntregaDeQuadros
	misturador *Misturador
	motor      *Motor
	config     webrtc.Configuration

	palco atomic.Pointer[string]

	mu    sync.Mutex
	pares map[string]*Par
}

func (a *App) assistindo(id string) bool {
	quem := a.palco.Load()
	if quem == nil {
		return true
	}

	return *quem != "" && *quem == id
}

func (a *App) Servir(ctx context.Context, entrada io.Reader) error {
	linhas := bufio.NewScanner(entrada)

	linhas.Buffer(make([]byte, 0, 64*1024), 1024*1024)

	for linhas.Scan() {
		select {
		case <-ctx.Done():
			return nil
		default:
		}

		bruto := bytes.TrimPrefix(linhas.Bytes(), marcaDeOrdem)
		if len(bruto) == 0 {
			continue
		}

		var cmd Comando
		if err := json.Unmarshal(bruto, &cmd); err != nil {

			a.saida.Manda(Evento{Ev: EvErro, Msg: fmt.Sprintf("comando ilegível: %v", err)})
			continue
		}

		if cmd.Cmd == CmdSair {
			return nil
		}
		if err := a.Executar(ctx, cmd); err != nil {
			a.saida.Manda(Evento{Ev: EvErro, Par: cmd.Par, Msg: err.Error()})
		}
	}
	return linhas.Err()
}

func (a *App) Executar(ctx context.Context, cmd Comando) error {
	switch cmd.Cmd {
	case CmdConfig:
		a.aplicarConfig(cmd)
		return nil

	case CmdConectar:
		par, err := a.abrirPar(cmd.Par)
		if err != nil {
			return fmt.Errorf("abrir par %s: %w", cmd.Par, err)
		}

		if cmd.Iniciar {
			if err := par.Oferecer(ctx); err != nil {
				return fmt.Errorf("oferecer a %s: %w", cmd.Par, err)
			}
		}
		return nil

	case CmdSinal:
		a.mu.Lock()
		par, ok := a.pares[cmd.Par]
		a.mu.Unlock()
		if !ok {

			return nil
		}
		if err := par.Receber(ctx, cmd.Tipo, cmd.Dados); err != nil {
			return fmt.Errorf("sinal %s de %s: %w", cmd.Tipo, cmd.Par, err)
		}
		return nil

	case CmdDesconectar:
		a.fecharPar(cmd.Par)
		return nil

	case CmdMudo:
		a.aplicarMudo(cmd.Ligado)
		return nil

	case CmdSurdo:
		a.motor.DefinirSurdo(cmd.Ligado)
		return nil

	case CmdTratamento:
		a.motor.DefinirTratamento(AjustesDaVoz{Eco: cmd.Eco, Ruido: cmd.Ruido, Ganho: cmd.Ganho})
		return nil

	case CmdTransmitir:
		if !cmd.Ligado {
			a.emissor.Desligar()
			return nil
		}

		a.emissor.Ligar(AjustesDaTela{
			Monitor: cmd.Monitor,
			Largura: cmd.Largura,
			Altura:  cmd.Altura,
			Fps:     cmd.Fps,
			Kbps:    cmd.Kbps,
		})
		return nil

	case CmdAparelhos:

		for _, s := range []struct {
			nome    string
			sentido int
		}{{"entrada", sentidoEntrada}, {"saida", sentidoSaida}} {
			lista, err := ListarNumaThreadPropria(s.sentido)
			if err != nil {
				return fmt.Errorf("listar aparelhos de %s: %w", s.nome, err)
			}
			a.saida.Manda(Evento{Ev: EvAparelhos, Tipo: s.nome, Aparelhos: lista})
		}
		return nil

	case CmdMonitores:

		go func() {
			defer PrenderNaThread()()

			responder := func(lista []MonitorDaTela, err error) {
				if err != nil {
					a.saida.Manda(Evento{Ev: EvErro, Msg: "listar monitores: " + err.Error()})
				}
				a.saida.Manda(Evento{Ev: EvMonitores, Monitores: lista})
			}

			if err := abrirCOM(); err != nil {
				responder(nil, err)
				return
			}
			defer fecharCOM()
			responder(ListarMonitores())
		}()
		return nil

	case CmdAssistir:

		quem := cmd.Par
		a.palco.Store(&quem)
		return nil

	case CmdUsarAparelho:
		sentido := sentidoSaida
		if cmd.Sentido == "entrada" {
			sentido = sentidoEntrada
		}
		a.motor.DefinirAparelho(sentido, cmd.Id)
		return nil

	default:
		return fmt.Errorf("comando desconhecido: %q", cmd.Cmd)
	}
}

func (a *App) aplicarConfig(cmd Comando) {
	servidores := make([]webrtc.ICEServer, 0, len(cmd.Stun)+len(cmd.Turn))
	if len(cmd.Stun) > 0 {
		servidores = append(servidores, webrtc.ICEServer{URLs: cmd.Stun})
	}
	for _, t := range cmd.Turn {
		servidores = append(servidores, webrtc.ICEServer{
			URLs:       []string{t.URL},
			Username:   t.User,
			Credential: t.Senha,
		})
	}
	if len(servidores) == 0 {
		return
	}
	a.mu.Lock()
	a.config = webrtc.Configuration{ICEServers: servidores}
	a.mu.Unlock()
}

func (a *App) abrirPar(id string) (*Par, error) {
	if id == "" {
		return nil, errors.New("par sem id")
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	if par, ok := a.pares[id]; ok {
		return par, nil
	}
	par, err := NovoPar(id, a.config, a.faixa, a.tela, a.misturador, a.entrega, a.saida)
	if err != nil {
		return nil, err
	}

	par.pedirQuadroChave = a.emissor.PedirQuadroChave

	par.relatarPerda = func(fracao float64) { a.emissor.PerdaRelatada(id, fracao) }

	par.querVer = func() bool { return a.assistindo(id) }
	a.pares[id] = par
	return par, nil
}

func (a *App) fecharPar(id string) {
	a.mu.Lock()
	par, ok := a.pares[id]
	delete(a.pares, id)
	a.mu.Unlock()
	if ok {
		par.Fechar()
	}

	a.emissor.EsquecerPar(id)
}

func (a *App) aplicarMudo(ligado bool) {

	a.motor.DefinirMudo(ligado)
}

func (a *App) Fechar() {

	a.emissor.Desligar()
	a.entrega.Fechar()

	a.mu.Lock()
	pares := make([]*Par, 0, len(a.pares))
	for _, p := range a.pares {
		pares = append(pares, p)
	}
	a.pares = make(map[string]*Par)
	a.mu.Unlock()

	for _, p := range pares {
		p.Fechar()
	}
}

type Escritor struct {
	mu  sync.Mutex
	enc *json.Encoder
}

func NewEscritor(w io.Writer) *Escritor {
	return &Escritor{enc: json.NewEncoder(w)}
}

func (e *Escritor) Manda(ev Evento) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if err := e.enc.Encode(ev); err != nil {

		fmt.Fprintf(os.Stderr, "ponte fechou na escrita: %v\n", err)
	}
}
