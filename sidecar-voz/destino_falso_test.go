package main

import (
	"sync"
	"testing"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

type destinoFalso struct {
	faixa   *webrtc.TrackLocalStaticSample
	comFina bool

	mu       sync.Mutex
	amostras []media.Sample
	finas    []media.Sample
}

func novoDestinoFalso(t *testing.T) *destinoFalso {
	t.Helper()
	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "astra-tela")
	if err != nil {
		t.Fatalf("criar faixa de teste: %v", err)
	}
	return &destinoFalso{faixa: faixa}
}

func (d *destinoFalso) Escrever(amostra media.Sample) (int, error) {
	d.mu.Lock()
	d.amostras = append(d.amostras, amostra)
	d.mu.Unlock()

	if d.faixa == nil {
		return 1, nil
	}
	if err := d.faixa.WriteSample(amostra); err != nil {
		return 0, err
	}
	return 1, nil
}

func (d *destinoFalso) Contar() (assistindo, total int) { return 1, 1 }

func (d *destinoFalso) TemCamadaFina() bool { return d.comFina }

func (d *destinoFalso) EscreverFina(amostra media.Sample) error {
	d.mu.Lock()
	d.finas = append(d.finas, amostra)
	d.mu.Unlock()
	return nil
}

func (d *destinoFalso) Quantas() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	return len(d.amostras)
}

func (d *destinoFalso) QuantasFinas() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	return len(d.finas)
}
