package main

import (
	"context"
	"fmt"
	"os"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

var CapacidadeH264 = webrtc.RTPCodecCapability{
	MimeType:    webrtc.MimeTypeH264,
	ClockRate:   90000,
	SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
}

const (
	quadrosDescartados = 8
	quadrosMedidos     = 16
)

const sinalDeVida = 2 * time.Second

type AjustesDaTela struct {
	Monitor int
	Janela  uint64
	Largura int
	Altura  int
	Fps     int
	Kbps    int
}

func (a AjustesDaTela) abrirFonte() (*Tela, error) {
	if a.Janela == 0 {
		tela, err := AbrirTela(a.Monitor)
		if err != nil {
			return nil, fmt.Errorf("abrir a tela: %w", err)
		}
		return tela, nil
	}
	j, ok := descreverJanela(uintptr(a.Janela))
	if !ok {
		return nil, fmt.Errorf("a janela escolhida não está mais disponível")
	}
	tela, err := AbrirJanela(uintptr(a.Janela), j.Largura, j.Altura)
	if err != nil {
		return nil, fmt.Errorf("abrir a janela %q: %w", j.Nome, err)
	}
	return tela, nil
}

type Emissor struct {
	plateia *PlateiaDaTela
	saida   *Escritor

	mu     sync.Mutex
	parar  context.CancelFunc
	parada chan struct{}

	querChave atomic.Bool

	perdas *PerdaDosPares

	entrega *EntregaDeQuadros
}

func NovoEmissor(plateia *PlateiaDaTela, saida *Escritor, entrega *EntregaDeQuadros) *Emissor {
	return &Emissor{plateia: plateia, saida: saida, perdas: NovaPerdaDosPares(), entrega: entrega}
}

func (e *Emissor) PerdaRelatada(par string, fracao float64) { e.perdas.Relatar(par, fracao) }

func (e *Emissor) BandaRelatada(par string, kbps int) { e.perdas.RelatarBanda(par, kbps) }

func (e *Emissor) EsquecerPar(par string) { e.perdas.Esquecer(par) }

func (e *Emissor) PedirQuadroChave() { e.querChave.Store(true) }

func (e *Emissor) Ligar(aj AjustesDaTela) {
	e.Desligar()

	ctx, cancelar := context.WithCancel(context.Background())
	parada := make(chan struct{})

	e.mu.Lock()
	e.parar = cancelar
	e.parada = parada
	e.mu.Unlock()

	go func() {
		defer close(parada)
		if err := e.laco(ctx, aj); err != nil && ctx.Err() == nil {

			fmt.Fprintf(os.Stderr, "transmissão parou: %v\n", err)
			e.saida.Manda(Evento{Ev: EvErro, Msg: "transmissão parou: " + err.Error()})
		}
		e.saida.Manda(Evento{Ev: EvTransmissao, V: "0"})
	}()
}

func (e *Emissor) Desligar() {
	e.mu.Lock()
	parar, parada := e.parar, e.parada
	e.parar, e.parada = nil, nil
	e.mu.Unlock()

	if parar == nil {
		return
	}
	parar()
	<-parada
}

func (e *Emissor) laco(ctx context.Context, aj AjustesDaTela) error {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		return err
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return err
	}
	defer fecharMF()

	tela, err := aj.abrirFonte()
	if err != nil {
		return err
	}
	defer tela.Fechar()

	controle := NovoControleDeBanda(aj.Kbps)

	medir := true
	for {
		novo, err := e.transmitir(ctx, tela, aj, medir, controle)
		if err != nil || ctx.Err() != nil || novo == nil {
			return err
		}
		aj = *novo
		medir = false
	}
}

