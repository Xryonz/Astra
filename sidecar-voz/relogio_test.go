package main

import (
	"runtime"
	"sync"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

func ligarDireto(t *testing.T, a, b *webrtc.PeerConnection) {
	t.Helper()

	oferta, err := a.CreateOffer(nil)
	if err != nil {
		t.Fatalf("montar oferta: %v", err)
	}
	juntou := webrtc.GatheringCompletePromise(a)
	if err := a.SetLocalDescription(oferta); err != nil {
		t.Fatalf("assumir oferta: %v", err)
	}
	<-juntou

	if err := b.SetRemoteDescription(*a.LocalDescription()); err != nil {
		t.Fatalf("aceitar oferta: %v", err)
	}
	resposta, err := b.CreateAnswer(nil)
	if err != nil {
		t.Fatalf("montar resposta: %v", err)
	}
	juntouB := webrtc.GatheringCompletePromise(b)
	if err := b.SetLocalDescription(resposta); err != nil {
		t.Fatalf("assumir resposta: %v", err)
	}
	<-juntouB

	if err := a.SetRemoteDescription(*b.LocalDescription()); err != nil {
		t.Fatalf("aceitar resposta: %v", err)
	}
}

func TestORelogioDoVideoAcompanhaOTempoReal(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	plateia := NovaPlateia()
	faixa, err := plateia.Entrar("B")
	if err != nil {
		t.Fatalf("abrir a faixa: %v", err)
	}

	config := webrtc.Configuration{}
	quemTransmite, err := webrtc.NewPeerConnection(config)
	if err != nil {
		t.Fatalf("criar quem transmite: %v", err)
	}
	defer quemTransmite.Close()
	if _, err := quemTransmite.AddTrack(faixa); err != nil {
		t.Fatalf("publicar a faixa: %v", err)
	}

	quemAssiste, err := webrtc.NewPeerConnection(config)
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer quemAssiste.Close()

	type marca struct {
		relogio uint32
		parede  time.Time
	}
	var mu sync.Mutex
	var marcas []marca

	quemAssiste.OnTrack(func(remota *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		for {
			pacote, _, err := remota.ReadRTP()
			if err != nil {
				return
			}
			mu.Lock()
			marcas = append(marcas, marca{relogio: pacote.Timestamp, parede: time.Now()})
			mu.Unlock()
		}
	})

	ligarDireto(t, quemTransmite, quemAssiste)

	emissor := NovoEmissor(plateia, NewEscritor(descartar{}), nil)
	emissor.Ligar(AjustesDaTela{Monitor: 0, Largura: 1280, Altura: 720, Fps: 30, Kbps: 2500})
	time.Sleep(8 * time.Second)
	emissor.Desligar()

	mu.Lock()
	defer mu.Unlock()

	if len(marcas) < 20 {
		t.Skipf("só %d pacotes chegaram — a tela pode estar parada demais", len(marcas))
	}

	primeira, ultima := marcas[0], marcas[len(marcas)-1]
	naParede := ultima.parede.Sub(primeira.parede)
	if naParede < 3*time.Second {
		t.Skipf("só %v de fluxo — pouco para medir deriva", naParede.Round(time.Millisecond))
	}

	tiques := ultima.relogio - primeira.relogio
	naMidia := time.Duration(float64(tiques) / 90_000 * float64(time.Second))

	razao := naMidia.Seconds() / naParede.Seconds()
	t.Logf("%d pacotes · relógio da mídia %v para %v de tempo real (%.0f%%)",
		len(marcas), naMidia.Round(time.Millisecond), naParede.Round(time.Millisecond), razao*100)

	if razao < 0.75 || razao > 1.25 {
		t.Errorf("o relógio da mídia andou a %.0f%% do tempo real; áudio e vídeo se separam nessa taxa", razao*100)
	}
}

type descartar struct{}

func (descartar) Write(p []byte) (int, error) { return len(p), nil }

func TestOCompressorDevolveOCarimboDeTempo(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		t.Fatalf("iniciar Media Foundation: %v", err)
	}
	defer fecharMF()

	tela, err := AbrirTela(0)
	if err != nil {
		t.Fatalf("abrir a tela: %v", err)
	}
	defer tela.Fechar()

	c, err := AbrirCompressor(tela, 1280, 720, 30, 2500)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()
	t.Logf("compressor: %s (na memória=%v)", c.Nome, c.NaMemoria)

	var carimbos []time.Duration
	semCarimbo := 0
	depoisDoPedido := 0

	comeco := time.Now()
	fim := comeco.Add(4 * time.Second)
	for time.Now().Before(fim) {
		textura, err := tela.ProximoQuadro(100)
		if err != nil || textura == 0 {
			continue
		}
		quando := time.Since(comeco)
		err = c.Comprimir(textura, quando, nil, func(_ []byte, carimbo time.Duration) {
			switch {
			case carimbo < 0:
				semCarimbo++
			case carimbo > quando+50*time.Millisecond:
				depoisDoPedido++
			default:
				carimbos = append(carimbos, carimbo)
			}
		})
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			t.Fatalf("comprimir: %v", err)
		}
	}
	decorrido := time.Since(comeco)

	if semCarimbo > 0 {
		t.Errorf("%d quadros saíram SEM carimbo de tempo; o relógio do vídeo depende dele", semCarimbo)
	}
	if depoisDoPedido > 0 {
		t.Errorf("%d quadros saíram com carimbo NO FUTURO do quadro que os gerou", depoisDoPedido)
	}
	if len(carimbos) < 5 {
		t.Skipf("só %d quadros em %v — mexa numa janela e rode de novo", len(carimbos), decorrido.Round(time.Millisecond))
	}

	for i := 1; i < len(carimbos); i++ {
		if carimbos[i] < carimbos[i-1] {
			t.Fatalf("o carimbo andou para trás: %v depois de %v", carimbos[i], carimbos[i-1])
		}
	}

	vao := carimbos[len(carimbos)-1] - carimbos[0]
	t.Logf("%d quadros em %v · o carimbo cobriu %v",
		len(carimbos), decorrido.Round(time.Millisecond), vao.Round(time.Millisecond))

	if vao < decorrido/2 {
		t.Errorf("o carimbo cobriu %v de %v de tempo real: ele não está seguindo o relógio",
			vao.Round(time.Millisecond), decorrido.Round(time.Millisecond))
	}
}
