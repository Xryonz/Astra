package main

// QUANTO A REDE ESTÁ AGUENTANDO — decidido pela perda que o outro lado relata.
//
// O QUE ISTO CONSERTA. Até aqui a transmissão mandava o que o preset dizia e não recuava
// nunca. Numa conexão que não aguenta 2.500 kbps, o resultado não é imagem pior: é
// pacote perdido, retransmissão em cima de retransmissão, e imagem quebrada do outro
// lado — com a máquina de quem transmite achando que está tudo bem.
//
// O DADO JÁ CHEGA. Cada par devolve um `ReceiverReport` por segundo, e dentro dele vem a
// fração de pacotes perdidos. `ouvirPedidos` já lê esse RTCP para atender pedido de
// quadro-chave, e jogava o resto fora.
//
// POR QUE NÃO O GCC. O caminho canônico seria o controle de congestionamento do pion
// (`pkg/gcc`), que estima a banda por atraso E por perda e sabe SUBIR sozinho. Ele foi
// tentado e não subiu — ver `sonda_banda_test.go` para os quatro fatos medidos, incluindo
// o pior deles: ligado do jeito óbvio, ele descarta todos os pacotes e a transmissão
// para. Isto aqui cobre a metade que decide se a imagem quebra; a outra metade (usar
// banda que sobra) fica para quando o GCC funcionar.
//
// A HISTERESE É FORTE DE PROPÓSITO, e o motivo é o atuador. Mudar a banda exige REABRIR
// o compressor — medido em três rotas diferentes, ele só aceita a banda na abertura (ver
// `sonda_banda_ao_vivo_test.go`). Reabrir custa um quadro-chave e uns décimos sem
// imagem. Um controle nervoso ficaria reabrindo a cada oscilação e produziria mais
// engasgo do que a perda que está tentando corrigir.
//
// Daí a assimetria: desce depressa, sobe devagar. Errar para baixo custa nitidez; errar
// para cima custa a imagem inteira.

import (
	"sync"
	"time"
)

const (
	// Acima disto a rede está claramente recusando o que mandamos. Dez por cento é o
	// mesmo limiar que o controlador por perda do GCC usa, e não é coincidência: abaixo
	// disso a retransmissão dá conta sem que ninguém veja.
	perdaQueDoi = 0.10

	// Abaixo disto a rede está confortável. Dois por cento também vem do GCC — perda
	// residual existe em qualquer link e tratá-la como problema faria a banda nunca
	// subir de volta.
	perdaConfortavel = 0.02

	// Segundos seguidos antes de agir. A assimetria é o coração do controle: três
	// segundos para recuar (um pico isolado não conta, uma congestão de verdade sim) e
	// vinte para voltar a subir, porque subir cedo demais recria exatamente o
	// congestionamento do qual acabamos de sair.
	segundosParaRecuar = 3
	segundosParaSubir  = 20

	// O piso. Abaixo disto a imagem vira mosaico e não serve para o que
	// compartilhar tela serve — mostrar texto. Melhor entregar pouco e legível.
	bandaMinima = 300

	// Quanto sobe de uma vez, quando sobe. Trinta por cento é grande o bastante para
	// recuperar em poucos passos e pequeno o bastante para o passo errado não derrubar
	// tudo de novo.
	passoDeSubida = 1.30

	// Mudança menor que isto não vale o preço. Reabrir o compressor custa um
	// quadro-chave; trocar 2.500 por 2.400 kbps gastaria esse preço por nada.
	mudancaQueValeAPena = 0.15
)

// ControleDeBanda decide, uma vez por segundo, quantos kbps pedir ao compressor.
//
// NÃO É CONCORRENTE. Vive na thread presa do laço de transmissão, junto do compressor —
// que é a mesma razão de `PedirQuadroChave` levantar bandeira em vez de mandar no
// compressor direto.
type ControleDeBanda struct {
	// O que a pessoa escolheu no preset. É TETO e não alvo: este controle só abaixa, e
	// quando volta a subir, para aqui.
	teto int

	atual int
	ruins int
	bons  int
}

func NovoControleDeBanda(teto int) *ControleDeBanda {
	if teto < bandaMinima {
		teto = bandaMinima
	}
	return &ControleDeBanda{teto: teto, atual: teto}
}

