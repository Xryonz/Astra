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
	"sync/atomic"

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

	// A TELA COMPARTILHADA, e UMA para a sala inteira pela mesma razão do microfone:
	// uma escrita reaproveita o mesmo quadro comprimido para todas as conexões. Aqui a
	// economia pesa muito mais que no áudio — comprimir H.264 é a coisa mais cara que
	// este processo faz, e numa malha de quatro seriam quatro compressores.
	tela, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "astra-tela")
	if err != nil {
		fmt.Fprintf(os.Stderr, "criar faixa de tela: %v\n", err)
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
		tela:       tela,
		emissor:    NovoEmissor(tela, escritor),
		entrega:    NovaEntrega(),
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
	tela       *webrtc.TrackLocalStaticSample
	emissor    *Emissor
	entrega    *EntregaDeQuadros
	misturador *Misturador
	motor      *Motor
	config     webrtc.Configuration

	// QUAL TELA ESTÁ NO PALCO DO ASTRA. Ver `assistindo` logo abaixo para o porquê de
	// ser um ponteiro e não uma string.
	//
	// Atômico, e não protegido pelo mutex de baixo, porque quem lê isto é o laço de
	// rede de cada par — uma leitura por pacote RTP, milhares por segundo. Um mutex
	// aqui poria o comando raro de troca de palco no caminho de todo pacote de vídeo
	// da sala.
	palco atomic.Pointer[string]

	mu    sync.Mutex
	pares map[string]*Par
}

// assistindo responde se vale a pena decodificar a tela desta pessoa.
//
// O PONTEIRO NULO NÃO É "NINGUÉM", É "O ASTRA AINDA NÃO DISSE" — e a diferença é o que
// mantém este processo utilizável sozinho. Rodar `astra-voz.exe` à mão, ou os testes de
// ponta a ponta, nunca manda `assistir`; se nulo valesse "ninguém", a imagem simplesmente
// não abriria e o motivo não estaria em lugar nenhum.
//
// Depois que o Astra fala uma vez, ele manda sempre — inclusive o "ninguém" explícito, que
// é uma string vazia e faz esta função responder não para todo mundo.
func (a *App) assistindo(id string) bool {
	quem := a.palco.Load()
	if quem == nil {
		return true
	}
	// O `*quem != ""` não é redundante com a comparação: `abrirPar` recusa par sem id
	// hoje, mas se um id vazio entrasse aqui ele casaria com o palco vazio e a tela de
	// ninguém passaria a ser decodificada. Uma comparação a mais fecha a porta.
	return *quem != "" && *quem == id
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

	case CmdTratamento:
		a.motor.DefinirTratamento(AjustesDaVoz{Eco: cmd.Eco, Ruido: cmd.Ruido, Ganho: cmd.Ganho})
		return nil

	case CmdTransmitir:
		if !cmd.Ligado {
			a.emissor.Desligar()
			return nil
		}
		// NÃO DEVOLVE ERRO DE ABERTURA, e é de propósito: montar a captura e o
		// compressor leva quase um segundo, e segurar a leitura da ponte por esse
		// tempo pararia a call inteira (é uma goroutine só, um comando por vez, ver
		// `Servir`). O laço sobe sozinho e conta o que houve pelo evento
		// EvTransmissao — que é o mesmo caminho por onde ele conta que parou.
		a.emissor.Ligar(AjustesDaTela{
			Monitor: cmd.Monitor,
			Largura: cmd.Largura,
			Altura:  cmd.Altura,
			Fps:     cmd.Fps,
			Kbps:    cmd.Kbps,
		})
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

	case CmdMonitores:
		// NÃO ESPERA A RESPOSTA, ao contrário do irmão de cima. Listar aparelhos de
		// áudio é uma consulta a um registro e volta em milissegundos; amostrar
		// monitores exige DUPLICAR cada tela, e são uns 100ms por monitor. A ponte é uma
		// goroutine só, um comando por vez (ver `Servir`) — segurá-la meio segundo
		// pararia a chamada inteira, incluindo a voz.
		//
		// A janela de escolha do outro lado já nasce sabendo esperar: ela abre com a
		// lista vazia e preenche quando o evento chega.
		go func() {
			defer PrenderNaThread()()

			// A RESPOSTA SAI SEMPRE, mesmo quando dá errado — e sai VAZIA em vez de não
			// sair. A janela de escolha do outro lado espera este evento para parar de
			// dizer "procurando as telas"; sem ele, uma falha aqui a deixaria procurando
			// para sempre, que é o beco sem saída pior que o erro. O motivo vai junto,
			// pelo caminho de erro, para quem for ler o registro.
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
		// A CÓPIA EXISTE PARA NÃO SEGURAR O COMANDO INTEIRO. Guardar `&cmd.Par` prenderia
		// o `Comando` todo enquanto o palco durar — e ele carrega o campo `Dados`, que é
		// onde cabe um SDP de quase um megabyte.
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
	// O PEDIDO DE QUADRO-CHAVE ATRAVESSA A MALHA INTEIRA, e cai num emissor só.
	//
	// Faz sentido justamente porque a faixa de tela é UMA para a sala: quem pede é uma
	// pessoa, mas o quadro-chave que sai atende todo mundo de uma vez. Numa sala em que
	// três entram juntas, os três pedidos viram um quadro caro em vez de três.
	par.pedirQuadroChave = a.emissor.PedirQuadroChave
	// A PERDA DE CADA UM VAI PARA O MESMO LUGAR, pela mesma razão: um compressor para a
	// sala inteira. Quem manda no ritmo é quem está na pior conexão — ver `banda.go`.
	par.relatarPerda = func(fracao float64) { a.emissor.PerdaRelatada(id, fracao) }
	// "ALGUÉM ESTÁ DE FATO OLHANDO A TELA DESTA PESSOA?" É o que decide se o quadro dela
	// vai ao descompressor ou para o lixo. Ver `recepcao.go`.
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
	// ESQUECER A PERDA DELE JUNTO. Sem isto, quem saiu da sala continuaria segurando a
	// banda de quem ficou — e justamente quem saiu por ter uma conexão ruim é quem
	// deixaria o número mais alto para trás.
	a.emissor.EsquecerPar(id)
}

func (a *App) aplicarMudo(ligado bool) {
	// O mudo é do MICROFONE, não das conexões: silenciar na fonte significa que
	// nem sequer sai pacote, em vez de sair silêncio codificado para cada pessoa
	// da sala. Em malha isso é a diferença entre gastar banda com nada e não
	// gastar, multiplicada pelo número de pessoas.
	a.motor.DefinirMudo(ligado)
}

func (a *App) Fechar() {
	// A TRANSMISSÃO PRIMEIRO, e a ordem importa: o laço dela segura a duplicação da
	// tela e um compressor do Media Foundation, e `Desligar` espera os dois voltarem.
	// Fechar as conexões antes deixaria o laço escrevendo numa faixa já solta.
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
