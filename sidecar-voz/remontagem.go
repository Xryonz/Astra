package main

const (
	janelaDeReordem = 2

	saltoQueRessincroniza = 100
)

type RemontadorDeVoz struct {
	dec *Decodificador

	esperado uint16
	comecou  bool

	guardados map[uint16][]byte

	pcm      []int16
	umQuadro []int16

	TapadosComVizinho int
	TapadosNoEscuro   int
	Reordenados       int
	Atrasados         int
	Ressincronizados  int
}

func NovoRemontadorDeVoz(dec *Decodificador) *RemontadorDeVoz {
	return &RemontadorDeVoz{
		dec:       dec,
		guardados: make(map[uint16][]byte, janelaDeReordem+1),
		pcm:       make([]int16, AmostrasPorQuadro*6),
		umQuadro:  make([]int16, AmostrasPorQuadro),
	}
}

func ehSilencio(carga []byte) bool { return len(carga) <= 2 }

func (r *RemontadorDeVoz) Entregar(seq uint16, carga []byte, emitir func([]int16)) {
	if !r.comecou {
		r.esperado, r.comecou = seq, true
	}

	switch dist := int16(seq - r.esperado); {
	case dist == 0:
		r.tocar(carga, emitir)
		r.esperado++
		r.escoarSeguidos(emitir)

	case dist > 0 && dist < saltoQueRessincroniza:
		if _, jaTem := r.guardados[seq]; !jaTem {
			r.guardados[seq] = append([]byte(nil), carga...)
			r.Reordenados++
		}
		for int16(seq-r.esperado) >= janelaDeReordem {
			r.desistirDoEsperado(emitir)
			r.escoarSeguidos(emitir)
		}

	case dist < 0 && dist > -saltoQueRessincroniza:
		r.Atrasados++

	default:
		r.Ressincronizados++
		clear(r.guardados)
		r.esperado = seq
		r.tocar(carga, emitir)
		r.esperado++
	}
}

func (r *RemontadorDeVoz) Escoar(emitir func([]int16)) {
	for len(r.guardados) > 0 {
		r.desistirDoEsperado(emitir)
		r.escoarSeguidos(emitir)
	}
}

func (r *RemontadorDeVoz) desistirDoEsperado(emitir func([]int16)) {
	if seguinte, tem := r.guardados[r.esperado+1]; tem && !ehSilencio(seguinte) {
		if n, err := r.dec.Decodificar(seguinte, r.umQuadro, true); err == nil && n > 0 {
			r.TapadosComVizinho++
			r.esperado++
			emitir(r.umQuadro[:n])
			return
		}
	}

	n, err := r.dec.Decodificar(nil, r.umQuadro, false)
	r.esperado++
	if err != nil || n <= 0 {
		emitir(nil)
		return
	}
	r.TapadosNoEscuro++
	emitir(r.umQuadro[:n])
}

func (r *RemontadorDeVoz) escoarSeguidos(emitir func([]int16)) {
	for {
		carga, tem := r.guardados[r.esperado]
		if !tem {
			return
		}
		delete(r.guardados, r.esperado)
		r.tocar(carga, emitir)
		r.esperado++
	}
}

func (r *RemontadorDeVoz) tocar(carga []byte, emitir func([]int16)) {
	if ehSilencio(carga) {
		emitir(nil)
		return
	}
	n, err := r.dec.Decodificar(carga, r.pcm, false)
	if err != nil || n <= 0 {
		emitir(nil)
		return
	}
	emitir(r.pcm[:n])
}

func (r *RemontadorDeVoz) Zerar() {
	r.TapadosComVizinho, r.TapadosNoEscuro = 0, 0
	r.Reordenados, r.Atrasados, r.Ressincronizados = 0, 0, 0
}

func (r *RemontadorDeVoz) Houve() bool {
	return r.TapadosComVizinho+r.TapadosNoEscuro+
		r.Reordenados+r.Atrasados+r.Ressincronizados > 0
}