func (e *Emissor) transmitir(
	ctx context.Context, tela *Tela, aj AjustesDaTela, medir bool, controle *ControleDeBanda,
) (*AjustesDaTela, error) {
	c, err := AbrirCompressor(tela, aj.Largura, aj.Altura, aj.Fps, aj.Kbps)
	if err != nil {
		return nil, err
	}
	defer c.Fechar()

	if e.entrega != nil {
		c.LigarEspelho(func(q Quadro) { e.entrega.Mandar("", q) })
	}

	comoSubiu := fmt.Sprintf("%dx%d @%d", c.saidaL, c.saidaA, c.fps)
	if c.TaxaVariavel {
		comoSubiu += " · taxa variável"
	}
	if c.BaixaLatencia {
		comoSubiu += " · baixa latência"
	}
	if c.NaMemoria {
		comoSubiu = "sem aceleração de placa · " + comoSubiu
	}
	e.saida.Manda(Evento{
		Ev:   EvTransmissao,
		V:    "1",
		Tipo: c.Nome,
		Msg:  comoSubiu,
	})

	duracaoNominal := time.Second / time.Duration(c.fps)
	var ultimoCarimbo time.Duration
	temCarimbo := false

	ritmo := NovoRitmo(c.fps)
	comeco := time.Now()
	relatorio := time.Now()
	var quadros, bytesEnviados, capturados, semSaida, semMudanca, revividos int
	var marco, marcoDoRelato Custos
	perfilVisto := false

	var falhaAoEntregar error

	var abridor []byte
	var ultimaSaida time.Time
	reenviando := false
	entregar := func(quadroPronto []byte, carimbo time.Duration) {
		if carimbo < 0 {
			carimbo = time.Since(comeco)
		}
		duracao := duracaoNominal
		if temCarimbo {
			duracao = carimbo - ultimoCarimbo
			if duracao < 0 {
				duracao = 0
			}
		}
		ultimoCarimbo, temCarimbo = carimbo, true

		if !perfilVisto {
			if p, ok := perfilDoSPS(quadroPronto); ok {
				perfilVisto = true
				e.saida.Manda(Evento{Ev: EvTransmissao, V: "1", Tipo: "perfil", Msg: p})
			}
		}

		if !reenviando && abreImagemSozinho(quadroPronto) {
			abridor = append(abridor[:0], quadroPronto...)
		}
		ultimaSaida = time.Now()

		if _, err := e.plateia.Escrever(media.Sample{Data: quadroPronto, Duration: duracao}); err != nil && falhaAoEntregar == nil {
			falhaAoEntregar = err
		}
		quadros++
		bytesEnviados += len(quadroPronto)
	}

	for {
		if ctx.Err() != nil {
			return nil, nil
		}

		ritmo.Esperar()

		textura, err := tela.ProximoQuadro(100)
		if err != nil {
			if _, perdeu := err.(ErroDeAcessoPerdido); perdeu {

				if err := tela.Remontar(aj.Monitor); err != nil {
					return nil, fmt.Errorf("recuperar a tela: %w", err)
				}
				continue
			}
			return nil, fmt.Errorf("capturar: %w", err)
		}
		if textura == 0 {

			semMudanca++
			if err := c.Drenar(entregar); err != nil {
				return nil, fmt.Errorf("colher o que sobrou: %w", err)
			}
			if falhaAoEntregar != nil {
				return nil, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
			}

			if len(abridor) > 0 {
				pediram := e.querChave.Swap(false)
				if pediram || time.Since(ultimaSaida) >= sinalDeVida {
					reenviando = true
					entregar(abridor, time.Since(comeco))
					reenviando = false
					revividos++
					if falhaAoEntregar != nil {
						return nil, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
					}
				}
			}
			continue
		}
		capturados++

		if e.querChave.Swap(false) {
			if !c.ForcarQuadroChave() {

				fmt.Fprintf(os.Stderr, "%s não atende pedido de quadro-chave\n", c.Nome)
			}
		}

		saiuAlgo := false
		err = c.Comprimir(
			textura,
			time.Since(comeco),
			tela.SoltarQuadro,
			func(quadroPronto []byte, carimbo time.Duration) {
				saiuAlgo = true
				entregar(quadroPronto, carimbo)
			},
		)
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			return nil, fmt.Errorf("comprimir: %w", err)
		}
		if falhaAoEntregar != nil {
			return nil, fmt.Errorf("entregar o quadro: %w", falhaAoEntregar)
		}
		if !saiuAlgo {

			semSaida++
		}

		if medir {
			switch c.Custos.Quadros {
			case quadrosDescartados:

				marco = c.Custos
			case quadrosDescartados + quadrosMedidos:
				medir = false
				custo := (c.Custos.Total() - marco.Total()) / quadrosMedidos
				if nova := TaxaQueCabe(custo, c.fps); nova < c.fps {

					e.saida.Manda(Evento{
						Ev:   EvTransmissao,
						V:    "1",
						Tipo: "taxa",
						Msg: fmt.Sprintf("esta máquina gasta %.1fms por quadro; caindo para %d/s",
							float64(custo.Microseconds())/1000, nova),
					})
					proximo := aj
					proximo.Fps = nova
					return &proximo, nil
				}
			}
		}

		if desde := time.Since(relatorio); desde >= time.Second {
			perda := e.perdas.PiorDaMaioria()
			msg := fmt.Sprintf("%d fps · %.1f de %d Mbps · %.0f%% perdido · %d capturados · %d sem saída · %d sem mudança",
				int(float64(quadros)/desde.Seconds()),
				float64(bytesEnviados)*8/desde.Seconds()/1_000_000,
				controle.Banda()/1000,
				perda*100,
				capturados, semSaida, semMudanca)

			if revividos > 0 {
				msg += fmt.Sprintf(" · %d reenviados com a tela parada", revividos)
			}
			if assistindo, total := e.plateia.Contar(); total > assistindo {
				msg += fmt.Sprintf(" · enviando para %d de %d", assistindo, total)
			}
			if atras := e.perdas.QuantosFicamAtras(controle.Banda()); atras > 0 {
				msg += fmt.Sprintf(" · %d sem rede para acompanhar", atras)
			}

			gasto := c.Custos.Menos(marcoDoRelato)
			marcoDoRelato = c.Custos
			if gasto.Quadros > 0 {
				m := gasto.Media()
				msg += fmt.Sprintf(" · por quadro %.1fms (cópia %.1f · redução %.1f · compressão %.1f · leitura %.1f · espera %.1f)",
					emMs(m.Total()+m.PedidoDeEntrada+m.SaidaPronta),
					emMs(m.Copia), emMs(m.Reducao), emMs(m.Compressao), emMs(m.Leitura),
					emMs(m.PedidoDeEntrada+m.SaidaPronta))
			}
			e.saida.Manda(Evento{Ev: EvTransmissao, V: "1", Tipo: "ritmo", Msg: msg})
			relatorio = time.Now()
			quadros, bytesEnviados, capturados, semSaida, semMudanca, revividos = 0, 0, 0, 0, 0, 0

			if !medir {
				if alvo, medido := e.perdas.BandaDaMaioria(); medido {
					if nova, mudou := controle.Sugerido(alvo); mudou {
						e.saida.Manda(Evento{
							Ev: EvTransmissao, V: "1", Tipo: "ritmo",
							Msg: fmt.Sprintf("a rede comporta %d kbps; ajustando para %d", alvo, nova),
						})
						proximo := aj
						proximo.Kbps = nova
						return &proximo, nil
					}
				} else if nova, mudou := controle.Segundo(perda); mudou {
					e.saida.Manda(Evento{
						Ev: EvTransmissao, V: "1", Tipo: "ritmo",
						Msg: fmt.Sprintf("%.0f%% dos pacotes não chegam; ajustando para %d kbps",
							perda*100, nova),
					})
					proximo := aj
					proximo.Kbps = nova
					return &proximo, nil
				}
			}
		}
	}
}

