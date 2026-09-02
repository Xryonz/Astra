package main

import (
	"context"

	"errors"
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

var CapacidadeOpus = webrtc.RTPCodecCapability{
	MimeType:  webrtc.MimeTypeOpus,
	ClockRate: TaxaDeAmostragem,
	Channels:  2,
}

type FaixaDeVoz interface {
	WriteSample(media.Sample) error
}

type Motor struct {
	faixa      FaixaDeVoz
	misturador *Misturador
	saida      *Escritor

	mudo  atomic.Bool
	surdo atomic.Bool

	aparelhoEntrada atomic.Value
	aparelhoSaida   atomic.Value
	geracaoEntrada  atomic.Uint64
	geracaoSaida    atomic.Uint64

	cancelarEco atomic.Bool

	suprimirRuido atomic.Bool
	ganhoAuto     atomic.Bool

	ecoReprovado atomic.Bool

	volumeDoMic atomic.Int32

	saidaPronta chan struct{}
	avisarSaida sync.Once

	dllOpus string
}

func NovoMotor(faixa FaixaDeVoz, mist *Misturador, saida *Escritor, dllOpus string) *Motor {
	m := &Motor{
		faixa:       faixa,
		misturador:  mist,
		saida:       saida,
		dllOpus:     dllOpus,
		saidaPronta: make(chan struct{}),
	}
	m.cancelarEco.Store(true)

	m.suprimirRuido.Store(true)
	m.ganhoAuto.Store(true)
	m.volumeDoMic.Store(ganhoInteiro)
	return m
}

func (m *Motor) DefinirVolumeDoMicrofone(porcento int) {
	m.volumeDoMic.Store(int32(min(max(porcento, 0), 100)) * ganhoInteiro / 100)
}

func (m *Motor) DefinirTratamento(aj AjustesDaVoz) {
	mudouEco := m.cancelarEco.Swap(aj.Eco) != aj.Eco
	mudouRuido := m.suprimirRuido.Swap(aj.Ruido) != aj.Ruido
	mudouGanho := m.ganhoAuto.Swap(aj.Ganho) != aj.Ganho
	if mudouEco || mudouRuido || mudouGanho {
		m.geracaoEntrada.Add(1)
	}
}

func (m *Motor) DefinirMudo(on bool) { m.mudo.Store(on) }

func (m *Motor) DefinirSurdo(on bool) { m.surdo.Store(on) }

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

func (m *Motor) Ligar(ctx context.Context) error {
	if err := AbrirOpus(m.dllOpus); err != nil {
		return fmt.Errorf("abrir codec de voz: %w", err)
	}
	go m.laçoDeCaptura(ctx)
	go m.laçoDeSaida(ctx)
	return nil
}

func (m *Motor) laçoDeCaptura(ctx context.Context) {
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		m.reclamar("iniciar COM na captura", err)
		return
	}
	defer fecharCOM()

	for ctx.Err() == nil {
		geracao := m.geracaoEntrada.Load()

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

			m.reclamar("abrir microfone", err)
			if !esperar(ctx, 2*time.Second) {
				return
			}
			continue
		}

		cod, err := NovoCodificador(fonte.Taxa(), CanaisDeVoz)
		if err != nil {
			m.reclamar("criar codificador", err)
			fonte.Fechar()
			return
		}

		ficouMuda := m.bombearMicrofone(ctx, fonte, cod, geracao)
		cod.Fechar()
		fonte.Fechar()

		if ficouMuda && querEco {
			m.ecoReprovado.Store(true)
			m.reclamar("cancelador de eco",
				fmt.Errorf("não entregou áudio nesta máquina; seguindo sem ele"))
		}
	}
}

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

func (m *Motor) bombearMicrofone(ctx context.Context, mic FonteDeAudio, cod *Codificador, geracao uint64) bool {

	porQuadro := mic.Taxa() * MilissegundosPorQuadro / 1000

	acumulado := make([]int16, 0, porQuadro*4)
	bloco := make([]int16, porQuadro*4)
	pacote := make([]byte, 4000)

	var det DetectorDeFala

	defer func() {
		if det.Calar() {
			m.saida.Manda(Evento{Ev: EvFala, V: marcaDeFala(false)})
		}
	}()

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

			if v := m.volumeDoMic.Load(); v != ganhoInteiro {
				for i := range quadro {
					quadro[i] = int16(int32(quadro[i]) * v / ganhoInteiro)
				}
			}

			mudo := m.mudo.Load()

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

				if err := m.faixa.WriteSample(amostra); err != nil {
					m.reclamar("enviar voz", err)
					return false
				}
			}

			acumulado = acumulado[porQuadro:]
		}

		if len(acumulado) == 0 {
			acumulado = acumulado[:0]
		}
	}
}

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

		m.avisarSaida.Do(func() { close(m.saidaPronta) })

		m.bombearSaida(ctx, alto, geracao)
		alto.Fechar()
	}
}

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

		livre, err := alto.EspacoLivre()
		if err != nil {
			m.reclamar("consultar a saída", err)
			return
		}
		for livre >= AmostrasPorQuadro {
			vozes := m.misturador.Puxar(quadro)

			bloco := quadro
			if vozes == 0 || m.surdo.Load() {

				bloco = nil
			}
			if err := alto.Escrever(bloco); err != nil {
				m.reclamar("tocar voz", err)
				return
			}

			if vozes == 0 {
				break
			}
			livre -= AmostrasPorQuadro
		}
	}
}

func (m *Motor) reclamar(oQueFazia string, err error) {
	msg := fmt.Sprintf("%s: %v", oQueFazia, err)
	fmt.Fprintln(os.Stderr, msg)
	m.saida.Manda(Evento{Ev: EvErro, Msg: msg})
}
