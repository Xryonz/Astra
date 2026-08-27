package main

import (
	"os"
	"sort"
	"sync"
	"testing"
	"time"

	"github.com/pion/interceptor"
	"github.com/pion/interceptor/pkg/cc"
	"github.com/pion/interceptor/pkg/gcc"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func apiComPacer(t *testing.T, novoPacer func() gcc.Pacer) *webrtc.API {
	t.Helper()

	motor := &webrtc.MediaEngine{}
	if err := motor.RegisterDefaultCodecs(); err != nil {
		t.Fatalf("registrar codecs: %v", err)
	}
	registro := &interceptor.Registry{}
	if err := webrtc.RegisterDefaultInterceptors(motor, registro); err != nil {
		t.Fatalf("registrar interceptores: %v", err)
	}

	medidor, err := cc.NewInterceptor(func() (cc.BandwidthEstimator, error) {
		return gcc.NewSendSideBWE(
			gcc.SendSideBWEPacer(novoPacer()),
			gcc.SendSideBWEInitialBitrate(bandaInicialDoGcc),
			gcc.SendSideBWEMinBitrate(bandaMinima*1000),
			gcc.SendSideBWEMaxBitrate(bandaMaximaDoGcc),
		)
	})
	if err != nil {
		t.Fatalf("criar o medidor: %v", err)
	}
	registro.Add(medidor)

	if err := webrtc.ConfigureTWCCHeaderExtensionSender(motor, registro); err != nil {
		t.Fatalf("extensão de cabeçalho: %v", err)
	}
	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
	)
}

type buracosNaVoz struct {
	maior   time.Duration
	p95     time.Duration
	quadros int
}

