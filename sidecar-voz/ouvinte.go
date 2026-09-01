package main

import (
	"errors"
	"fmt"
	"net"
	"os"
	"time"

	"github.com/pion/webrtc/v4"
)

type Ouvinte struct {
	id         string
	saida      *Escritor
	misturador *Misturador
	entrega    *EntregaDeQuadros

	querVer func() bool

	pedirChave func(webrtc.SSRC)
}

func (p *Ouvinte) queremVer() bool {
	return p.querVer == nil || p.querVer()
}

func (p *Ouvinte) avisarFala(falando bool) {
	p.saida.Manda(Evento{Ev: EvFala, Par: p.id, V: marcaDeFala(falando)})
}

func (p *Ouvinte) receber(remota *webrtc.TrackRemote) {
	dec, err := NovoDecodificador(TaxaDeAmostragem, CanaisDeVoz)
	if err != nil {
		fmt.Fprintf(os.Stderr, "decodificador de %s: %v\n", p.id, err)
		return
	}
	defer dec.Fechar()

	remontador := NovoRemontadorDeVoz(dec)

	var det DetectorDeFala

	defer func() {
		if det.Calar() {
			p.avisarFala(false)
		}
	}()

	tocar := func(pcm []int16) {
		if det.Alimentar(pcm, time.Now()) {
			p.avisarFala(det.Falando())
		}
		if p.misturador != nil {
			p.misturador.Entregar(p.id, pcm)
		}
	}

	relatorio := time.Now()
	for {

		_ = remota.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			if esperaEstourada(err) {
				remontador.Escoar(tocar)
				if det.Alimentar(nil, time.Now()) {
					p.avisarFala(det.Falando())
				}
				continue
			}
			return
		}

		remontador.Entregar(pacote.SequenceNumber, pacote.Payload, tocar)

		if time.Since(relatorio) >= time.Second {
			relatorio = time.Now()
			if remontador.Houve() {
				fmt.Fprintf(os.Stderr, "voz de %s: %d tapados com o vizinho · %d no escuro · %d reordenados · %d atrasados · %d ressincronizados\n",
					p.id, remontador.TapadosComVizinho, remontador.TapadosNoEscuro,
					remontador.Reordenados, remontador.Atrasados, remontador.Ressincronizados)
			}
			remontador.Zerar()
		}
	}
}

func esperaEstourada(err error) bool {
	var deRede net.Error
	return errors.As(err, &deRede) && deRede.Timeout()
}
