package main

import (
	"math"
	"time"
)

const limiarDeFala = 0.015

const esperaAntesDeCalar = 400 * time.Millisecond

type DetectorDeFala struct {
	falando   bool
	ultimaVoz time.Time
}

func (d *DetectorDeFala) Alimentar(pcm []int16, agora time.Time) bool {
	if nivelDe(pcm) >= limiarDeFala {
		d.ultimaVoz = agora
		if !d.falando {
			d.falando = true
			return true
		}
		return false
	}
	if d.falando && agora.Sub(d.ultimaVoz) > esperaAntesDeCalar {
		d.falando = false
		return true
	}
	return false
}

func (d *DetectorDeFala) Calar() bool {
	if !d.falando {
		return false
	}
	d.falando = false
	return true
}

func (d *DetectorDeFala) Falando() bool { return d.falando }

func nivelDe(pcm []int16) float64 {
	if len(pcm) == 0 {
		return 0
	}
	var soma float64
	for _, a := range pcm {
		v := float64(a)
		soma += v * v
	}
	return math.Sqrt(soma/float64(len(pcm))) / 32768
}

func marcaDeFala(falando bool) string {
	if falando {
		return "1"
	}
	return "0"
}
