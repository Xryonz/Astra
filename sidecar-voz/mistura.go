package main

import (
	"sync"
	"time"
)

const quadrosDeFolga = 3

type Misturador struct {
	mu    sync.Mutex
	vozes map[string]*vozRecebida

	soma []int32
}

type vozRecebida struct {
	fila [][]int16

	ultimaEntrega time.Time
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
	v.ultimaEntrega = time.Now()

	if len(v.fila) >= quadrosDeFolga {

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
			}
			continue
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
