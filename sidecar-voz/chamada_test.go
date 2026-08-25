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

func TestChamadaCompletaEntreDoisPares(t *testing.T) {
	abrirParaTeste(t)

	ctx, cancelar := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancelar()

	canoA, escreveA := io.Pipe()
	canoB, escreveB := io.Pipe()

	misturadorB := NovoMisturador()

	faixaA, err := webrtc.NewTrackLocalStaticSample(CapacidadeOpus, "audio", "teste-mic")
	if err != nil {
		t.Fatalf("criar faixa: %v", err)
	}

	telaA, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "teste-tela")
	if err != nil {
		t.Fatalf("criar faixa de tela: %v", err)
	}

	config := webrtc.Configuration{}

	parA, err := NovoPar("B", config, faixaA, telaA, nil, nil, NewEscritor(escreveA))
	if err != nil {
		t.Fatalf("criar par A: %v", err)
	}
	defer parA.Fechar()

	parB, err := NovoPar("A", config, nil, nil, misturadorB, nil, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar par B: %v", err)
	}
	defer parB.Fechar()

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
