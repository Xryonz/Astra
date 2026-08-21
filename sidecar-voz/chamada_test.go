package main

import (
	"bufio"
	"context"
	"encoding/json"
	"io"
	"math"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

// UMA CHAMADA INTEIRA DENTRO DE UM PROCESSO.
//
// Este é o teste que importa. Os outros provam peças; este prova o CAMINHO: dois
// pares de verdade fazendo o aperto de mão pela mesma sinalização que o Astra usa,
// voz codificada em Opus atravessando o transporte do Pion, e chegando decodificada
// no misturador do outro lado.
//
// Não precisa de microfone nem de alto-falante — entra um tom sintetizado no lugar
// do microfone e confere-se o que sai da mistura. Por isso roda em qualquer
// máquina, inclusive sem placa de som.
func TestChamadaCompletaEntreDoisPares(t *testing.T) {
	abrirParaTeste(t) // precisa do Opus

	ctx, cancelar := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancelar()

	// Cada lado escreve os envelopes num cano; um roteador lê e entrega ao outro.
	// É exatamente o papel que o servidor do Astra faz na vida real, reduzido ao
	// essencial.
	canoA, escreveA := io.Pipe()
	canoB, escreveB := io.Pipe()

	misturadorB := NovoMisturador()

	faixaA, err := webrtc.NewTrackLocalStaticSample(CapacidadeOpus, "audio", "teste-mic")
	if err != nil {
		t.Fatalf("criar faixa: %v", err)
	}

	// A TELA ENTRA SÓ DE UM LADO, de propósito: é o arranjo real de quem compartilha —
	// um transmite, o outro só assiste. Exercita a negociação assimétrica, que é onde
	// declarar vídeo de menos (ou de mais) quebra o aperto de mão.
	telaA, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "teste-tela")
	if err != nil {
		t.Fatalf("criar faixa de tela: %v", err)
	}

	config := webrtc.Configuration{}

	parA, err := NovoPar("B", config, faixaA, telaA, nil, NewEscritor(escreveA))
	if err != nil {
		t.Fatalf("criar par A: %v", err)
	}
	defer parA.Fechar()

	parB, err := NovoPar("A", config, nil, nil, misturadorB, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar par B: %v", err)
	}
	defer parB.Fechar()

	// O roteador: tudo que um lado emite, o outro recebe.
	rotear := func(de io.Reader, para *Par) {
		linhas := bufio.NewScanner(de)
		linhas.Buffer(make([]byte, 0, 64*1024), 1024*1024)
		for linhas.Scan() {
			var ev Evento
			if err := json.Unmarshal(linhas.Bytes(), &ev); err != nil {
				continue
			}
			if ev.Ev != EvSinal {
				continue
			}
			if err := para.Receber(ctx, ev.Tipo, ev.Dados); err != nil {
				t.Logf("entregar %s: %v", ev.Tipo, err)
			}
		}
	}
	go rotear(canoA, parB)
	go rotear(canoB, parA)

	if err := parA.Oferecer(ctx); err != nil {
		t.Fatalf("oferecer: %v", err)
	}

	// Fala um tom de 440 Hz até a mistura do outro lado ter voz — ou até o tempo
	// acabar. Empurrar durante a conexão é proposital: é assim que acontece de
	// verdade, com a pessoa já falando enquanto o ICE ainda fecha.
	cod, err := NovoCodificador(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		t.Fatalf("codificador: %v", err)
	}
	defer cod.Fechar()

	quadro := make([]int16, AmostrasPorQuadro)
	pacote := make([]byte, 4000)
	fase := 0.0
	passo := 2 * math.Pi * 440 / TaxaDeAmostragem

	saida := make([]int16, AmostrasPorQuadro)
	limite := time.After(15 * time.Second)
	tique := time.NewTicker(MilissegundosPorQuadro * time.Millisecond)
	defer tique.Stop()

	for {
		select {
		case <-limite:
			t.Fatal("nenhuma voz chegou ao outro lado em 15s")
		case <-tique.C:
		}

		for i := range quadro {
			quadro[i] = int16(math.Sin(fase) * 8000)
			fase += passo
		}
		n, err := cod.Codificar(quadro, pacote)
		if err != nil {
			t.Fatalf("codificar: %v", err)
		}
		_ = faixaA.WriteSample(media.Sample{
			Data:     pacote[:n],
			Duration: MilissegundosPorQuadro * time.Millisecond,
		})

		if vozes := misturadorB.Puxar(saida); vozes > 0 {
			var energia float64
			for _, v := range saida {
				energia += float64(v) * float64(v)
			}
			rms := math.Sqrt(energia / float64(len(saida)))
			t.Logf("voz atravessou: %d voz(es) na mistura, rms %.0f", vozes, rms)
			if rms < 500 {
				t.Fatalf("chegou quase silêncio (rms %.0f) — algo se perdeu no caminho", rms)
			}
			return
		}
	}
}
