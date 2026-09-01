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
)

var marcaDeOrdem = []byte{0xEF, 0xBB, 0xBF}

func main() {

	ctx, parar := signal.NotifyContext(context.Background(), os.Interrupt)
	defer parar()

	escritor := NewEscritor(os.Stdout)
	misturador := NovoMisturador()
	entrega := NovaEntrega()

	sala := NovaSala(escritor, misturador, entrega)

	dllOpus := os.Getenv("ASTRA_OPUS_DLL")
	if dllOpus == "" {
		dllOpus = "opus-0.dll"
	}

	motor := NovoMotor(sala.FaixaDeVoz(), misturador, escritor, dllOpus)
	if err := motor.Ligar(ctx); err != nil {

		fmt.Fprintf(os.Stderr, "ligar o motor de áudio: %v\n", err)
		escritor.Manda(Evento{Ev: EvErro, Msg: "áudio indisponível: " + err.Error()})
		os.Exit(1)
	}

	emissor := NovoEmissor(sala, escritor, entrega)
	sala.emissor = emissor

	app := &App{
		saida:      escritor,
		sala:       sala,
		emissor:    emissor,
		entrega:    entrega,
		misturador: misturador,
		motor:      motor,
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
	sala       *Sala
	emissor    *Emissor
	entrega    *EntregaDeQuadros
	misturador *Misturador
	motor      *Motor
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
	case CmdEntrarNaSala:
		if err := a.sala.Entrar(cmd.Url, cmd.Token); err != nil {
			return fmt.Errorf("entrar na sala: %w", err)
		}
		return nil

	case CmdDeixarSala:
		a.emissor.Desligar()
		a.sala.Sair()
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
			a.sala.PararTela()
			return nil
		}

		if err := a.sala.PublicarTela(cmd.Largura, cmd.Altura); err != nil {
			return fmt.Errorf("publicar a tela: %w", err)
		}

		a.emissor.Ligar(AjustesDaTela{
			Monitor: cmd.Monitor,
			Janela:  cmd.Janela,
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

	case CmdJanelas:
		go func() {
			defer PrenderNaThread()()

			if err := abrirCOM(); err != nil {
				a.saida.Manda(Evento{Ev: EvErro, Msg: "listar janelas: " + err.Error()})
				a.saida.Manda(Evento{Ev: EvJanelas})
				return
			}
			defer fecharCOM()

			lista, err := ListarJanelas()
			if err != nil {
				a.saida.Manda(Evento{Ev: EvErro, Msg: "listar janelas: " + err.Error()})
			}
			a.saida.Manda(Evento{Ev: EvJanelas, Janelas: lista})
		}()
		return nil

	case CmdAssistir:
		a.sala.Assistir(cmd.Par)
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

func (a *App) aplicarMudo(ligado bool) {

	a.motor.DefinirMudo(ligado)
}

func (a *App) Fechar() {

	a.emissor.Desligar()
	a.entrega.Fechar()
	a.sala.Sair()
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
