package main

// QUEM ESTÁ FALANDO — detectado aqui dentro, porque não há mais ninguém para
// detectar.
//
// Numa malha não existe servidor no caminho da mídia, e portanto não existe
// servidor para dizer "fulano está falando". Essa informação tem que sair de onde o
// áudio passa, e o áudio já passa por aqui decodificado — o custo é uma soma de
// quadrados sobre 960 amostras, que é ruído perto de decodificar Opus.
//
// O QUE VAI PELA PONTE É A TRANSIÇÃO, NUNCA O NÍVEL.
//
// Mandar o nível a cada quadro seriam 50 mensagens por segundo POR PESSOA — numa
// call de seis, 300 linhas de JSON por segundo só para acender e apagar um círculo,
// e cada uma acordando o lado Kotlin para recompor a tela. A tela só precisa saber
// quando começa e quando para: um punhado de mensagens por minuto.

import (
	"math"
	"time"
)

// Acima disto é voz. Mesmo valor que o motor antigo usava, e ele foi calibrado
// contra microfone de verdade nesta casa — não vale a pena redescobrir.
const limiarDeFala = 0.015

// Segura o "falando" um instante depois do nível cair.
//
// Sem isso, a pausa entre duas palavras apagaria o círculo, e um indicador que
// pisca a cada sílaba informa menos do que indicador nenhum: o olho aprende a
// ignorá-lo.
const esperaAntesDeCalar = 400 * time.Millisecond

// DetectorDeFala guarda o estado de UMA pessoa. Não é seguro para uso concorrente,
// e não precisa ser: cada um vive dentro da goroutine que lê aquela faixa.
type DetectorDeFala struct {
	falando   bool
	ultimaVoz time.Time
}

// Alimentar entrega um quadro e devolve `true` quando o estado MUDOU.
//
// Quadro vazio é silêncio válido, e é assim que o chamador avança o relógio quando
// não chegou pacote nenhum — ver o prazo de leitura em `Par.receber`.
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

// Calar apaga o indicador na marra: a pessoa ficou muda, ou a faixa dela morreu.
// Devolve `true` se havia mesmo o que apagar.
func (d *DetectorDeFala) Calar() bool {
	if !d.falando {
		return false
	}
	d.falando = false
	return true
}

func (d *DetectorDeFala) Falando() bool { return d.falando }

// nivelDe é a raiz da média dos quadrados, normalizada em 0..1.
//
// Média dos quadrados e NÃO pico, de propósito: o pico dispara com qualquer estalo
// de teclado ou batida na mesa. O que separa voz de estalo é energia sustentada, e
// é isso que a média mede.
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

// marcaDeFala traduz o booleano para o campo curto da ponte. Existe para os dois
// lados (aqui e o Kotlin) não discordarem sobre o que é "ligado".
func marcaDeFala(falando bool) string {
	if falando {
		return "1"
	}
	return "0"
}
