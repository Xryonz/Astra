package main

// SONDA DA ESTIMATIVA DE BANDA — o pion sabe dizer quanto a rede aguenta?
//
// A PERGUNTA POR TRÁS. Hoje a transmissão manda 2.500 kbps porque o preset diz 2.500
// kbps. Numa conexão que aguenta 800, o resultado não é imagem pior: é pacote perdido,
// enxurrada de retransmissão e imagem quebrada, com nada recuando. O Discord decide
// resolução, taxa e qualidade a partir de uma estimativa de banda; o Astra não tem
// estimativa nenhuma.
//
// O QUE JÁ EXISTE E O QUE FALTA, conferido no pion 4.2.18 e não suposto:
// `RegisterDefaultInterceptors` liga NACK, relatórios RTCP, cabeçalhos de simulcast,
// estatísticas e o REMETENTE de TWCC. Ou seja, o outro lado já manda os relatórios de
// chegada — e ninguém os consome. Estimador de banda: nenhum.
//
// A peça está em `pion/interceptor/pkg/gcc`, já baixada como dependência transitiva.
//
// A PERGUNTA QUE DECIDE SE VALE A PENA, e que documentação nenhuma responde para o
// nosso caso: ele SOBE a estimativa, ou só sabe descer? A diferença é tudo. Um
// estimador que só desce protege a conexão ruim mas nunca deixa a boa usar o que tem —
// e uma transmissão de tela passa a maior parte do tempo mandando MENOS do que poderia,
// porque tela parada não gera quadro. É a "região limitada pela aplicação": sem tráfego
// suficiente, o estimador não tem o que medir.
//
// Duas fases, e a segunda é a que responde:
//
//	1. oferece 1.000 kbps e observa a estimativa
//	2. oferece 6.000 kbps e observa de novo
//
// Se a estimativa da fase 2 subir muito acima da fase 1, ele sobe de verdade. Se ficar
// grudada no que oferecemos, ele só acompanha — e aí o desenho em produção tem de
// sondar a banda por conta própria.
//
// ============================================================================
// ESTADO: A SONDA NÃO CONSEGUIU LIGAR O GCC. Ela fica no repositório porque o que
// ela DESCOBRIU vale mais que o que ela não conseguiu — e porque a próxima pessoa
// que tentar precisa começar destes quatro fatos, não do zero.
//
//  1. SEM `ConfigureTWCCHeaderExtensionSender`, O PACER DESCARTA TODOS OS PACOTES.
//     `RegisterDefaultInterceptors` registra o REMETENTE de TWCC (quem recebe mídia
//     passa a mandar relatório), mas NÃO carimba a extensão nos pacotes que saem. O
//     pacer do GCC recusa qualquer pacote sem ela, e recusa num log que só aparece
//     se alguém estiver olhando: "failed to write packet: missing transport layer cc
//     header extension". Ligar o GCC sem esta linha em produção pararia a
//     transmissão INTEIRA, sem erro devolvido a ninguém.
//
//  2. OS DOIS LADOS PRECISAM DA MESMA API. Com o receptor na API padrão, a oferta
//     saía com `a=extmap:4 .../transport-wide-cc` e a resposta voltava SEM ela.
//     Quem estima é quem manda, mas só consegue se quem recebe souber carimbar a
//     chegada.
//
//  3. `ouvirPedidos` É CARGA MAIOR DO QUE O NOME DIZ. Em pion o RTCP só atravessa a
//     cadeia de interceptores quando alguém chama `ReadRTCP` no remetente. Aquela
//     goroutine, que existe para atender pedido de quadro-chave, é a mesma que vai
//     alimentar o controle de congestionamento.
//
//  4. E O QUE TRAVOU: com tudo acima ligado, ZERO pacotes RTP chegam do outro lado.
//     O pacer aceita e não entrega. Trinta ReceiverReport voltam (o transporte está
//     de pé), nenhum TransportLayerCC, e todas as estatísticas do estimador em zero.
//     Ou falta configurar o pacer, ou ele precisa de um empurrão que não achei.
//
// A CONCLUSÃO PRÁTICA: ligar o GCC do pion não é a troca de uma linha que eu estimei.
// O caminho barato que entrega a maior parte do valor está descrito em
// `ouvirPedidos` — os ReceiverReport JÁ chegam, um por segundo, com fração de perda
// dentro, e hoje são jogados fora.
// ============================================================================
//
//	go test -run SondaDaEstimativaDeBanda -v

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
	// ATRAS DO PROPRIO PORTAO: ela leva 30s e enche a saida de erro do pacer, que e
	// justamente o defeito que ela documenta. Suite limpa vale mais que sonda automatica.
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

	// O RECEPTOR USA A MESMA API, e isso não é preguiça de escrever outra: é o arranjo
	// REAL. Os dois lados de uma chamada do Astra são o Astra.
	//
	// E a simetria é OBRIGATÓRIA, o que esta sonda descobriu do jeito difícil. Com o
	// receptor na API padrão, a oferta saía com `a=extmap:4 .../transport-wide-cc` e a
	// resposta VOLTAVA SEM ELA — o receptor não aceitava a extensão, não gerava relatório
	// de chegada nenhum, e o estimador ficava devolvendo o valor inicial para sempre.
	// Trinta ReceiverReport de volta e zero TransportLayerCC.
	//
	// Quem estima é quem MANDA, mas só consegue estimar se quem RECEBE souber carimbar
	// a chegada.
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

	// LER O RTCP DE VOLTA É O QUE ALIMENTA O ESTIMADOR, e sem isso ele fica cego.
	//
	// Em pion o RTCP que chega só atravessa a cadeia de interceptores quando alguém
	// chama `ReadRTCP` no remetente. Sem esta goroutine os relatórios de chegada do
	// outro lado ficam parados no soquete: o estimador nunca vê perda nem atraso, e
	// devolve para sempre o valor inicial — que foi exatamente o que esta sonda mostrou
	// antes desta linha existir, com TODAS as estatísticas em zero.
	//
	// A produção já faz isto em `ouvirPedidos`, para atender pedido de quadro-chave. A
	// sonda descobriu que aquela goroutine é carga muito maior do que o nome sugere: é
	// ela que vai alimentar o controle de congestionamento também.
	// CONTA O QUE VOLTA, POR TIPO. "estimativa parada" tem dois diagnósticos opostos —
	// ele decidiu que aquele é o número certo, ou nunca recebeu nada para decidir. Só
	// contar os tipos de RTCP separa os dois.
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

	// LER O QUE CHEGA É OBRIGATÓRIO. Faixa que ninguém lê enche o buffer de recepção, e
	// aí a medida passa a ser do buffer e não da rede.
	// CONFERE NA REDE, e não no SDP. Negociar a extensão e CARIMBAR a extensão são coisas
	// diferentes, e a segunda é a que o outro lado precisa para carimbar a chegada.
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

	// ESPERAR A CONEXÃO E NÃO A FAIXA. `OnTrack` só dispara quando o primeiro RTP chega,
	// e o primeiro RTP só sai depois desta espera — esperar por ele aqui é um impasse em
	// que cada lado aguarda o outro. Custou uma rodada.
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
	// O SDP DIZ SE A EXTENSÃO FOI NEGOCIADA, e é a única coisa que separa "o receptor
	// não quis" de "o receptor nem soube que era para querer". Sem `transport-cc` nas
	// duas descrições, nenhum relatório de chegada é gerado — e o estimador fica
	// devolvendo o valor inicial para sempre, que foi o que esta sonda viu.
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

	// O estimador nasce junto com a conexão, então só existe depois do aperto de mãos.
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
		// Código de início Annex-B e cabeçalho de fatia não-IDR: sem isso o
		// empacotador de H.264 do pion não acha NAL nenhum e não manda nada — e a
		// sonda mediria o silêncio.
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
				// AS ENTRANHAS JUNTO COM O NÚMERO. "1000 kbps" parado não diz se ele
				// decidiu que 1000 é o certo ou se nunca recebeu realimentação nenhuma
				// — dois estados com o mesmo número e diagnósticos opostos. É a mesma
				// regra que já apontou o remontador e o teto do compressor: contar as
				// etapas, não só o resultado.
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

