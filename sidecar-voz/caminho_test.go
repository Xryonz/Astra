package main

import (
	"sync"
	"testing"
	"time"

	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func emPassos(d time.Duration) uint32 {
	return uint32(d * 65536 / time.Second)
}

func TestIdaEVoltaDescontaOTempoQueOOutroSegurou(t *testing.T) {
	agora := time.Date(2026, 8, 26, 14, 30, 15, 123456789, time.UTC)

	casos := []struct {
		nome     string
		partiu   time.Duration
		segurou  time.Duration
		esperado time.Duration
	}{
		{"rede boa", 30 * time.Millisecond, 10 * time.Millisecond, 20 * time.Millisecond},
		{"rede ruim", 320 * time.Millisecond, 20 * time.Millisecond, 300 * time.Millisecond},
		{"o outro segurou muito", 500 * time.Millisecond, 450 * time.Millisecond, 50 * time.Millisecond},
	}

	for _, c := range casos {
		r := rtcp.ReceptionReport{
			LastSenderReport: ntpDoMeio(agora.Add(-c.partiu)),
			Delay:            emPassos(c.segurou),
		}

		d, ok := idaEVolta(r, agora)
		if !ok {
			t.Errorf("%s: o relato foi recusado", c.nome)
			continue
		}
		erro := d - c.esperado
		if erro < 0 {
			erro = -erro
		}
		if erro > 2*time.Millisecond {
			t.Errorf("%s: ida e volta deu %v, esperava %v", c.nome, d, c.esperado)
		}
	}
}

func TestIdaEVoltaRecusaOQueNaoDaParaMedir(t *testing.T) {
	agora := time.Now()

	if _, ok := idaEVolta(rtcp.ReceptionReport{LastSenderReport: 0}, agora); ok {
		t.Error("aceitou um relato sem relatorio anterior do remetente")
	}

	doFuturo := rtcp.ReceptionReport{
		LastSenderReport: ntpDoMeio(agora.Add(2 * time.Second)),
	}
	if d, ok := idaEVolta(doFuturo, agora); ok {
		t.Errorf("aceitou um relato com relogio adiantado e devolveu %v", d)
	}

	antigo := rtcp.ReceptionReport{
		LastSenderReport: ntpDoMeio(agora.Add(-30 * time.Second)),
	}
	if d, ok := idaEVolta(antigo, agora); ok {
		t.Errorf("aceitou uma ida e volta de %v", d)
	}
}

func TestTremorSaiNaTaxaDoCodec(t *testing.T) {
	if d := tremorDoRelato(rtcp.ReceptionReport{Jitter: 480}, TaxaDeAmostragem); d != 10*time.Millisecond {
		t.Errorf("tremor da voz deu %v, esperava 10ms", d)
	}
	if d := tremorDoRelato(rtcp.ReceptionReport{Jitter: 900}, 90000); d != 10*time.Millisecond {
		t.Errorf("tremor da tela deu %v, esperava 10ms", d)
	}
}

func TestOMedidorSoFalaNoPrazoESoComAmostra(t *testing.T) {
	m := NovoMedidorDoCaminho("tela", 90000)
	comeco := m.relatorio

	if _, pronto := m.Fechar(comeco.Add(relatarOCaminhoACada * 2)); pronto {
		t.Fatal("falou sem ter medido nada")
	}

	relato := &rtcp.ReceiverReport{Reports: []rtcp.ReceptionReport{{
		LastSenderReport: ntpDoMeio(comeco.Add(-40 * time.Millisecond)),
		Delay:            emPassos(10 * time.Millisecond),
		Jitter:           900,
	}}}
	m.Anotar(relato, comeco)

	if _, pronto := m.Fechar(comeco.Add(relatarOCaminhoACada / 2)); pronto {
		t.Fatal("falou antes do prazo")
	}

	leitura, pronto := m.Fechar(comeco.Add(relatarOCaminhoACada))
	if !pronto {
		t.Fatal("não falou no prazo, tendo amostra")
	}
	if leitura.Ida < 25 || leitura.Ida > 35 {
		t.Errorf("ida e volta %d ms, esperava perto de 30 (40 de ida menos 10 de espera)", leitura.Ida)
	}
	if leitura.Tremor != 10 {
		t.Errorf("tremor %d ms, esperava 10 (900 passos a 90000 Hz)", leitura.Tremor)
	}

	if _, pronto := m.Fechar(comeco.Add(relatarOCaminhoACada * 3)); pronto {
		t.Error("falou de novo sem amostra nova: os contadores não foram zerados")
	}
}

func TestOsRelatoriosTrazemIdaEVoltaDeVerdade(t *testing.T) {
	nascedouro := fabricaDeTeste(t)

	remetente, err := nascedouro.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o remetente: %v", err)
	}
	defer remetente.Close()

	receptor, err := nascedouro.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o receptor: %v", err)
	}
	defer receptor.Close()

	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "tela", "teste")
	if err != nil {
		t.Fatalf("criar a faixa: %v", err)
	}
	envio, err := remetente.AddTrack(faixa)
	if err != nil {
		t.Fatalf("somar a faixa: %v", err)
	}

	receptor.OnTrack(func(remota *webrtc.TrackRemote, quemRecebe *webrtc.RTPReceiver) {
		go escoarRtcp(quemRecebe)
		for {
			if _, _, err := remota.ReadRTP(); err != nil {
				return
			}
		}
	})

	var mu sync.Mutex
	var medidos int
	var maior time.Duration
	go func() {
		for {
			pacotes, _, err := envio.ReadRTCP()
			if err != nil {
				return
			}
			agora := time.Now()
			mu.Lock()
			for _, pacote := range pacotes {
				relato, ok := pacote.(*rtcp.ReceiverReport)
				if !ok {
					continue
				}
				for _, r := range relato.Reports {
					if d, ok := idaEVolta(r, agora); ok {
						medidos++
						if d > maior {
							maior = d
						}
					}
				}
			}
			mu.Unlock()
		}
	}()

	ligado := make(chan struct{})
	var umaVez sync.Once
	remetente.OnConnectionStateChange(func(estado webrtc.PeerConnectionState) {
		if estado == webrtc.PeerConnectionStateConnected {
			umaVez.Do(func() { close(ligado) })
		}
	})

	if err := apertarMaos(t, remetente, receptor); err != nil {
		t.Fatalf("conectar: %v", err)
	}
	select {
	case <-ligado:
	case <-time.After(10 * time.Second):
		t.Fatal("os dois lados não conectaram")
	}

	relogio := time.NewTicker(time.Second / 30)
	defer relogio.Stop()
	quadro := make([]byte, 1200)
	copy(quadro, []byte{0, 0, 0, 1, 0x41})

	fim := time.Now().Add(4 * time.Second)
	for time.Now().Before(fim) {
		<-relogio.C
		_ = faixa.WriteSample(media.Sample{Data: quadro, Duration: time.Second / 30})
	}

	mu.Lock()
	defer mu.Unlock()
	t.Logf("%d medições de ida e volta · maior %v", medidos, maior)

	if medidos == 0 {
		t.Fatal("nenhum ReceiverReport trouxe LSR/DLSR utilizáveis: não dá para medir latência")
	}
	if maior > 500*time.Millisecond {
		t.Errorf("ida e volta de %v em loopback: a conta de NTP está errada", maior)
	}
}
