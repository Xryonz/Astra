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

	// Atômico porque o comando de mudo chega pela ponte, noutra goroutine, no meio
	// do laço de captura. Um mutex aqui seria travar o caminho do áudio 50 vezes
	// por segundo para ler um booleano.
	mudo atomic.Bool

	// Caminho da biblioteca Opus, resolvido uma vez na abertura.
	dllOpus string
}

func NovoMotor(faixa *webrtc.TrackLocalStaticSample, mist *Misturador, saida *Escritor, dllOpus string) *Motor {
	return &Motor{faixa: faixa, misturador: mist, saida: saida, dllOpus: dllOpus}
}

func (m *Motor) DefinirMudo(on bool) { m.mudo.Store(on) }

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
func (m *Motor) laçoDeCaptura(ctx context.Context) {
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		m.reclamar("iniciar COM na captura", err)
		return
	}
	defer fecharCOM()

	mic, err := AbrirCaptura()
	if err != nil {
		m.reclamar("abrir microfone", err)
		return
	}
	defer mic.Fechar()

	cod, err := NovoCodificador(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		m.reclamar("criar codificador", err)
		return
	}
	defer cod.Fechar()

	// O microfone entrega blocos de tamanho variável; o Opus exige exatamente 20ms.
	// Este acumulador é a ponte entre os dois, e é por isso que ele existe em vez de
	// codificar direto o que o WASAPI devolveu.
	acumulado := make([]int16, 0, AmostrasPorQuadro*4)
	bloco := make([]int16, AmostrasPorQuadro*4)
	pacote := make([]byte, 4000)

	for {
		select {
		case <-ctx.Done():
			return
		default:
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
			if !m.mudo.Load() {
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
func (m *Motor) laçoDeSaida(ctx context.Context) {
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		m.reclamar("iniciar COM na saída", err)
		return
	}
	defer fecharCOM()

	alto, err := AbrirSaida()
	if err != nil {
		m.reclamar("abrir alto-falante", err)
		return
	}
	defer alto.Fechar()

	quadro := make([]int16, AmostrasPorQuadro)

	for {
		select {
		case <-ctx.Done():
			return
		default:
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
			if vozes == 0 {
				// Ninguém falando: avisa silêncio em vez de escrever zeros. Uma
				// cópia de buffer a menos, e é o estado mais comum numa call.
				if err := alto.Escrever(nil); err != nil {
					m.reclamar("tocar silêncio", err)
					return
				}
				break
			}
			if err := alto.Escrever(quadro); err != nil {
				m.reclamar("tocar voz", err)
				return
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
