package main

import (
	"sync"
	"time"
)

const (
	folgaDeRajada = 3

	folgaMaxima = 10

	paradaQueNaoEJitter = 200 * time.Millisecond

	pulosParaAcalmar = 500
)

type Misturador struct {
	mu    sync.Mutex
	vozes map[string]*vozRecebida

	soma []int32
}

type vozRecebida struct {
	fila [][]int16

	ultimaEntrega time.Time

	alvo     int
	enchendo bool
	faltouEm time.Time
	calmos   int
	minFila  int
}

const silencioAteEsquecer = 3 * time.Second

func NovoMisturador() *Misturador {
	return &Misturador{vozes: make(map[string]*vozRecebida)}
}

func (m *Misturador) Entregar(id string, pcm []int16) {
	m.mu.Lock()
	defer m.mu.Unlock()

	v, ok := m.vozes[id]
	if !ok {
		v = &vozRecebida{}
		m.vozes[id] = v
	}

	agora := time.Now()
	v.ultimaEntrega = agora

	if pcm == nil {
		v.faltouEm = time.Time{}
		return
	}

	if !v.faltouEm.IsZero() {
		if agora.Sub(v.faltouEm) <= paradaQueNaoEJitter && v.alvo < folgaMaxima {
			v.alvo++
			v.calmos = 0
		}
		v.faltouEm = time.Time{}
	}

	for len(v.fila) >= v.alvo+folgaDeRajada {
		copy(v.fila, v.fila[1:])
		v.fila = v.fila[:len(v.fila)-1]
	}

	guardado := make([]int16, len(pcm))
	copy(guardado, pcm)
	v.fila = append(v.fila, guardado)
}

func (m *Misturador) Puxar(destino []int16) int {
	m.mu.Lock()
	defer m.mu.Unlock()

	if cap(m.soma) < len(destino) {
		m.soma = make([]int32, len(destino))
	}
	soma := m.soma[:len(destino)]
	for i := range soma {
		soma[i] = 0
	}
	vozes := 0

	agora := time.Now()

	for id, v := range m.vozes {
		if len(v.fila) == 0 {
			if agora.Sub(v.ultimaEntrega) > silencioAteEsquecer {
				delete(m.vozes, id)
				continue
			}
			if !v.enchendo {
				v.enchendo = true
				v.faltouEm = agora
			}
			continue
		}

		if v.enchendo {
			if len(v.fila) <= v.alvo {
				continue
			}
			v.enchendo = false
		}

		if v.calmos == 0 || len(v.fila) < v.minFila {
			v.minFila = len(v.fila)
		}
		v.calmos++
		if v.calmos >= pulosParaAcalmar {
			v.calmos = 0
			if v.minFila > 1 && v.alvo > 0 {
				v.alvo--
				if len(v.fila) > v.alvo+1 {
					copy(v.fila, v.fila[1:])
					v.fila = v.fila[:len(v.fila)-1]
				}
			}
		}

		quadro := v.fila[0]
		copy(v.fila, v.fila[1:])
		v.fila = v.fila[:len(v.fila)-1]
		vozes++
		for i := 0; i < len(destino) && i < len(quadro); i++ {
			soma[i] += int32(quadro[i])
		}
	}

	if vozes == 0 {
		for i := range destino {
			destino[i] = 0
		}
		return 0
	}

	for i := range destino {
		destino[i] = cortar(soma[i])
	}
	return vozes
}

func cortar(v int32) int16 {
	const teto = 32767
	const piso = -32768
	if v > teto {
		return teto
	}
	if v < piso {
		return piso
	}
	return int16(v)
}

func (m *Misturador) Esquecer(id string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.vozes, id)
}
