package main

import (
	"os"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

func TestSondaCadaStunResponde(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_STUN") == "" {
		t.Skip("defina ASTRA_SONDA_STUN=1 (depende da internet)")
	}

	for _, url := range stunPadrao {
		t.Run(url, func(t *testing.T) {
			pc, err := webrtc.NewPeerConnection(webrtc.Configuration{
				ICEServers: []webrtc.ICEServer{{URLs: []string{url}}},
			})
			if err != nil {
				t.Fatalf("criar conexão: %v", err)
			}
			defer pc.Close()

			refletido := make(chan string, 4)
			pc.OnICECandidate(func(c *webrtc.ICECandidate) {
				if c != nil && c.Typ == webrtc.ICECandidateTypeSrflx {
					select {
					case refletido <- c.Address:
					default:
					}
				}
			})

			if _, err := pc.CreateDataChannel("sonda", nil); err != nil {
				t.Fatalf("abrir canal: %v", err)
			}
			oferta, err := pc.CreateOffer(nil)
			if err != nil {
				t.Fatalf("montar oferta: %v", err)
			}
			comeco := time.Now()
			if err := pc.SetLocalDescription(oferta); err != nil {
				t.Fatalf("assumir oferta: %v", err)
			}

			select {
			case endereco := <-refletido:
				t.Logf("respondeu em %v e disse que meu endereço é %s",
					time.Since(comeco).Round(10*time.Millisecond), endereco)
			case <-time.After(8 * time.Second):
				t.Errorf("nenhum candidato refletido em 8s: este STUN só custa espera")
			}
		})
	}
}
