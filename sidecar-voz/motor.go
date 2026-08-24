package main

// O MOTOR DE ÁUDIO — os dois laços que fazem o som circular.
//
// São duas goroutines, cada uma presa à própria thread do sistema, e a separação
// não é enfeite: captura e reprodução são guiadas por relógios DIFERENTES. O
// microfone entrega quando o aparelho quer; o alto-falante pede quando está com
// fome. Amarrar os dois no mesmo laço faria um esperar pelo outro, e a espera de um
// viraria falha de áudio do outro.
//
// Entre eles não há chamada direta, só o misturador — que é o único ponto onde os
// dois relógios se encontram, e é por isso que ele carrega o mutex.

import (
	"context"
	// `errors.Is` e não `==` nas comparações com ErrSemAudio.
	//
	// Hoje as duas formas dão o mesmo resultado — o erro nunca é embrulhado no caminho
	// até aqui. Mas é exatamente a família do defeito que custou caro nesta sessão (ver
	// `esperaEstourada` em par.go): comparação de erro que passa a mentir no dia em que
	// alguém acrescenta um `%w` no meio, sem falhar em compilação nem em teste. E o
	// silêncio seria pior aqui do que lá: "não há áudio agora" é o caso COMUM deste laço,
	// então confundi-lo com falha de verdade derrubaria a captura a cada bloco vazio.
	"errors"
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

// CapacidadeOpus é como a faixa se anuncia no SDP.
//
// `Channels: 2` mesmo o áudio sendo MONO, e isso não é engano: em WebRTC o Opus é
// sempre negociado como `opus/48000/2`, e é assim que o Pion o registra por padrão.
// A contagem no SDP é formalidade de negociação — quantos canais o áudio realmente
// tem viaja dentro do próprio fluxo Opus, e mono é perfeitamente válido ali.
//
// Declarar `Channels: 1` aqui parece mais honesto e QUEBRA a chamada: a faixa deixa
// de casar com o codec registrado, e o outro lado recusa com "codec não suportado".
// Foi assim que o teste de chamada completa falhou duas vezes.
var CapacidadeOpus = webrtc.RTPCodecCapability{
	MimeType:  webrtc.MimeTypeOpus,
	ClockRate: TaxaDeAmostragem,
	Channels:  2,
}

// Motor liga o microfone à rede e a rede ao alto-falante.
type Motor struct {
	faixa      *webrtc.TrackLocalStaticSample
	misturador *Misturador
	saida      *Escritor

	// Atômicos porque os comandos chegam pela ponte, noutra goroutine, no meio dos
	// laços de áudio. Um mutex aqui seria travar o caminho do som 50 vezes por
	// segundo para ler um booleano.
	mudo  atomic.Bool
	surdo atomic.Bool

	// Qual aparelho usar em cada sentido. Vazio = o de comunicação padrão do
	// Windows. A GERAÇÃO ao lado é o que avisa o laço de que a escolha mudou: o laço
	// a compara a cada volta e, quando difere, fecha o aparelho e abre o novo.
	//
	// Contador em vez de "mudou?" booleano porque duas trocas rápidas seguidas
	// perderiam a segunda — o laço zeraria a bandeira depois de atender a primeira e
	// nunca saberia da outra.
	aparelhoEntrada atomic.Value
	aparelhoSaida   atomic.Value
	geracaoEntrada  atomic.Uint64
	geracaoSaida    atomic.Uint64

	// Passar o microfone pelo cancelador de eco do Windows. LIGADO por padrão: quem
	// usa caixas de som em vez de fone devolve o áudio de todo mundo pelo próprio
	// microfone, e essa pessoa é a última a perceber — quem sofre são os outros.
	// Deixar desligado por padrão seria escolher o defeito.
	cancelarEco atomic.Bool

	// Supressão de ruído e ganho automático. Moram DENTRO do cancelador (é o mesmo
	// objeto do Windows), então valem só quando ele está no caminho — ver
	// AbrirEntradaDeVoz. Guardados aqui do mesmo jeito que o eco porque mudam pela
	// ponte, em plena call, e o laço de captura os relê ao reabrir a fonte.
	suprimirRuido atomic.Bool
	ganhoAuto     atomic.Bool

	// O CANCELADOR JÁ FOI TENTADO E NÃO PRODUZIU NESTA MÁQUINA.
	//
	// Separado da escolha da pessoa de propósito: `cancelarEco` é o que ela quer,
	// isto é o que a máquina consegue. Sobrescrever a escolha dela faria o interruptor
	// mentir — ele diria "ligado" para sempre enquanto nada acontece.
	ecoReprovado atomic.Bool

	// Fecha quando o alto-falante abre.
	//
	// O CANCELADOR DE ECO EXIGE UM FLUXO DE SAÍDA ATIVO, e isso não é suposição:
	// medido. Sem saída aberta ele responde S_FALSE indefinidamente e não entrega uma
	// amostra sequer; com ela, entrega 98% do tempo real. Faz sentido — é um
	// cancelador de eco, e sem referência não há o que cancelar contra.
	//
	// Como as duas goroutines sobem juntas, sem este sinal a captura podia abrir o
	// cancelador antes de existir saída, e ele nasceria mudo.
	saidaPronta chan struct{}
	avisarSaida sync.Once

	// Caminho da biblioteca Opus, resolvido uma vez na abertura.
	dllOpus string
}

func NovoMotor(faixa *webrtc.TrackLocalStaticSample, mist *Misturador, saida *Escritor, dllOpus string) *Motor {
	m := &Motor{
		faixa:       faixa,
		misturador:  mist,
		saida:       saida,
		dllOpus:     dllOpus,
		saidaPronta: make(chan struct{}),
	}
	m.cancelarEco.Store(true)
	// Os padrões do Astra, e eles NÃO são os padrões do objeto do Windows: lá a
	// supressão de ruído já vem ligada mas o ganho automático vem desligado. Quem
	// manda é a tela, então os dois começam como a tela mostra.
	m.suprimirRuido.Store(true)
	m.ganhoAuto.Store(true)
	return m
}

// DefinirTratamento troca os três ajustes do microfone em plena call.
//
// Reaproveita a geração da entrada porque o efeito é o mesmo de trocar de aparelho: o
// laço fecha a fonte atual e abre outra. Um contador separado só para isto seria
// duplicar mecanismo idêntico.
//
// OS TRÊS DE UMA VEZ e não um por chamada: as três são propriedades escritas na
// ABERTURA do cancelador, então cada mudança custa uma reabertura — alguns quadros de
// silêncio. Aplicar em bloco cobra esse preço UMA vez.
//
// AVALIA OS TRÊS ANTES DE DECIDIR, e a ordem importa: `Swap` já escreveu o valor novo
// quando devolve o antigo, então interromper no primeiro que não mudou deixaria os
// outros dois por escrever. Foi por isso que os três `Swap` acontecem incondicionalmente
// e só a REABERTURA é condicional — reabrir à toa corta o som sem nada em troca.
func (m *Motor) DefinirTratamento(aj AjustesDaVoz) {
	mudouEco := m.cancelarEco.Swap(aj.Eco) != aj.Eco
	mudouRuido := m.suprimirRuido.Swap(aj.Ruido) != aj.Ruido
	mudouGanho := m.ganhoAuto.Swap(aj.Ganho) != aj.Ganho
	if mudouEco || mudouRuido || mudouGanho {
		m.geracaoEntrada.Add(1)
	}
}

func (m *Motor) DefinirMudo(on bool) { m.mudo.Store(on) }

// DefinirSurdo é "não quero ouvir ninguém". Quem ensurdece também fica mudo, mas
// isso é decisão do Astra — aqui são dois comandos independentes, e o motor não
// tem opinião sobre a política.
func (m *Motor) DefinirSurdo(on bool) { m.surdo.Store(on) }

// DefinirAparelho troca o microfone ou o alto-falante EM PLENA CALL.
//
// Não interrompe a chamada: só o laço daquele sentido fecha o aparelho e abre o
// outro, o que custa alguns quadros de silêncio. As conexões continuam de pé, e é
// por isso que a troca vive aqui e não no nível da sala.
func (m *Motor) DefinirAparelho(sentido int, id string) {
	if sentido == sentidoEntrada {
		m.aparelhoEntrada.Store(id)
		m.geracaoEntrada.Add(1)
		return
	}
	m.aparelhoSaida.Store(id)
	m.geracaoSaida.Add(1)
}

func (m *Motor) idEntrada() string {
	s, _ := m.aparelhoEntrada.Load().(string)
	return s
}

func (m *Motor) idSaida() string {
	s, _ := m.aparelhoSaida.Load().(string)
	return s
}

// Ligar sobe as duas goroutines. Elas morrem com o contexto.
func (m *Motor) Ligar(ctx context.Context) error {
	if err := AbrirOpus(m.dllOpus); err != nil {
		return fmt.Errorf("abrir codec de voz: %w", err)
	}
	go m.laçoDeCaptura(ctx)
	go m.laçoDeSaida(ctx)
	return nil
}

// laçoDeCaptura: microfone -> Opus -> rede.
//
// DOIS LAÇOS, e o de fora existe por causa da troca de aparelho: quando a pessoa
// escolhe outro microfone, o de dentro sai, o aparelho é fechado, e o de fora abre o
// novo. As conexões nem ficam sabendo.
func (m *Motor) laçoDeCaptura(ctx context.Context) {
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		m.reclamar("iniciar COM na captura", err)
		return
	}
	defer fecharCOM()

	for ctx.Err() == nil {
		geracao := m.geracaoEntrada.Load()

		// ESPERA A SAÍDA ABRIR antes de montar o cancelador — ele nasce mudo sem
		// fluxo de saída ativo. Prazo curto: se a saída não abrir (máquina sem
		// alto-falante), seguimos sem cancelador em vez de ficar sem microfone.
		querEco := m.cancelarEco.Load() && !m.ecoReprovado.Load()
		if querEco && !m.esperarSaida(ctx, 3*time.Second) {
			fmt.Fprintln(os.Stderr, "alto-falante não abriu a tempo; seguindo sem cancelador de eco")
			querEco = false
		}

		fonte, err := AbrirEntradaDeVoz(m.idEntrada(), AjustesDaVoz{
			Eco:   querEco,
			Ruido: m.suprimirRuido.Load(),
			Ganho: m.ganhoAuto.Load(),
		})
		if err != nil {
			// TENTAR DE NOVO em vez de morrer. Antes, um microfone indisponível
			// matava a captura para o resto da call — e microfone indisponível é
			// coisa que passa: outro programa soltou o aparelho, o USB voltou.
			m.reclamar("abrir microfone", err)
			if !esperar(ctx, 2*time.Second) {
				return
			}
			continue
		}

		// O CODIFICADOR NASCE COM A FONTE, e não antes dela.
		//
		// Ele era criado uma vez, fora do laço, quando toda fonte entregava 48 kHz.
		// Com o cancelador de eco isso deixou de valer: ele entrega 16 kHz, e um
		// codificador aberto na taxa errada não dá erro — produz voz acelerada ou
		// arrastada, que soa como defeito de rede e manda procurar no lugar errado.
		//
		// Recriar por troca de aparelho é barato: acontece quando alguém mexe no
		// seletor, não a cada quadro.
		cod, err := NovoCodificador(fonte.Taxa(), CanaisDeVoz)
		if err != nil {
			m.reclamar("criar codificador", err)
			fonte.Fechar()
			return
		}

		ficouMuda := m.bombearMicrofone(ctx, fonte, cod, geracao)
		cod.Fechar()
		fonte.Fechar()

		// CANCELADOR QUE NÃO PRODUZ É PIOR QUE CANCELADOR NENHUM: a call fica sem
		// microfone e a pessoa só descobre quando alguém diz "não te ouço".
		//
		// Sala em silêncio NÃO cai aqui — o cancelador entrega amostras de silêncio,
		// não ausência de amostras. Zero amostras por segundos é máquina em que ele
		// não engatou, e aí a resposta certa é voz com eco em vez de voz nenhuma.
		if ficouMuda && querEco {
			m.ecoReprovado.Store(true)
			m.reclamar("cancelador de eco",
				fmt.Errorf("não entregou áudio nesta máquina; seguindo sem ele"))
		}
	}
}

