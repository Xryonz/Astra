package main

import (
	"testing"

	"github.com/pion/interceptor"
	"github.com/pion/webrtc/v4"
)

// A malha tinha uma fabrica de conexoes (fabricaDePares) e dois testes de
// MEDICAO pegavam carona nela: eles nao testam a malha, testam que ida-e-volta e
// perda saem certos quando ha RTCP de verdade voltando. Com a malha fora, a
// fabrica passa a viver aqui -- sem o estimador de banda do GCC, que era a unica
// parte que existia para a malha decidir taxa (no SFU quem decide e o servidor).
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

// Vivia no caminho.go porque a malha precisava dele em toda faixa que chegava.
// No SFU quem le o RTCP das faixas assinadas e o SDK; sobrou como utilitario de
// teste, e e aqui que ele pertence.
func escoarRtcp(receptor *webrtc.RTPReceiver) {
	for {
		if _, _, err := receptor.ReadRTCP(); err != nil {
			return
		}
	}
}
