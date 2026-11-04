package main

import (
	"runtime"
	"time"
)

func AlvoDeSaida(largura, altura, pessoasNaSala int) (int, int) {
	teto := 1080
	if pessoasNaSala >= 3 {
		teto = 720
	}
	if altura <= teto {
		return largura, altura
	}

	l := largura * teto / altura
	return l &^ 1, teto &^ 1
}

type MedidaDaTransmissao struct {
	Quadros    int
	Pedacos    int
	Bytes      int
	Largura    int
	Altura     int
	Fps        int
	Compressor string
	Formato    string
	Assincrono bool
	Duracao    time.Duration

	TempoNoCano time.Duration

	Custos Custos

	Processador time.Duration
}

func (m MedidaDaTransmissao) Nucleos() float64 {
	if m.Duracao <= 0 {
		return 0
	}
	return float64(m.Processador) / float64(m.Duracao)
}

func (m MedidaDaTransmissao) CustoPorQuadro() time.Duration {
	if m.Quadros == 0 {
		return 0
	}
	return m.TempoNoCano / time.Duration(m.Quadros)
}

func (m MedidaDaTransmissao) Folga() float64 {
	orcamento := time.Second / time.Duration(m.Fps)
	if m.Quadros == 0 || orcamento == 0 {
		return 0
	}
	return 1 - float64(m.CustoPorQuadro())/float64(orcamento)
}

func (m MedidaDaTransmissao) PorSegundo() float64 {
	if m.Duracao <= 0 {
		return 0
	}
	return float64(m.Quadros) / m.Duracao.Seconds()
}

func (m MedidaDaTransmissao) Kbps() float64 {
	if m.Duracao <= 0 {
		return 0
	}
	return float64(m.Bytes) * 8 / 1000 / m.Duracao.Seconds()
}

func MedirTransmissao(monitor int, duracao time.Duration, saidaL, saidaA, kbps int) (MedidaDaTransmissao, error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	var m MedidaDaTransmissao
	if err := abrirCOM(); err != nil {
		return m, err
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return m, err
	}
	defer fecharMF()

	tela, err := AbrirTela(monitor)
	if err != nil {
		return m, err
	}
	defer tela.Fechar()

	c, err := AbrirCompressor(tela, saidaL, saidaA, 0, kbps)
	if err != nil {
		return m, err
	}
	defer c.Fechar()

	m.Largura, m.Altura = c.saidaL, c.saidaA
	m.Fps = c.fps
	m.Compressor = c.Nome
	m.Formato = c.Formato
	m.Assincrono = c.Assincrono

	ritmo := NovoRitmo(c.fps)
	cpuAntes := TempoDeProcessador()
	comeco := time.Now()
	fim := comeco.Add(duracao)
	for time.Now().Before(fim) {

		ritmo.Esperar()

		textura, e := tela.ProximoQuadro(100)
		if e != nil {
			if _, perdeu := e.(ErroDeAcessoPerdido); perdeu {
				if err := tela.Remontar(monitor); err != nil {
					return m, err
				}
				continue
			}
			return m, e
		}
		if textura == 0 {
			continue
		}

		antes := time.Now()
		err := c.Comprimir(textura, time.Since(comeco), tela.SoltarQuadro, func(nal []byte, _ time.Duration) {
			m.Pedacos++
			m.Bytes += len(nal)
		})
		m.TempoNoCano += time.Since(antes)
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			return m, err
		}
		m.Quadros++
	}
	m.Duracao = time.Since(comeco)
	m.Custos = c.Custos
	m.Processador = TempoDeProcessador() - cpuAntes
	return m, nil
}
