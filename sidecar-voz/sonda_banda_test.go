package main

import (
	"context"
	"fmt"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/pion/interceptor"
	"github.com/pion/interceptor/pkg/cc"
	"github.com/pion/interceptor/pkg/gcc"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func TestSondaDaEstimativaDeBanda(t *testing.T) {

	if os.Getenv("ASTRA_TESTE_BANDA") == "" {
		t.Skip("defina ASTRA_TESTE_BANDA=1 (sonda de registro, 30s e barulhenta)")
	}
	ctx, cancelar := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancelar()

	api, estimadores := apiComEstimativa(t, 1_000_000)

	remetente, err := api.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o remetente: %v", err)
	}
	defer remetente.Close()

	receptor, err := apiDoReceptor(t).NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o receptor: %v", err)
	}
	defer receptor.Close()

	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "sonda")
	if err != nil {
		t.Fatalf("criar a faixa: %v", err)
	}
	envio, err := remetente.AddTrack(faixa)
	if err != nil {
		t.Fatalf("somar a faixa: %v", err)
	}

	var mu sync.Mutex
	tipos := map[string]int{}
	go func() {
		for {
			pacotes, _, err := envio.ReadRTCP()
			if err != nil {
				return
			}
			mu.Lock()
			for _, p := range pacotes {
				tipos[fmt.Sprintf("%T", p)]++
			}
			mu.Unlock()
		}
	}()
	defer func() {
		mu.Lock()
		defer mu.Unlock()
		t.Logf("")
		t.Logf("RTCP QUE VOLTOU DO OUTRO LADO:")
		if len(tipos) == 0 {
			t.Logf("  nada")
		}
		for nome, quantos := range tipos {
			t.Logf("  %-48s %d", nome, quantos)
		}
	}()

	var comExtensao, semExtensao int
	receptor.OnTrack(func(remota *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		for {
			p, _, err := remota.ReadRTP()
			if err != nil {
				return
			}
			mu.Lock()
			if p.Header.GetExtension(4) != nil {
				comExtensao++
			} else {
				semExtensao++
			}
			mu.Unlock()
		}
	})
	defer func() {
		mu.Lock()
		defer mu.Unlock()
		t.Logf("PACOTES RECEBIDOS: %d com a extensão do TWCC, %d sem", comExtensao, semExtensao)
	}()

	ligado := make(chan struct{})
	var umaVez bool
	remetente.OnConnectionStateChange(func(estado webrtc.PeerConnectionState) {
		if estado == webrtc.PeerConnectionStateConnected && !umaVez {
			umaVez = true
			close(ligado)
		}
	})

	if err := apertarMaos(t, remetente, receptor); err != nil {
		t.Fatalf("conectar: %v", err)
	}

	for nome, sdp := range map[string]string{
		"oferta":   remetente.LocalDescription().SDP,
		"resposta": receptor.LocalDescription().SDP,
	} {
		achou := false
		for _, linha := range strings.Split(sdp, "\n") {
			if strings.Contains(linha, "transport-cc") || strings.Contains(linha, "transport-wide-cc") {
				achou = true
				t.Logf("  %s: %s", nome, strings.TrimSpace(linha))
			}
		}
		if !achou {
			t.Logf("  %s: SEM transport-cc", nome)
		}
	}
	select {
	case <-ligado:
	case <-time.After(15 * time.Second):
		t.Fatal("os dois lados não conectaram")
	}

	var bwe cc.BandwidthEstimator
	select {
	case bwe = <-estimadores:
	case <-time.After(5 * time.Second):
		t.Fatal("o interceptor de congestionamento não criou estimador nenhum")
	}

	fase := func(nome string, kbps int, quanto time.Duration) (int, int) {
		t.Logf("")
		t.Logf("--- %s: oferecendo %d kbps por %v ---", nome, kbps, quanto)

		const porSegundo = 30
		porQuadro := kbps * 1000 / 8 / porSegundo
		quadro := make([]byte, porQuadro)

		copy(quadro, []byte{0, 0, 0, 1, 0x41})

		relogio := time.NewTicker(time.Second / porSegundo)
		defer relogio.Stop()
		relato := time.NewTicker(time.Second)
		defer relato.Stop()

		fim := time.Now().Add(quanto)
		menor, maior := bwe.GetTargetBitrate(), bwe.GetTargetBitrate()
		for time.Now().Before(fim) {
			select {
			case <-ctx.Done():
				return menor, maior
			case <-relogio.C:
				_ = faixa.WriteSample(media.Sample{
					Data:     quadro,
					Duration: time.Second / porSegundo,
				})
			case <-relato.C:
				alvo := bwe.GetTargetBitrate()
				if alvo < menor {
					menor = alvo
				}
				if alvo > maior {
					maior = alvo
				}

				e := bwe.GetStats()
				t.Logf("  alvo %6d kbps  |  perda %5.1f%% -> %d kbps  |  atraso %.1fms (limite %.1fms) -> %d kbps  |  %v/%v",
					alvo/1000,
					100*paraFloat(e["averageLoss"]),
					paraInt(e["lossTargetBitrate"])/1000,
					paraFloat(e["delayMeasurement"]),
					paraFloat(e["delayThreshold"]),
					paraInt(e["delayTargetBitrate"])/1000,
					e["usage"], e["state"],
				)
			}
		}
		return menor, maior
	}

	_, maiorMagro := fase("FASE 1", 1000, 12*time.Second)
	_, maiorGordo := fase("FASE 2", 6000, 18*time.Second)

	t.Logf("")
	t.Logf("VEREDITO")
	t.Logf("  oferecendo 1.000 kbps, o alvo chegou a %d kbps", maiorMagro/1000)
	t.Logf("  oferecendo 6.000 kbps, o alvo chegou a %d kbps", maiorGordo/1000)
	switch {
	case maiorGordo > maiorMagro*3/2:
		t.Logf("  => ELE SOBE. A estimativa acompanha a banda disponível, não só o que mandamos.")
	default:
		t.Logf("  => ELE SÓ ACOMPANHA o que oferecemos (região limitada pela aplicação).")
		t.Logf("     Em produção isso serve para RECUAR, não para subir — subir exige")
		t.Logf("     sondagem própria, ou aceitar que o preset é o teto.")
	}
}

