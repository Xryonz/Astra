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
	"fmt"
	"os"
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

	// Caminho da biblioteca Opus, resolvido uma vez na abertura.
	dllOpus string
}

func NovoMotor(faixa *webrtc.TrackLocalStaticSample, mist *Misturador, saida *Escritor, dllOpus string) *Motor {
	return &Motor{faixa: faixa, misturador: mist, saida: saida, dllOpus: dllOpus}
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

	// O codificador NÃO depende do aparelho: pedimos sempre 48 kHz mono e o Windows
	// reamostra. Por isso ele nasce fora do laço e sobrevive às trocas — recriá-lo a
	// cada troca jogaria fora o estado que o Opus usa para emendar os quadros.
	cod, err := NovoCodificador(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		m.reclamar("criar codificador", err)
		return
	}
	defer cod.Fechar()

	for ctx.Err() == nil {
		geracao := m.geracaoEntrada.Load()
		mic, err := AbrirCaptura(m.idEntrada())
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
		m.bombearMicrofone(ctx, mic, cod, geracao)
		mic.Fechar()
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

// bombearMicrofone lê deste microfone até o contexto morrer ou a escolha de
// aparelho mudar.
func (m *Motor) bombearMicrofone(ctx context.Context, mic *Captura, cod *Codificador, geracao uint64) {
	// O microfone entrega blocos de tamanho variável; o Opus exige exatamente 20ms.
	// Este acumulador é a ponte entre os dois, e é por isso que ele existe em vez de
	// codificar direto o que o WASAPI devolveu.
	acumulado := make([]int16, 0, AmostrasPorQuadro*4)
	bloco := make([]int16, AmostrasPorQuadro*4)
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

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		if m.geracaoEntrada.Load() != geracao {
			return
		}

		if err := mic.Esperar(200); err != nil {
			if err == ErrSemAudio {
				continue
			}
			m.reclamar("esperar pelo microfone", err)
			return
		}

		for {
			n, _, err := mic.Ler(bloco)
			if err == ErrSemAudio {
				break
			}
			if err != nil {
				m.reclamar("ler microfone", err)
				return
			}
			acumulado = append(acumulado, bloco[:n]...)
		}

		for len(acumulado) >= AmostrasPorQuadro {
			quadro := acumulado[:AmostrasPorQuadro]

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
					return
				}
				amostra := media.Sample{
					Data:     pacote[:bytes],
					Duration: MilissegundosPorQuadro * time.Millisecond,
				}
				// UMA escrita, e o Pion espalha para todos os pares. É aqui que a
				// otimização da faixa compartilhada se paga.
				if err := m.faixa.WriteSample(amostra); err != nil {
					m.reclamar("enviar voz", err)
					return
				}
			}

			acumulado = acumulado[AmostrasPorQuadro:]
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
			if err == ErrSemAudio {
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