func medirVozSobPressao(t *testing.T, api *webrtc.API, quanto time.Duration) buracosNaVoz {
	t.Helper()

	remetente, err := api.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o remetente: %v", err)
	}
	defer remetente.Close()

	receptor, err := api.NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatalf("criar o receptor: %v", err)
	}
	defer receptor.Close()

	voz, err := webrtc.NewTrackLocalStaticSample(CapacidadeOpus, "voz", "teste")
	if err != nil {
		t.Fatalf("criar a faixa de voz: %v", err)
	}
	if _, err := remetente.AddTrack(voz); err != nil {
		t.Fatalf("somar a voz: %v", err)
	}

	tela, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "tela", "teste")
	if err != nil {
		t.Fatalf("criar a faixa de tela: %v", err)
	}
	if _, err := remetente.AddTrack(tela); err != nil {
		t.Fatalf("somar a tela: %v", err)
	}

	var mu sync.Mutex
	var chegadas []time.Time

	receptor.OnTrack(func(remota *webrtc.TrackRemote, quemRecebe *webrtc.RTPReceiver) {
		go escoarRtcp(quemRecebe)
		ehVoz := remota.Kind() == webrtc.RTPCodecTypeAudio
		for {
			_, _, err := remota.ReadRTP()
			if err != nil {
				return
			}
			if !ehVoz {
				continue
			}
			mu.Lock()
			chegadas = append(chegadas, time.Now())
			mu.Unlock()
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

	quadroDeVoz := make([]byte, 160)
	quadroDeTela := make([]byte, 60_000)
	copy(quadroDeTela, []byte{0, 0, 0, 1, 0x65})

	parar := make(chan struct{})
	var pressao sync.WaitGroup
	pressao.Add(1)
	go func() {
		defer pressao.Done()
		relogio := time.NewTicker(time.Second / 30)
		defer relogio.Stop()
		for {
			select {
			case <-parar:
				return
			case <-relogio.C:
				_ = tela.WriteSample(media.Sample{
					Data:     quadroDeTela,
					Duration: time.Second / 30,
				})
			}
		}
	}()

	relogio := time.NewTicker(MilissegundosPorQuadro * time.Millisecond)
	defer relogio.Stop()
	fim := time.Now().Add(quanto)
	for time.Now().Before(fim) {
		<-relogio.C
		_ = voz.WriteSample(media.Sample{
			Data:     quadroDeVoz,
			Duration: MilissegundosPorQuadro * time.Millisecond,
		})
	}
	close(parar)
	pressao.Wait()
	time.Sleep(300 * time.Millisecond)

	mu.Lock()
	defer mu.Unlock()

	if len(chegadas) < 10 {
		t.Fatalf("chegaram só %d pacotes de voz; a medição não vale", len(chegadas))
	}

	vaos := make([]time.Duration, 0, len(chegadas)-1)
	for i := 1; i < len(chegadas); i++ {
		vaos = append(vaos, chegadas[i].Sub(chegadas[i-1]))
	}
	sort.Slice(vaos, func(a, b int) bool { return vaos[a] < vaos[b] })

	return buracosNaVoz{
		maior:   vaos[len(vaos)-1],
		p95:     vaos[len(vaos)*95/100],
		quadros: len(chegadas),
	}
}

func TestAVozNaoEsperaAtrasDaTela(t *testing.T) {
	if testing.Short() {
		t.Skip("mede tempo real; pulado no modo curto")
	}

	const duracao = 5 * time.Second

	neutro := medirVozSobPressao(t, apiComPacer(t, func() gcc.Pacer { return gcc.NewNoOpPacer() }), duracao)
	t.Logf("pacer neutro (o que o Astra usa): %d pacotes · p95 %v · maior vão %v",
		neutro.quadros, neutro.p95.Round(time.Millisecond), neutro.maior.Round(time.Millisecond))

	if neutro.maior > 400*time.Millisecond {
		t.Errorf("a voz ficou %v sem chegar enquanto a tela martelava", neutro.maior)
	}
	if neutro.p95 > 60*time.Millisecond {
		t.Errorf("p95 do vão entre pacotes de voz = %v; o esperado é perto de 20ms", neutro.p95)
	}
}

func TestSondaDoPacerQueSeguraAVoz(t *testing.T) {
	if os.Getenv("ASTRA_MEDIR_PACER") == "" {
		t.Skip("defina ASTRA_MEDIR_PACER=1 — é o registro de por que o pacer padrão ficou de fora")
	}

	const duracao = 5 * time.Second

	neutro := medirVozSobPressao(t, apiComPacer(t, func() gcc.Pacer { return gcc.NewNoOpPacer() }), duracao)
	balde := medirVozSobPressao(t, apiComPacer(t, func() gcc.Pacer { return gcc.NewLeakyBucketPacer(bandaInicialDoGcc) }), duracao)
	neutroDeNovo := medirVozSobPressao(t, apiComPacer(t, func() gcc.Pacer { return gcc.NewNoOpPacer() }), duracao)

	linha := func(nome string, b buracosNaVoz) {
		t.Logf("  %-28s %4d pacotes · p95 %6v · maior vão %6v",
			nome, b.quadros, b.p95.Round(time.Millisecond), b.maior.Round(time.Millisecond))
	}

	t.Logf("")
	t.Logf("VOZ ENQUANTO A TELA MARTELA 60 KB a 30/s:")
	linha("pacer neutro", neutro)
	linha("balde furado (padrão do gcc)", balde)
	linha("pacer neutro de novo", neutroDeNovo)

	if balde.maior > neutro.maior*2 {
		t.Logf("")
		t.Logf("VEREDITO: o balde furado segura a voz atrás da tela — é fila FIFO única")
		t.Logf("          para todos os SSRCs. Por isso o Astra usa NewNoOpPacer.")
	} else {
		t.Logf("")
		t.Logf("VEREDITO: nesta máquina os dois se comportaram igual. Não conclua que o")
		t.Logf("          balde é inofensivo: em rede real, com o alvo abaixo do que a")
		t.Logf("          tela produz, a fila é que cresce — aqui não há gargalo.")
	}
}
