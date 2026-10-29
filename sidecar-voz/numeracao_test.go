package main

import (
	"net"
	"sync"
	"testing"
	"time"

	"github.com/pion/ice/v4"
	"github.com/pion/interceptor"
	"github.com/pion/interceptor/pkg/cc"
	"github.com/pion/sdp/v3"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func soDentroDoComputador() webrtc.SettingEngine {
	regras := webrtc.SettingEngine{}
	regras.SetIncludeLoopbackCandidate(true)
	regras.SetIPFilter(func(ip net.IP) bool { return ip.IsLoopback() })
	regras.SetICEMulticastDNSMode(ice.MulticastDNSModeDisabled)
	return regras
}

func apiComoALiveKitMonta(t *testing.T, interceptadores []interceptor.Factory) *webrtc.API {
	t.Helper()

	motor := &webrtc.MediaEngine{}
	if err := motor.RegisterDefaultCodecs(); err != nil {
		t.Fatalf("registrar os codecs: %v", err)
	}
	if err := motor.RegisterHeaderExtension(
		webrtc.RTPHeaderExtensionCapability{URI: sdp.TransportCCURI}, webrtc.RTPCodecTypeVideo,
	); err != nil {
		t.Fatalf("registrar a extensão do TWCC: %v", err)
	}

	registro := &interceptor.Registry{}
	for _, fabrica := range interceptadores {
		registro.Add(fabrica)
	}

	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
		webrtc.WithSettingEngine(soDentroDoComputador()),
	)
}

func receptorDentroDoComputador(t *testing.T) *webrtc.API {
	t.Helper()

	motor := &webrtc.MediaEngine{}
	if err := motor.RegisterDefaultCodecs(); err != nil {
		t.Fatalf("registrar os codecs: %v", err)
	}
	registro := &interceptor.Registry{}
	if err := webrtc.RegisterDefaultInterceptors(motor, registro); err != nil {
		t.Fatalf("registrar os interceptadores: %v", err)
	}

	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
		webrtc.WithSettingEngine(soDentroDoComputador()),
	)
}

func TestAConexaoMontadaComoNaLiveKitNumeraOVideo(t *testing.T) {
	interceptadores, err := interceptadoresDaSala(func(cc.BandwidthEstimator) {})
	if err != nil {
		t.Fatalf("montar os interceptadores da sala: %v", err)
	}

	remetente, err := apiComoALiveKitMonta(t, interceptadores).NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o remetente: %v", err)
	}
	defer remetente.Close()

	receptor, err := receptorDentroDoComputador(t).NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o receptor: %v", err)
	}
	defer receptor.Close()

	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "tela", "teste")
	if err != nil {
		t.Fatalf("criar a faixa: %v", err)
	}
	if _, err := remetente.AddTrack(faixa); err != nil {
		t.Fatalf("somar a faixa: %v", err)
	}

	receptor.OnTrack(func(remota *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		for {
			if _, _, err := remota.ReadRTP(); err != nil {
				return
			}
		}
	})

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

	quadro := make([]byte, 1200)
	copy(quadro, []byte{0, 0, 0, 1, 0x41})
	for i := 1; i <= 10; i++ {
		if err := faixa.WriteSample(media.Sample{Data: quadro, Duration: time.Second / 30}); err != nil {
			t.Fatalf("o quadro %d não saiu: %v", i, err)
		}
	}
}
