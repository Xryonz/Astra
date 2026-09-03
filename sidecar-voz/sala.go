package main

import (
	"fmt"
	"os"
	"sync"

	"github.com/go-logr/logr"
	protoLogger "github.com/livekit/protocol/logger"
	"github.com/livekit/protocol/livekit"
	lksdk "github.com/livekit/server-sdk-go/v2"
	"github.com/pion/interceptor"
	"github.com/pion/interceptor/pkg/cc"
	"github.com/pion/interceptor/pkg/gcc"
	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

type Sala struct {
	saida      *Escritor
	misturador *Misturador
	entrega    *EntregaDeQuadros
	emissor    *Emissor

	mu       sync.Mutex
	sala     *lksdk.Room
	mic      *lksdk.LocalTrack
	tela     *lksdk.LocalTrack
	pubTela  *lksdk.LocalTrackPublication
	telasDos map[string]*lksdk.RemoteTrackPublication
	noPalco  string
	banda    cc.BandwidthEstimator
}

const (
	bandaInicialDoGcc = 1_500_000
	bandaMaximaDoGcc  = 12_000_000
)

func calarOSdk() {
	lksdk.SetLogger(protoLogger.LogRLogger(logr.Discard()))
}

func NovaSala(saida *Escritor, mist *Misturador, entrega *EntregaDeQuadros) *Sala {
	calarOSdk()
	return &Sala{
		saida:      saida,
		misturador: mist,
		entrega:    entrega,
		telasDos:   make(map[string]*lksdk.RemoteTrackPublication),
	}
}

func (s *Sala) Entrar(url, token string) error {
	if url == "" || token == "" {
		return fmt.Errorf("sala sem endereço ou credencial")
	}
	s.Sair()

	mic, err := lksdk.NewLocalTrack(CapacidadeOpus,
		lksdk.WithRTCPHandler(medidorDeCaminho("voz", TaxaDeAmostragem, s.relatarCaminho("voz"))))
	if err != nil {
		return fmt.Errorf("criar faixa de microfone: %w", err)
	}

	retorno := &lksdk.RoomCallback{
		OnDisconnected: func() {
			s.saida.Manda(Evento{Ev: EvEstado, V: "disconnected"})
		},
		ParticipantCallback: lksdk.ParticipantCallback{
			OnTrackSubscribed:   s.aoAssinar,
			OnTrackUnsubscribed: s.aoDesassinar,
			OnTrackPublished:    s.aoPublicarem,
		},
		OnParticipantConnected: func(rp *lksdk.RemoteParticipant) {
			s.saida.Manda(Evento{Ev: EvEstado, Par: rp.Identity(), V: "connected"})
		},
		OnParticipantDisconnected: func(rp *lksdk.RemoteParticipant) {
			quem := rp.Identity()
			s.esquecer(quem)
			s.saida.Manda(Evento{Ev: EvEstado, Par: quem, V: "closed"})
		},
	}

	medidor, err := cc.NewInterceptor(func() (cc.BandwidthEstimator, error) {
		return gcc.NewSendSideBWE(
			gcc.SendSideBWEPacer(gcc.NewNoOpPacer()),
			gcc.SendSideBWEInitialBitrate(bandaInicialDoGcc),
			gcc.SendSideBWEMinBitrate(bandaMinima*1000),
			gcc.SendSideBWEMaxBitrate(bandaMaximaDoGcc),
		)
	})
	if err != nil {
		return fmt.Errorf("medir o congestionamento: %w", err)
	}
	medidor.OnNewPeerConnection(func(_ string, e cc.BandwidthEstimator) {
		s.mu.Lock()
		s.banda = e
		s.mu.Unlock()
	})

	quarto, err := lksdk.ConnectToRoomWithToken(url, token, retorno,
		lksdk.WithInterceptors([]interceptor.Factory{medidor}),
		lksdk.WithIncludeDefaultInterceptors(true),
	)
	if err != nil {
		return fmt.Errorf("entrar na sala: %w", err)
	}

	if _, err := quarto.LocalParticipant.PublishTrack(mic, &lksdk.TrackPublicationOptions{
		Name:   "astra-microfone",
		Source: livekit.TrackSource_MICROPHONE,
	}); err != nil {
		quarto.Disconnect()
		return fmt.Errorf("publicar microfone: %w", err)
	}

	s.mu.Lock()
	s.sala = quarto
	s.mic = mic
	s.mu.Unlock()

	fmt.Fprintf(os.Stderr, "na sala %q como %q\n", quarto.Name(), quarto.LocalParticipant.Identity())
	s.saida.Manda(Evento{Ev: EvEstado, V: "connected"})

	for _, rp := range quarto.GetRemoteParticipants() {
		s.saida.Manda(Evento{Ev: EvEstado, Par: rp.Identity(), V: "connected"})
	}
	return nil
}

func (s *Sala) relatarCaminho(oQue string) func(LeituraDoCaminho) {
	return func(leitura LeituraDoCaminho) {
		s.saida.Manda(Evento{Ev: EvCaminho, Tipo: oQue, Caminho: &leitura})
	}
}

func (s *Sala) Sair() {
	s.mu.Lock()
	quarto := s.sala
	s.sala, s.mic, s.tela, s.pubTela = nil, nil, nil, nil
	s.banda = nil
	s.telasDos = make(map[string]*lksdk.RemoteTrackPublication)
	s.mu.Unlock()

	s.misturador.EsquecerGanhos()

	if quarto != nil {
		quarto.Disconnect()
	}
}

type vozDaSala struct{ s *Sala }

func (v vozDaSala) WriteSample(amostra media.Sample) error { return v.s.escreverVoz(amostra) }

func (s *Sala) FaixaDeVoz() FaixaDeVoz { return vozDaSala{s} }

func (s *Sala) escreverVoz(amostra media.Sample) error {
	s.mu.Lock()
	faixa := s.mic
	s.mu.Unlock()
	if faixa == nil {
		return nil
	}
	return faixa.WriteSample(amostra, nil)
}

func (s *Sala) Escrever(amostra media.Sample) (int, error) {
	s.mu.Lock()
	faixa := s.tela
	s.mu.Unlock()
	if faixa == nil {
		return 0, nil
	}
	if err := faixa.WriteSample(amostra, nil); err != nil {
		return 0, err
	}
	return 1, nil
}

func (s *Sala) Contar() (assistindo, total int) {
	s.mu.Lock()
	quarto := s.sala
	publicada := s.tela != nil
	s.mu.Unlock()
	if quarto == nil || !publicada {
		return 0, 0
	}

	n := len(quarto.GetRemoteParticipants())
	return n, n
}

func (s *Sala) PublicarTela(largura, altura int) error {
	s.mu.Lock()
	quarto := s.sala
	jaTem := s.tela != nil
	s.mu.Unlock()
	if quarto == nil {
		return fmt.Errorf("sem sala para transmitir")
	}
	if jaTem {
		return nil
	}

	ritmo := medidorDeCaminho("tela", 90000, s.relatarCaminho("tela"))
	faixa, err := lksdk.NewLocalTrack(CapacidadeH264, lksdk.WithRTCPHandler(func(p rtcp.Packet) {
		ritmo(p)
		s.aoChegarRtcp(p)
	}))
	if err != nil {
		return fmt.Errorf("criar faixa de tela: %w", err)
	}

	pub, err := quarto.LocalParticipant.PublishTrack(faixa, &lksdk.TrackPublicationOptions{
		Name:        "astra-tela",
		Source:      livekit.TrackSource_SCREEN_SHARE,
		VideoWidth:  largura,
		VideoHeight: altura,
	})
	if err != nil {
		return fmt.Errorf("publicar tela: %w", err)
	}

	s.mu.Lock()
	s.tela, s.pubTela = faixa, pub
	s.mu.Unlock()
	return nil
}

func (s *Sala) PararTela() {
	s.mu.Lock()
	quarto, pub := s.sala, s.pubTela
	s.tela, s.pubTela = nil, nil
	s.mu.Unlock()

	if quarto == nil || pub == nil {
		return
	}
	if err := quarto.LocalParticipant.UnpublishTrack(pub.SID()); err != nil {
		fmt.Fprintf(os.Stderr, "despublicar a tela: %v\n", err)
	}
}

func (s *Sala) aoChegarRtcp(pacote rtcp.Packet) {
	if s.emissor == nil {
		return
	}

	s.mu.Lock()
	estimador := s.banda
	s.mu.Unlock()
	if estimador != nil {
		s.emissor.BandaRelatada(salaComoPar, estimador.GetTargetBitrate()/1000)
	}

	switch recado := pacote.(type) {
	case *rtcp.PictureLossIndication, *rtcp.FullIntraRequest:
		s.emissor.PedirQuadroChave()

	case *rtcp.ReceiverReport:
		pior := 0.0
		for _, r := range recado.Reports {
			if f := float64(r.FractionLost) / 256; f > pior {
				pior = f
			}
		}
		s.emissor.PerdaRelatada(salaComoPar, pior)
	}
}

const salaComoPar = "sfu"

func (s *Sala) Assistir(quem string) {
	s.mu.Lock()
	s.noPalco = quem
	pares := make(map[string]*lksdk.RemoteTrackPublication, len(s.telasDos))
	for id, pub := range s.telasDos {
		pares[id] = pub
	}
	s.mu.Unlock()

	for id, pub := range pares {
		s.ajustarAssinatura(id, pub, quem)
	}
}

func (s *Sala) ajustarAssinatura(quem string, pub *lksdk.RemoteTrackPublication, palco string) {
	quer := palco == "" || palco == quem
	if pub.IsSubscribed() == quer {
		return
	}
	if err := pub.SetSubscribed(quer); err != nil {
		fmt.Fprintf(os.Stderr, "assinatura da tela de %s: %v\n", quem, err)
	}
}

func (s *Sala) aoPublicarem(pub *lksdk.RemoteTrackPublication, rp *lksdk.RemoteParticipant) {
	if pub.Source() != livekit.TrackSource_SCREEN_SHARE {
		return
	}
	quem := rp.Identity()

	s.mu.Lock()
	s.telasDos[quem] = pub
	palco := s.noPalco
	s.mu.Unlock()

	s.ajustarAssinatura(quem, pub, palco)
}

func (s *Sala) aoAssinar(
	faixa *webrtc.TrackRemote, pub *lksdk.RemoteTrackPublication, rp *lksdk.RemoteParticipant,
) {
	quem := rp.Identity()
	ouvinte := &Ouvinte{
		id:         quem,
		saida:      s.saida,
		misturador: s.misturador,
		entrega:    s.entrega,
		querVer:    func() bool { return s.assistindo(quem) },
		pedirChave: func(ssrc webrtc.SSRC) { rp.WritePLI(ssrc) },
	}

	if faixa.Kind() == webrtc.RTPCodecTypeVideo {
		s.mu.Lock()
		s.telasDos[quem] = pub
		s.mu.Unlock()
		go ouvinte.receberTela(faixa)
		return
	}
	go ouvinte.receber(faixa)
}

func (s *Sala) aoDesassinar(
	_ *webrtc.TrackRemote, pub *lksdk.RemoteTrackPublication, rp *lksdk.RemoteParticipant,
) {
	if pub.Kind() != lksdk.TrackKindVideo {
		return
	}
	s.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: rp.Identity(), V: "0"})
}

func (s *Sala) assistindo(quem string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.noPalco == "" || s.noPalco == quem
}

func (s *Sala) esquecer(quem string) {
	s.mu.Lock()
	delete(s.telasDos, quem)
	s.mu.Unlock()

	if s.misturador != nil {
		s.misturador.Esquecer(quem)
	}
	if s.emissor != nil {
		s.emissor.EsquecerPar(quem)
	}
	s.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: quem, V: "0"})
}

func (s *Sala) Fechar() { s.Sair() }