// esperarSaida bloqueia até o alto-falante abrir, ou até o prazo acabar.
func (m *Motor) esperarSaida(ctx context.Context, prazo time.Duration) bool {
	t := time.NewTimer(prazo)
	defer t.Stop()
	select {
	case <-m.saidaPronta:
		return true
	case <-ctx.Done():
		return false
	case <-t.C:
		return false
	}
}

// esperar dorme, ou devolve false se o contexto morrer antes.
func esperar(ctx context.Context, d time.Duration) bool {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-t.C:
		return true
	}
}

// bombearMicrofone lê desta fonte até o contexto morrer ou a escolha de aparelho
// mudar.
//
// Devolve `true` quando a fonte ficou MUDA — nenhuma amostra por tempo demais. Quem
// chama usa isso para desistir do cancelador de eco e voltar ao microfone cru.
func (m *Motor) bombearMicrofone(ctx context.Context, mic FonteDeAudio, cod *Codificador, geracao uint64) bool {
	// O QUADRO SAI DA FONTE, e não da constante global.
	//
	// Vinte milissegundos são vinte milissegundos, mas quantas AMOSTRAS isso são
	// depende da taxa: 960 na captura crua de 48 kHz, 320 no cancelador de eco de
	// 16 kHz. Usar a constante global aqui mandaria 960 amostras ao Opus quando só
	// existiam 320 — e o codificador aceitaria, produzindo voz arrastada.
	porQuadro := mic.Taxa() * MilissegundosPorQuadro / 1000

	// A fonte entrega blocos de tamanho variável; o Opus exige exatamente um quadro.
	// Este acumulador é a ponte entre os dois, e é por isso que ele existe em vez de
	// codificar direto o que chegou.
	acumulado := make([]int16, 0, porQuadro*4)
	bloco := make([]int16, porQuadro*4)
	pacote := make([]byte, 4000)

	// O meu próprio indicador de fala. Sai daqui e não da rede, pela razão óbvia de
	// que a minha voz nunca dá a volta pela rede — e pela razão menos óbvia de que
	// esperar ela voltar mostraria o meu círculo com o atraso da internet.
	var det DetectorDeFala
	// Trocar de microfone com o círculo aceso o deixaria aceso durante a troca
	// inteira: quem sai apaga o que acendeu.
	defer func() {
		if det.Calar() {
			m.saida.Manda(Evento{Ev: EvFala, V: marcaDeFala(false)})
		}
	}()

	// VIGIA DA FONTE MUDA.
	//
	// Uma fonte que abre sem erro e nunca entrega nada é o pior estado possível: a
	// call parece funcionando e a pessoa só descobre quando alguém diz "não te ouço".
	// Foi exatamente assim que o cancelador de eco se comportou numa máquina sem
	// fluxo de saída ativo — S_FALSE para sempre, sem um único erro.
	//
	// Sala em silêncio NÃO dispara isto: microfone entrega amostras de silêncio, não
	// ausência de amostras.
	const paciencia = 2 * time.Second
	ultimaAmostra := time.Now()

	for {
		select {
		case <-ctx.Done():
			return false
		default:
		}
		if m.geracaoEntrada.Load() != geracao {
			return false
		}

		if err := mic.Esperar(200); err != nil {
			if !errors.Is(err, ErrSemAudio) {
				m.reclamar("esperar pelo microfone", err)
				return false
			}
			// Sem material agora: só o vigia decide se isso já durou demais.
			if time.Since(ultimaAmostra) > paciencia {
				return true
			}
			continue
		}

		for {
			n, _, err := mic.Ler(bloco)
			if errors.Is(err, ErrSemAudio) {
				break
			}
			if err != nil {
				m.reclamar("ler microfone", err)
				return false
			}
			acumulado = append(acumulado, bloco[:n]...)
			if n > 0 {
				ultimaAmostra = time.Now()
			}
		}

		if time.Since(ultimaAmostra) > paciencia {
			return true
		}

		for len(acumulado) >= porQuadro {
			quadro := acumulado[:porQuadro]

			// MUDO CORTA NA FONTE. Não é "codificar silêncio e mandar": é não
			// mandar nada. Em malha, mandar silêncio codificado para N pessoas é
			// gastar banda com nada, N vezes.
			//
			// O acumulador continua sendo consumido para não crescer sem fim
			// enquanto a pessoa está muda.
			mudo := m.mudo.Load()

			// MUDO É SILÊNCIO PARA O DETECTOR, e não "não medir".
			//
			// Alimentar com o quadro de verdade acenderia o meu círculo enquanto
			// estou mudo — eu me veria falando enquanto ninguém me ouve, que é
			// exatamente o engano que o indicador existe para evitar.
			var paraODetector []int16
			if !mudo {
				paraODetector = quadro
			}
			if det.Alimentar(paraODetector, time.Now()) {
				m.saida.Manda(Evento{Ev: EvFala, V: marcaDeFala(det.Falando())})
			}

			if !mudo {
				bytes, err := cod.Codificar(quadro, pacote)
				if err != nil {
					m.reclamar("codificar voz", err)
					return false
				}
				amostra := media.Sample{
					Data:     pacote[:bytes],
					Duration: MilissegundosPorQuadro * time.Millisecond,
				}
				// UMA escrita, e o Pion espalha para todos os pares. É aqui que a
				// otimização da faixa compartilhada se paga.
				if err := m.faixa.WriteSample(amostra); err != nil {
					m.reclamar("enviar voz", err)
					return false
				}
			}

			acumulado = acumulado[porQuadro:]
		}

		// Devolve a capacidade ao começo da fatia quando ela esvazia, para o
		// `append` não realocar a cada volta.
		if len(acumulado) == 0 {
			acumulado = acumulado[:0]
		}
	}
}