func apiComEstimativa(t *testing.T, inicial int) (*webrtc.API, chan cc.BandwidthEstimator) {
	t.Helper()

	motor := &webrtc.MediaEngine{}
	if err := motor.RegisterDefaultCodecs(); err != nil {
		t.Fatalf("registrar codecs: %v", err)
	}
	registro := &interceptor.Registry{}
	if err := webrtc.RegisterDefaultInterceptors(motor, registro); err != nil {
		t.Fatalf("registrar interceptores padrão: %v", err)
	}

	fabrica, err := cc.NewInterceptor(func() (cc.BandwidthEstimator, error) {
		return gcc.NewSendSideBWE(
			gcc.SendSideBWEInitialBitrate(inicial),
			gcc.SendSideBWEMinBitrate(150_000),
			gcc.SendSideBWEMaxBitrate(12_000_000),
		)
	})
	if err != nil {
		t.Fatalf("criar o interceptor de congestionamento: %v", err)
	}

	estimadores := make(chan cc.BandwidthEstimator, 4)
	fabrica.OnNewPeerConnection(func(_ string, e cc.BandwidthEstimator) {
		estimadores <- e
	})
	registro.Add(fabrica)

	if err := webrtc.ConfigureTWCCHeaderExtensionSender(motor, registro); err != nil {
		t.Fatalf("registrar a extensão de cabeçalho do TWCC: %v", err)
	}

	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
	), estimadores
}

func apertarMaos(t *testing.T, de, para *webrtc.PeerConnection) error {
	t.Helper()

	oferta, err := de.CreateOffer(nil)
	if err != nil {
		return err
	}
	pronto := webrtc.GatheringCompletePromise(de)
	if err := de.SetLocalDescription(oferta); err != nil {
		return err
	}
	<-pronto

	if err := para.SetRemoteDescription(*de.LocalDescription()); err != nil {
		return err
	}
	resposta, err := para.CreateAnswer(nil)
	if err != nil {
		return err
	}
	prontoDois := webrtc.GatheringCompletePromise(para)
	if err := para.SetLocalDescription(resposta); err != nil {
		return err
	}
	<-prontoDois

	return de.SetRemoteDescription(*para.LocalDescription())
}

func paraFloat(v any) float64 {
	if f, ok := v.(float64); ok {
		return f
	}
	return 0
}

func paraInt(v any) int {
	if n, ok := v.(int); ok {
		return n
	}
	return 0
}

func apiDoReceptor(t *testing.T) *webrtc.API {
	t.Helper()

	motor := &webrtc.MediaEngine{}
	if err := motor.RegisterDefaultCodecs(); err != nil {
		t.Fatalf("registrar codecs: %v", err)
	}
	registro := &interceptor.Registry{}
	if err := webrtc.RegisterDefaultInterceptors(motor, registro); err != nil {
		t.Fatalf("registrar interceptores padrão: %v", err)
	}
	if err := webrtc.ConfigureTWCCHeaderExtensionSender(motor, registro); err != nil {
		t.Fatalf("registrar a extensão de cabeçalho do TWCC: %v", err)
	}
	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
	)
}
