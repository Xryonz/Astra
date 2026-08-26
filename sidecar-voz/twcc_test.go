package main

import (
	"sync"
	"testing"
	"time"

	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func corrida(simbolo uint16, quantos uint16) rtcp.PacketStatusChunk {
	return &rtcp.RunLengthChunk{PacketStatusSymbol: simbolo, RunLength: quantos}
}

func TestContagemDoTwccPorCorrida(t *testing.T) {
	pacote := &rtcp.TransportLayerCC{
		PacketStatusCount: 10,
		PacketChunks: []rtcp.PacketStatusChunk{
			corrida(rtcp.TypeTCCPacketReceivedSmallDelta, 7),
			corrida(rtcp.TypeTCCPacketNotReceived, 3),
		},
	}

	recebidos, perdidos := contarNoTwcc(pacote)
	if recebidos != 7 || perdidos != 3 {
		t.Fatalf("contou %d recebidos e %d perdidos, esperava 7 e 3", recebidos, perdidos)
	}
}

func TestContagemDoTwccPorVetor(t *testing.T) {
	pacote := &rtcp.TransportLayerCC{
		PacketStatusCount: 6,
		PacketChunks: []rtcp.PacketStatusChunk{
			&rtcp.StatusVectorChunk{
				SymbolSize: rtcp.TypeTCCSymbolSizeTwoBit,
				SymbolList: []uint16{
					rtcp.TypeTCCPacketReceivedSmallDelta,
					rtcp.TypeTCCPacketNotReceived,
					rtcp.TypeTCCPacketReceivedLargeDelta,
					rtcp.TypeTCCPacketNotReceived,
					rtcp.TypeTCCPacketReceivedWithoutDelta,
					rtcp.TypeTCCPacketReceivedSmallDelta,
				},
			},
		},
	}

	recebidos, perdidos := contarNoTwcc(pacote)
	if recebidos != 4 || perdidos != 2 {
		t.Fatalf("contou %d recebidos e %d perdidos, esperava 4 e 2", recebidos, perdidos)
	}
}

func TestContagemDoTwccIgnoraOEnchimentoDoFim(t *testing.T) {
	pacote := &rtcp.TransportLayerCC{
		PacketStatusCount: 3,
		PacketChunks: []rtcp.PacketStatusChunk{
			corrida(rtcp.TypeTCCPacketReceivedSmallDelta, 3),
			corrida(rtcp.TypeTCCPacketNotReceived, 200),
		},
	}

	recebidos, perdidos := contarNoTwcc(pacote)
	if recebidos != 3 || perdidos != 0 {
		t.Fatalf("contou %d recebidos e %d perdidos, esperava 3 e 0: "+
			"o enchimento depois de PacketStatusCount virou perda inventada", recebidos, perdidos)
	}
}

func TestAJanelaDoTwccSoFechaNoPrazo(t *testing.T) {
	var p PerdaPeloTwcc
	comeco := time.Now()

	cheio := &rtcp.TransportLayerCC{
		PacketStatusCount: 10,
		PacketChunks: []rtcp.PacketStatusChunk{
			corrida(rtcp.TypeTCCPacketReceivedSmallDelta, 8),
			corrida(rtcp.TypeTCCPacketNotReceived, 2),
		},
	}

	if _, fechou := p.Somar(cheio, comeco); fechou {
		t.Fatal("a janela fechou no primeiro relatório")
	}
	if _, fechou := p.Somar(cheio, comeco.Add(janelaDoTwcc/2)); fechou {
		t.Fatal("a janela fechou antes do prazo")
	}

	fracao, fechou := p.Somar(cheio, comeco.Add(janelaDoTwcc))
	if !fechou {
		t.Fatal("a janela não fechou no prazo")
	}
	if fracao < 0.19 || fracao > 0.21 {
		t.Errorf("perda somada deu %.3f, esperava 0.20 (6 perdidos em 30)", fracao)
	}
}

func TestOTwccVoltaDoOutroLado(t *testing.T) {
	nascedouro, err := fabricaDePares()
	if err != nil {
		t.Fatalf("montar a fábrica: %v", err)
	}

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

	receptor.OnTrack(func(remota *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		for {
			if _, _, err := remota.ReadRTP(); err != nil {
				return
			}
		}
	})

	var mu sync.Mutex
	var chegaramTwcc, chegaramRelatorios, contados int
	go func() {
		for {
			pacotes, _, err := envio.ReadRTCP()
			if err != nil {
				return
			}
			mu.Lock()
			for _, pacote := range pacotes {
				switch recado := pacote.(type) {
				case *rtcp.TransportLayerCC:
					chegaramTwcc++
					recebidos, perdidos := contarNoTwcc(recado)
					contados += recebidos + perdidos
				case *rtcp.ReceiverReport:
					chegaramRelatorios++
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

	const porSegundo = 30
	relogio := time.NewTicker(time.Second / porSegundo)
	defer relogio.Stop()

	quadro := make([]byte, 1200)
	copy(quadro, []byte{0, 0, 0, 1, 0x41})

	fim := time.Now().Add(2500 * time.Millisecond)
	enviados := 0
	for time.Now().Before(fim) {
		<-relogio.C
		if err := faixa.WriteSample(media.Sample{
			Data:     quadro,
			Duration: time.Second / porSegundo,
		}); err != nil {
			t.Fatalf("enviar quadro: %v", err)
		}
		enviados++
	}

	time.Sleep(300 * time.Millisecond)

	mu.Lock()
	defer mu.Unlock()
	t.Logf("%d quadros enviados · %d relatórios TWCC cobrindo %d pacotes · %d ReceiverReports",
		enviados, chegaramTwcc, contados, chegaramRelatorios)

	if chegaramTwcc == 0 {
		t.Fatal("nenhum TransportLayerCC voltou: sem a extensão de cabeçalho nos pacotes que saem, " +
			"o outro lado não tem o que numerar e o sinal de congestionamento nunca nasce")
	}
	if contados == 0 {
		t.Error("os relatórios TWCC voltaram vazios")
	}
}
