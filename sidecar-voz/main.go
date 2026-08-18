// astra-voz — o processo de voz do Astra.
//
// POR QUE ISTO É UM PROCESSO À PARTE, e não uma biblioteca dentro do app:
//
// A voz do Astra vivia dentro da JVM, falando com objetos nativos por JNI. Objeto
// nativo liberado enquanto outra thread ainda o usa não lança exceção em Kotlin —
// derruba o processo inteiro. Na prática: abrir uma call podia fechar o Astra, e
// encerrar uma call também. Um app de conversa que morre ao entrar numa chamada
// está pior do que um que não tem chamada.
//
// Num processo separado, o pior caso de um defeito de mídia vira "a call caiu e
// reconectou". A conversa em texto, os servidores e as janelas abertas continuam
// de pé. Essa é a razão principal de existir este binário — vale mesmo que o
// código aqui dentro tenha os seus próprios bugs, porque bugs vão existir de
// qualquer jeito e o que muda é o tamanho do estrago.
//
// TOPOLOGIA: ponto a ponto (malha). A mídia vai direto de uma pessoa pra outra e
// não passa por servidor nenhum. O servidor do Astra só apresenta os dois e
// carrega os envelopes do aperto de mão. Isso dá a menor latência possível e custo
// zero de servidor de mídia — em troca de a banda de subida crescer com o número
// de pessoas na sala, que é o limite conhecido desta escolha.
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

	"github.com/pion/webrtc/v4"
)

// A MARCA DE ORDEM DE BYTES (BOM) na frente da primeira linha.
//
// Não é paranoia: aconteceu no primeiro teste desta ponte. Vários escritores no
// Windows põem esses três bytes no começo do fluxo, e o `json.Unmarshal` recusa
// com "invalid character 'ï'" — mensagem que não lembra nem de longe a causa.
// Custa uma comparação por linha evitar uma hora de caçada.
var marcaDeOrdem = []byte{0xEF, 0xBB, 0xBF}

func main() {
	// O contexto morre com Ctrl+C e com o fim da entrada padrão. Os dois caminhos
	// levam ao mesmo lugar: soltar microfone e conexões antes de sair.
	ctx, parar := signal.NotifyContext(context.Background(), os.Interrupt)
	defer parar()

	// UMA faixa de microfone para a call inteira — não uma por pessoa.
	//
	// É a otimização mais importante da malha, e ela precisa nascer aqui em cima
	// para todos os pares receberem a MESMA. Ver o comentário longo em NovoPar:
	// o Opus passa a rodar uma vez por quadro em vez de uma vez por companheiro.
	//
	// O áudio é mono (voz não precisa de estéreo, e estéreo dobraria a banda para
	// carregar a mesma informação). A declaração no SDP é outra coisa — ver
	// CapacidadeOpus, que explica por que ela diz dois canais.
	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeOpus, "audio", "astra-microfone")
	if err != nil {
		fmt.Fprintf(os.Stderr, "criar faixa de microfone: %v\n", err)
		os.Exit(1)
	}

	escritor := NewEscritor(os.Stdout)
	misturador := NovoMisturador()

	// A biblioteca Opus vem por caminho, não por busca: o Astra sabe onde ela está
	// (ele empacotou), e procurar em pastas do sistema convidaria a carregar uma
	// versão qualquer que estivesse por aí.
	dllOpus := os.Getenv("ASTRA_OPUS_DLL")
	if dllOpus == "" {
		dllOpus = "opus-0.dll" // ao lado do executável
	}

	motor := NovoMotor(faixa, misturador, escritor, dllOpus)
	if err := motor.Ligar(ctx); err != nil {
		// Sem áudio não há voz, e continuar de pé fingindo que há seria pior do
		// que sair: o Astra ao menos consegue dizer o que houve.
		fmt.Fprintf(os.Stderr, "ligar o motor de áudio: %v\n", err)
		escritor.Manda(Evento{Ev: EvErro, Msg: "áudio indisponível: " + err.Error()})
		os.Exit(1)
	}

	app := &App{
		saida:      escritor,
		faixa:      faixa,
		misturador: misturador,
		motor:      motor,
		pares:      make(map[string]*Par),
		// Sem TURN configurado, o padrão é o STUN público do Google: ele resolve a
		// maioria das redes domésticas. As que ficam de fora são as de NAT
		// simétrico, e para essas só um TURN resolve — o Astra manda a lista dele
		// no comando `config`, e aí este padrão é substituído.
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

// App guarda o estado do processo: a configuração de rede e um par por pessoa na
// call. Tudo que mexe no mapa passa pelo mutex — os eventos do Pion chegam nas
// goroutines dele, não na que lê a entrada padrão.
type App struct {
	saida      *Escritor
	faixa      *webrtc.TrackLocalStaticSample
	misturador *Misturador
	motor      *Motor
	config     webrtc.Configuration

	mu    sync.Mutex
	pares map[string]*Par
}

// Servir lê a ponte linha a linha até a entrada fechar.
//
// Um comando por vez, na mesma goroutine, de propósito: os comandos são raros
// (entrar, sair, mudo) e serializá-los elimina de graça uma classe inteira de
// corrida. O trabalho pesado — mídia, ICE, codificação — acontece nas goroutines
// do Pion e do áudio, não aqui.
func (a *App) Servir(ctx context.Context, entrada io.Reader) error {
	linhas := bufio.NewScanner(entrada)
	// SDP passa fácil dos 64 KB do buffer padrão do Scanner, e estourar isso
	// mataria a ponte no meio de um aperto de mão.
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
			// Linha ruim não derruba o processo: reporta e segue. A ponte é o
			// único jeito de o Astra falar com a voz, e fechá-la por causa de um
			// JSON torto tiraria a call inteira do ar por um erro de digitação.
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
		// Só um dos dois lados oferece. Quem oferece é decidido pelo Astra (regra
		// determinística de quem tem o id menor), e não aqui — assim os dois lados
		// chegam à mesma conclusão sem precisar combinar nada em tempo real.
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
			// Sinal de alguém que já saiu é normal — pacote atrasado de rede. Não
			// é erro, e reportar como erro só ia poluir o diagnóstico.
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

	case CmdAparelhos:
		// Os dois sentidos numa resposta cada. `Tipo` diz de qual é a lista — sem
		// ele o outro lado teria de adivinhar pela ordem de chegada, e ordem não é
		// garantia de nada num canal assíncrono.
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
	par, err := NovoPar(id, a.config, a.faixa, a.misturador, a.saida)
	if err != nil {
		return nil, err
	}
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
}

func (a *App) aplicarMudo(ligado bool) {
	// O mudo é do MICROFONE, não das conexões: silenciar na fonte significa que
	// nem sequer sai pacote, em vez de sair silêncio codificado para cada pessoa
	// da sala. Em malha isso é a diferença entre gastar banda com nada e não
	// gastar, multiplicada pelo número de pessoas.
	a.motor.DefinirMudo(ligado)
}

func (a *App) Fechar() {
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

// Escritor serializa a saída.
//
// Existe porque os eventos nascem em várias goroutines do Pion ao mesmo tempo, e
// duas escritas concorrentes na saída padrão intercalariam bytes no meio de uma
// linha. O outro lado lê linha a linha: meia linha de JSON é um erro de parse que
// só aparece sob carga, que é o pior tipo de bug pra caçar.
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
		// A saída padrão morreu, ou seja, o Astra fechou. Não há pra quem
		// reportar; a saída de erro ainda serve de registro.
		fmt.Fprintf(os.Stderr, "ponte fechou na escrita: %v\n", err)
	}
}
