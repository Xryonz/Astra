package main

// MISTURA — juntar a voz de todo mundo num fluxo só para o alto-falante.
//
// Numa malha não existe servidor somando as vozes: cada pessoa recebe N-1 fluxos
// separados e tem que somá-los em casa. Este arquivo é esse "em casa", e é o lugar
// onde mora o custo que cresce com o tamanho da call — a faixa compartilhada
// resolveu a CODIFICAÇÃO, não a decodificação.
//
// A soma tem uma armadilha que quase todo mundo pisa: somar duas ondas de 16 bits
// estoura os 16 bits. Somar sem tratar isso não produz volume alto, produz um
// rangido — o valor dá a volta e vira negativo, e o resultado soa como rádio
// quebrado. É por isso que existe o corte lá embaixo.

import "sync"

// quadrosDeFolga é quantos quadros de 20ms cada pessoa pode adiantar antes de
// começarmos a descartar.
//
// Três quadros (60ms) é um meio-termo escolhido: menos que isso e qualquer
// tremida de rede vira buraco audível; muito mais e a conversa fica com atraso
// perceptível, que é pior — gente começa a falar por cima uma da outra.
const quadrosDeFolga = 3

// Misturador guarda o que chegou de cada pessoa e entrega a soma.
//
// Um buffer por pessoa, e não uma fila só: cada um chega no seu ritmo, e juntar
// tudo numa fila única faria a voz de quem tem rede pior atropelar a de quem tem
// rede boa.
type Misturador struct {
	mu    sync.Mutex
	vozes map[string]*vozRecebida
}

type vozRecebida struct {
	// Fila circular de quadros já decodificados, prontos para somar.
	fila [][]int16
	// Quantos quadros seguidos esta pessoa deixou de entregar. Serve para saber
	// quando ela sumiu de vez, em vez de estar só com a rede ruim.
	faltas int
}

func NovoMisturador() *Misturador {
	return &Misturador{vozes: make(map[string]*vozRecebida)}
}

// Entregar guarda um quadro decodificado de alguém.
//
// Chamado da goroutine que lê a conexão daquela pessoa — uma por par, portanto
// várias ao mesmo tempo. Daí o mutex.
func (m *Misturador) Entregar(id string, pcm []int16) {
	m.mu.Lock()
	defer m.mu.Unlock()

	v, ok := m.vozes[id]
	if !ok {
		v = &vozRecebida{}
		m.vozes[id] = v
	}
	v.faltas = 0

	// DESCARTA O MAIS ANTIGO, não o mais novo, quando a fila enche.
	//
	// Parece contraintuitivo jogar fora o que chegou primeiro, mas em conversa ao
	// vivo o áudio velho não tem valor nenhum: ninguém quer ouvir o que foi dito há
	// 200ms com 200ms de atraso. Guardar o novo mantém a conversa no presente, ao
	// custo de um engasgo curto — que é o que o ouvido perdoa.
	if len(v.fila) >= quadrosDeFolga {
		v.fila = v.fila[1:]
	}

	// Cópia própria: o buffer de quem chamou vai ser reaproveitado no próximo
	// quadro. Guardar a fatia direto faria a fila inteira apontar para o mesmo
	// pedaço de memória, e o som viraria o último quadro repetido N vezes.
	guardado := make([]int16, len(pcm))
	copy(guardado, pcm)
	v.fila = append(v.fila, guardado)
}

// Puxar soma o próximo quadro de cada pessoa em `destino` e devolve quantas vozes
// entraram. Zero significa silêncio — ninguém falando, ou ninguém na call.
func (m *Misturador) Puxar(destino []int16) int {
	m.mu.Lock()
	defer m.mu.Unlock()

	// Acumula em 32 bits ANTES de cortar. Somar direto em 16 bits perderia a
	// informação de quanto passou do limite, e o corte precisa dela para saber o
	// quanto atenuar.
	soma := make([]int32, len(destino))
	vozes := 0

	for id, v := range m.vozes {
		if len(v.fila) == 0 {
			v.faltas++
			// Quinze quadros são 300ms sem nada. Aí a pessoa saiu, caiu, ou está
			// em silêncio profundo com DTX — nos três casos, esquecer o buffer
			// dela evita segurar memória por uma call inteira.
			if v.faltas > 15 {
				delete(m.vozes, id)
			}
			continue
		}
		quadro := v.fila[0]
		v.fila = v.fila[1:]
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

// cortar prende o valor dentro dos 16 bits.
//
// Corte simples, e não divisão pelo número de vozes, de propósito: dividir faria o
// volume de todo mundo CAIR toda vez que alguém entrasse na call, o que soa como
// defeito. O estouro só acontece quando várias pessoas falam alto ao mesmo tempo,
// que é raro e curto; baixar o volume de todos o tempo inteiro para evitá-lo seria
// pagar sempre por um problema que acontece às vezes.
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

// Esquecer tira alguém da mistura — quando sai da call, por exemplo.
func (m *Misturador) Esquecer(id string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.vozes, id)
}
