package main

import (
	"testing"

	"github.com/pion/interceptor"
	"github.com/pion/webrtc/v4"
)

func fabricaDeTeste(t *testing.T) *webrtc.API {
	t.Helper()

	motor := &webrtc.MediaEngine{}
	if err := motor.RegisterDefaultCodecs(); err != nil {
		t.Fatalf("registrar os codecs: %v", err)
	}

	registro := &interceptor.Registry{}
	if err := webrtc.RegisterDefaultInterceptors(motor, registro); err != nil {
		t.Fatalf("registrar os interceptores: %v", err)
	}
	if err := webrtc.ConfigureTWCCHeaderExtensionSender(motor, registro); err != nil {
		t.Fatalf("numerar os pacotes que saem: %v", err)
	}

	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
	)
}

func escoarRtcp(receptor *webrtc.RTPReceiver) {
	for {
		if _, _, err := receptor.ReadRTCP(); err != nil {
			return
		}
	}
}