// Banda devolve a banda em vigor.
func (c *ControleDeBanda) Banda() int { return c.atual }

// Segundo recebe a pior perda relatada no último segundo e devolve a banda nova, quando
// há uma. O booleano é "mudou" — falso é o caso normal, e é o que impede o compressor de
// ser reaberto à toa.
//
// A PIOR PERDA E NÃO A MÉDIA, porque numa malha o compressor é UM só para todos os
// pares. Quem estiver na conexão pior manda no ritmo de todo mundo — é a limitação
// central da malha, e a média esconderia justamente a pessoa que está sofrendo.
func (c *ControleDeBanda) Segundo(perda float64) (int, bool) {
	switch {
	case perda > perdaQueDoi:
		c.ruins++
		c.bons = 0
	case perda < perdaConfortavel:
		c.bons++
		c.ruins = 0
	default:
		// A ZONA MORTA NÃO ZERA OS CONTADORES, e isso é deliberado: perda oscilando em
		// torno do limiar é exatamente uma rede no limite, e zerar ali faria o controle
		// nunca agir — ficaria esperando uma sequência limpa que não vem.
		return c.atual, false
	}

	switch {
	case c.ruins >= segundosParaRecuar:
		// A QUEDA É PROPORCIONAL À PERDA, e é a fórmula do controlador por perda do
		// GCC: perder 20% dos pacotes tira 10% da banda, perder 50% tira 25%. Uma queda
		// fixa seria tímida demais no colapso e brutal demais no arranhão.
		return c.mudarPara(int(float64(c.atual) * (1 - 0.5*perda)))
	case c.bons >= segundosParaSubir:
		return c.mudarPara(int(float64(c.atual) * passoDeSubida))
	}
	return c.atual, false
}

func (c *ControleDeBanda) mudarPara(novo int) (int, bool) {
	if novo > c.teto {
		novo = c.teto
	}
	if novo < bandaMinima {
		novo = bandaMinima
	}

	// O LIMIAR DE MUDANÇA VEM ANTES DE ZERAR OS CONTADORES. Se a mudança não vale o
	// preço, os contadores CONTINUAM correndo — senão uma rede que pede uma queda
	// pequena a cada três segundos nunca acumularia o bastante para uma queda que valha.
	if diferenca(novo, c.atual) < mudancaQueValeAPena {
		return c.atual, false
	}

	c.atual = novo
	c.ruins, c.bons = 0, 0
	return novo, true
}

func diferenca(a, b int) float64 {
	if b == 0 {
		return 1
	}
	d := float64(a-b) / float64(b)
	if d < 0 {
		return -d
	}
	return d
}

// PerdaDosPares junta o que cada par relata e devolve a pior.
//
// CONCORRENTE, ao contrário do controle: quem escreve são as goroutines de RTCP, uma por
// par, e quem lê é a thread do laço de transmissão.
type PerdaDosPares struct {
	mu     sync.Mutex
	perdas map[string]relatoDePerda
}

type relatoDePerda struct {
	fracao float64
	quando time.Time
}

func NovaPerdaDosPares() *PerdaDosPares {
	return &PerdaDosPares{perdas: map[string]relatoDePerda{}}
}

func (p *PerdaDosPares) Relatar(par string, fracao float64) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.perdas[par] = relatoDePerda{fracao: fracao, quando: time.Now()}
}

func (p *PerdaDosPares) Esquecer(par string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	delete(p.perdas, par)
}

// Pior devolve a maior perda relatada recentemente.
//
// RELATO VELHO NÃO CONTA. Um par que parou de mandar relatório caiu, mudou de rede ou
// está congelado — e a última coisa que ele disse antes de sumir não deve segurar a
// banda de todo mundo para sempre. Três segundos é o triplo do intervalo normal.
func (p *PerdaDosPares) Pior() float64 {
	p.mu.Lock()
	defer p.mu.Unlock()

	const validade = 3 * time.Second
	pior, agora := 0.0, time.Now()
	for par, r := range p.perdas {
		if agora.Sub(r.quando) > validade {
			delete(p.perdas, par)

			continue
		}
		if r.fracao > pior {
			pior = r.fracao
		}
	}
	return pior
}