// laçoDeSaida: rede -> mistura -> alto-falante.
//
// Mesma estrutura de dois laços da captura, e pelo mesmo motivo: trocar de saída em
// plena call fecha só este aparelho.
func (m *Motor) laçoDeSaida(ctx context.Context) {
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		m.reclamar("iniciar COM na saída", err)
		return
	}
	defer fecharCOM()

	for ctx.Err() == nil {
		geracao := m.geracaoSaida.Load()
		alto, err := AbrirSaida(m.idSaida())
		if err != nil {
			m.reclamar("abrir alto-falante", err)
			if !esperar(ctx, 2*time.Second) {
				return
			}
			continue
		}
		// AVISA A CAPTURA que já existe fluxo de saída. O cancelador de eco depende
		// disso para engatar — sem referência, ele não produz nada. Uma vez só: o
		// que importa é que a saída já EXISTIU, e as reaberturas por troca de
		// aparelho são curtas demais para o cancelador notar.
		m.avisarSaida.Do(func() { close(m.saidaPronta) })

		m.bombearSaida(ctx, alto, geracao)
		alto.Fechar()
	}
}

// bombearSaida toca nesta saída até o contexto morrer ou a escolha mudar.
func (m *Motor) bombearSaida(ctx context.Context, alto *Saida, geracao uint64) {
	quadro := make([]int16, AmostrasPorQuadro)

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		if m.geracaoSaida.Load() != geracao {
			return
		}

		if err := alto.Esperar(200); err != nil {
			if errors.Is(err, ErrSemAudio) {
				continue
			}
			m.reclamar("esperar pela saída", err)
			return
		}

		// Enche TODO o espaço livre, não um quadro só. Se a máquina engasgou e o
		// buffer esvaziou, entregar um quadro apenas deixaria ele quase vazio de
		// novo, e a falha se repetiria no ciclo seguinte.
		livre, err := alto.EspacoLivre()
		if err != nil {
			m.reclamar("consultar a saída", err)
			return
		}
		for livre >= AmostrasPorQuadro {
			vozes := m.misturador.Puxar(quadro)

			// ENSURDECIDO CONTINUA PUXANDO, e joga fora.
			//
			// Parar de puxar deixaria as filas de todo mundo cheias, e ao voltar a
			// ouvir a pessoa receberia de uma vez o que ficou represado — um jato de
			// conversa velha. Puxar e descartar mantém a call andando no presente,
			// que é onde ela tem de estar quando o ouvido voltar.
			bloco := quadro
			if vozes == 0 || m.surdo.Load() {
				// Silêncio se avisa, não se escreve: uma cópia de buffer a menos, e
				// é o estado mais comum numa call.
				bloco = nil
			}
			if err := alto.Escrever(bloco); err != nil {
				m.reclamar("tocar voz", err)
				return
			}

			// Sem ninguém falando não há nada represado para drenar; girar mais só
			// encheria a saída de silêncio adiantado.
			if vozes == 0 {
				break
			}
			livre -= AmostrasPorQuadro
		}
	}
}

// reclamar manda o erro para o Astra e para o registro.
//
// Falha de áudio NÃO derruba o processo: as conexões continuam de pé, e o Astra
// pode decidir avisar a pessoa e tentar de novo. Sair aqui levaria a call inteira
// junto por causa de um fone desconectado.
func (m *Motor) reclamar(oQueFazia string, err error) {
	msg := fmt.Sprintf("%s: %v", oQueFazia, err)
	fmt.Fprintln(os.Stderr, msg)
	m.saida.Manda(Evento{Ev: EvErro, Msg: msg})
}