// apiComEstimativa monta a API do pion com o controle de congestionamento ligado.
//
// TROCAR `webrtc.NewPeerConnection` POR ISTO É A MUDANÇA INTEIRA em produção — e o
// detalhe que não pode ser esquecido é `RegisterDefaultInterceptors`: montar a API na
// mão SEM ele apagaria o NACK, os relatórios e o TWCC, que é o que a transmissão já usa
// hoje. Ganhar estimativa perdendo retransmissão seria troca ruim.
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

	// A LINHA QUE FALTAVA, e sem ela o GCC descarta TODOS os pacotes.
	//
	// O padrão do pion registra o REMETENTE de TWCC — quem RECEBE mídia passa a mandar
	// relatórios de chegada. Isso é o lado de cá do problema. O que faltava é a extensão
	// no cabeçalho dos pacotes que SAEM, sem a qual o outro lado não tem como numerar o
	// que chegou.
	//
	// O pacer do GCC recusa qualquer pacote sem ela — e recusa em silêncio, num log que
	// só aparece se alguém estiver olhando: "failed to write packet: missing transport
	// layer cc header extension". Em produção isso seria a transmissão inteira parando
	// de sair, com a estimativa de banda ligada e nenhum erro devolvido a ninguém.
	//
	// Foi esta sonda que pegou, na primeira execução.
	if err := webrtc.ConfigureTWCCHeaderExtensionSender(motor, registro); err != nil {
		t.Fatalf("registrar a extensão de cabeçalho do TWCC: %v", err)
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

	return webrtc.NewAPI(
		webrtc.WithMediaEngine(motor),
		webrtc.WithInterceptorRegistry(registro),
	), estimadores
}

// apertarMaos liga os dois lados SEM trickle: espera a coleta de candidatos terminar e
// troca a descrição já completa. Num teste é mais curto e não deixa corrida nenhuma.
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

// apiDoReceptor é a mesma API sem o estimador: extensão de cabeçalho e interceptores
// padrão. É o que todo par do Astra precisa ter para o OUTRO lado conseguir estimar.
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
