package main

import (
	"fmt"
	"os"
	"runtime"
	"time"

	"github.com/pion/rtcp"
	"github.com/pion/rtp/codecs"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media/samplebuilder"
)

const pacotesQueEsperam = 512

const silencioQueEncerra = 5 * time.Second

const conferirOSilencio = 500 * time.Millisecond

func (p *Par) receberTela(remota *webrtc.TrackRemote) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		fmt.Fprintf(os.Stderr, "COM para a tela de %s: %v\n", p.id, err)
		return
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		fmt.Fprintf(os.Stderr, "Media Foundation para a tela de %s: %v\n", p.id, err)
		return
	}
	defer fecharMF()

	noAr := true
	p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "faixa"})
	defer func() {
		if noAr {
			p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "0"})
		}
	}()

	pedirImagem := func() {
		err := p.pc.WriteRTCP([]rtcp.Packet{
			&rtcp.PictureLossIndication{MediaSSRC: uint32(remota.SSRC())},
		})
		if err != nil {
			fmt.Fprintf(os.Stderr, "não consegui pedir imagem a %s: %v\n", p.id, err)
		}
	}

	var d *Descompressor
	var remontador *samplebuilder.SampleBuilder
	defer func() {
		if d != nil {
			d.Fechar()
		}
	}()

	desistiu := false

	jaTemImagem := false
	var ultimoPedido time.Time

	comeco := time.Now()
	var quadros, pacotes, amostras, ignorados int
	relatorio := time.Now()

	ultimoPacote := time.Now()

	for {

		_ = remota.SetReadDeadline(time.Now().Add(conferirOSilencio))
		pacote, _, err := remota.ReadRTP()
		if err != nil {
			if !esperaEstourada(err) {
				return
			}
			if noAr && time.Since(ultimoPacote) >= silencioQueEncerra {

				noAr = false
				if d != nil {
					d.Fechar()
					d, remontador = nil, nil
					jaTemImagem = false
				}
				p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "0"})
			}
			continue
		}
		ultimoPacote = time.Now()
		if !noAr {

			noAr = true
			p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "faixa"})
		}
		pacotes++

		switch {
		case !p.queremVer():

			if d != nil {
				d.Fechar()
				d, remontador = nil, nil
				jaTemImagem = false
			}
			desistiu = false
			ignorados++

		case d == nil && desistiu:

			ignorados++

		default:
			if d == nil {

				novo, err := AbrirDescompressor(1280, 720)
				if err != nil {
					fmt.Fprintf(os.Stderr, "sem descompressor para a tela de %s: %v\n", p.id, err)
					p.saida.Manda(Evento{Ev: EvErro, Par: p.id, Msg: "não consigo mostrar a tela: " + err.Error()})

					desistiu = true
					ignorados++
					continue
				}
				d = novo

				remontador = samplebuilder.New(pacotesQueEsperam, &codecs.H264Packet{}, 90000)
				p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: d.Nome})
				pedirImagem()
				ultimoPedido = time.Now()
			}

			remontador.Push(pacote)

			if !jaTemImagem && time.Since(ultimoPedido) >= time.Second {
				pedirImagem()
				ultimoPedido = time.Now()
			}

			for {
				amostra := remontador.Pop()
				if amostra == nil {
					break
				}
				amostras++

				err := d.Decodificar(amostra.Data, time.Since(comeco), func(q Quadro) {
					quadros++
					jaTemImagem = true
					p.entrega.Mandar(p.id, q)
				})
				if err != nil {
					fmt.Fprintf(os.Stderr, "quadro estragado de %s: %v\n", p.id, err)
				}
			}
		}

		if desde := time.Since(relatorio); desde >= time.Second {
			msg := fmt.Sprintf("%d fps · %d pacotes · %d remontados",
				int(float64(quadros)/desde.Seconds()), pacotes, amostras)
			if ignorados > 0 {
				msg += fmt.Sprintf(" · %d descartados sem ninguém olhando", ignorados)
			}
			p.saida.Manda(Evento{Ev: EvTelaDeOutro, Par: p.id, V: "1", Tipo: "ritmo", Msg: msg})
			relatorio = time.Now()
			quadros, pacotes, amostras, ignorados = 0, 0, 0, 0
		}
	}
}

func (p *Par) queremVer() bool {
	return p.querVer == nil || p.querVer()
}