func emMs(d time.Duration) float64 { return float64(d.Microseconds()) / 1000 }

func perfilDoSPS(fluxo []byte) (string, bool) {
	perfil, achou := "", false
	percorrerNal(fluxo, func(tipo byte, inicio int) bool {

		if tipo != 7 || inicio+3 >= len(fluxo) {
			return true
		}
		perfil = fmt.Sprintf("%02x%02x%02x", fluxo[inicio+1], fluxo[inicio+2], fluxo[inicio+3])
		achou = true
		return false
	})
	return perfil, achou
}

func percorrerNal(fluxo []byte, cada func(tipo byte, inicio int) bool) {
	for i := 0; i+4 < len(fluxo); i++ {
		if fluxo[i] != 0 || fluxo[i+1] != 0 {
			continue
		}
		inicio := 0
		switch {
		case fluxo[i+2] == 1:
			inicio = i + 3
		case fluxo[i+2] == 0 && i+5 < len(fluxo) && fluxo[i+3] == 1:
			inicio = i + 4
		default:
			continue
		}
		if !cada(fluxo[inicio]&0x1F, inicio) {
			return
		}
	}
}

func abreImagemSozinho(fluxo []byte) bool {
	var temSPS, temPPS, temIDR bool
	percorrerNal(fluxo, func(tipo byte, _ int) bool {
		switch tipo {
		case 7:
			temSPS = true
		case 8:
			temPPS = true
		case 5:
			temIDR = true
		}
		return !(temSPS && temPPS && temIDR)
	})
	return temSPS && temPPS && temIDR
}
