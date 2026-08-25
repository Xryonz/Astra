package main

import (
	"sync"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

type assento struct {
	faixa   *webrtc.TrackLocalStaticSample
	assiste bool
}

type PlateiaDaTela struct {
	mu       sync.RWMutex
	assentos map[string]*assento

	escrita  sync.Mutex
	destinos []*webrtc.TrackLocalStaticSample
}

func NovaPlateia() *PlateiaDaTela {
	return &PlateiaDaTela{assentos: make(map[string]*assento)}
}

func (p *PlateiaDaTela) Entrar(par string) (*webrtc.TrackLocalStaticSample, error) {
	faixa, err := webrtc.NewTrackLocalStaticSample(CapacidadeH264, "video", "astra-tela")
	if err != nil {
		return nil, err
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	p.assentos[par] = &assento{faixa: faixa, assiste: true}
	return faixa, nil
}

func (p *PlateiaDaTela) Sair(par string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	delete(p.assentos, par)
}

func (p *PlateiaDaTela) Assiste(par string, quer bool) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if a, ok := p.assentos[par]; ok {
		a.assiste = quer
	}
}

func (p *PlateiaDaTela) Escrever(amostra media.Sample) (int, error) {
	p.escrita.Lock()
	defer p.escrita.Unlock()

	p.destinos = p.destinos[:0]
	p.mu.RLock()
	for _, a := range p.assentos {
		if a.assiste {
			p.destinos = append(p.destinos, a.faixa)
		}
	}
	p.mu.RUnlock()

	var falha error
	for _, faixa := range p.destinos {
		if err := faixa.WriteSample(amostra); err != nil && falha == nil {
			falha = err
		}
	}
	return len(p.destinos), falha
}

func (p *PlateiaDaTela) Contar() (assistindo, total int) {
	p.mu.RLock()
	defer p.mu.RUnlock()

	for _, a := range p.assentos {
		if a.assiste {
			assistindo++
		}
	}
	return assistindo, len(p.assentos)
}
