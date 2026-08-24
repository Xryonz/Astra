package main

// O ESPELHO — a tela de quem transmite, de volta para a janela dele.
//
// O QUE FALTAVA, e por que não era um defeito: o cano de quadros sempre carregou só a
// tela dos OUTROS, porque quem produz quadro em NV12 é o descompressor, e a própria tela
// nunca passa por descompressor nenhum. Quem transmitia via o botão aceso e um relatório
// de texto — nada que respondesse "estou mostrando a janela certa?", que é a única
// pergunta que a pessoa realmente faz nos primeiros dez segundos.
//
// O CAMINHO BARATO JÁ ESTAVA MONTADO, e é o motivo de este arquivo ser pequeno:
//
//   - O `Redimensionador` (Video Processor MFT) já sabe reduzir E converter dentro da
//     placa, e já sabe entregar NV12 legível pela CPU — é assim que a máquina sem
//     compressor de placa transmite (ver `comprimirNaMemoria`). Aqui ele é aberto uma
//     segunda vez, com outro destino.
//   - O protocolo do cano JÁ RESERVA o campo vazio para "eu" (ver `ponte.go`), e o lado
//     do Astra já traduz `tamPar == 0` em `par = ""`. Nada muda no formato.
//   - O NV12 que sai daqui é o mesmo que o shader do Astra já desenha. Zero código de
//     desenho novo.
//
// TRÊS DECISÕES QUE SEGURAM O CUSTO, porque o espelho não pode roubar do compressor —
// ele é o enfeite, e o compressor é o produto:
//
//  1. PEQUENO. 320 de largura, não o tamanho da transmissão. A miniatura é desenhada com
//     uns 160 pixels de lado na faixa de participantes; mandar 720p para caber ali seria
//     mover vinte vezes mais bytes para jogar fora na hora de desenhar.
//  2. LENTO, E POR RELÓGIO. Oito por segundo, medidos em tempo e não em "a cada N
//     quadros" — assim o espelho custa o mesmo com a transmissão a 60 ou a 15, e a
//     máquina fraca não paga mais justamente por ser fraca.
//  3. DESISTE EM SILÊNCIO. Qualquer erro aqui apaga o espelho e deixa a transmissão
//     seguir. Derrubar uma chamada porque a miniatura falhou seria trocar o produto pelo
//     enfeite.

import (
	"fmt"
	"os"
	"time"
	"unsafe"
)

// A LARGURA DO ESPELHO. A altura sai da proporção da tela, arredondada para par — NV12
// guarda a cor em blocos de dois por dois, e dimensão ímpar não é recusa, é uma faixa de
// cor errada na borda.
const larguraDoEspelho = 320

// DE QUANTO EM QUANTO TEMPO O ESPELHO ATUALIZA.
//
// Oito por segundo é o ponto em que o movimento ainda se lê como movimento (o olho aceita
// bem acima de seis) e o custo já é ruído: a conversão medida é 0,7ms no tamanho cheio, e
// aqui a saída tem um vigésimo dos pixels. Oito vezes por segundo isso não chega a 0,1%
// de um núcleo.
//
// Não é ajustável de fora de propósito: seria um botão para a pessoa piorar a própria
// transmissão em troca de uma miniatura mais fluida, e essa troca nunca vale.
const compassoDoEspelho = 125 * time.Millisecond

// Espelho devolve a própria tela, reduzida, pelo cano de quadros.
type Espelho struct {
	reduzir *Redimensionador
	l, a    int

	proximo time.Time
	bytes   []byte
	desistiu bool

	// Para onde vai o quadro pronto. Injetado para o teste poder olhar sem cano nenhum.
	entregar func(Quadro)
}

// AbrirEspelho monta o redutor da miniatura na MESMA placa da captura.
//
// `gerente` tem de ser o mesmo que o compressor recebeu: duas peças em placas diferentes
// não trocam textura, e a máquina híbrida (Intel + NVIDIA) é onde isso aparece.
//
// Devolve nulo SEM ERRO quando não há para quem entregar — rodar este processo à mão não
// tem Astra do outro lado, e não é falha.
func AbrirEspelho(gerente objeto, deL, deA int, entregar func(Quadro)) (*Espelho, error) {
	if entregar == nil || deL <= 0 || deA <= 0 {
		return nil, nil
	}

	l := larguraDoEspelho
	if deL < l {
		// Tela menor que o espelho: reduzir seria ampliar, e ampliar para depois
		// desenhar pequeno é gastar duas vezes para piorar.
		l = deL
	}
	a := deA * l / deL
	l, a = l&^1, a&^1
	if l <= 0 || a <= 0 {
		return nil, nil
	}

	r, err := AbrirRedimensionador(gerente, deL, deA, l, a, formatoNV12)
	if err != nil {
		return nil, fmt.Errorf("abrir o espelho: %w", err)
	}
	return &Espelho{reduzir: r, l: l, a: a, entregar: entregar}, nil
}

// Talvez entrega um quadro ao espelho SE já for hora. Barata de chamar a 60 por segundo:
// o caso comum é olhar o relógio e voltar.
//
// A amostra recebida é a do compressor — a cópia que ele fez da captura, não a textura da
// área de trabalho. Isso importa: a de lá tem de ser devolvida ao DXGI depressa, e o
// espelho não pode ser mais um a segurá-la.
func (e *Espelho) Talvez(amostra objeto) {
	if e == nil || e.desistiu || amostra == 0 {
		return
	}
	agora := time.Now()
	if agora.Before(e.proximo) {
		return
	}
	// A PRÓXIMA CASA CONTA A PARTIR DE AGORA, e não da anterior. Recuperar casas
	// perdidas faria o espelho disparar em rajada logo depois de a máquina engasgar —
	// exatamente quando ela menos pode pagar.
	e.proximo = agora.Add(compassoDoEspelho)

	if err := e.passar(amostra); err != nil {
		// UMA LINHA SÓ, e depois silêncio. Um erro por quadro encheria o diagnóstico de
		// rede com a mesma frase oitocentas vezes e esconderia o que importa.
		fmt.Fprintf(os.Stderr, "espelho desligado: %v\n", err)
		e.desistiu = true
	}
}

func (e *Espelho) passar(amostra objeto) error {
	menor, err := e.reduzir.Reduzir(amostra)
	if err != nil {
		return err
	}
	if menor == 0 {
		return nil // quadro perdido na redução; o próximo vem
	}
	defer menor.soltar()

	var buffer objeto
	if r := menor.chamar(amostraJuntarBuffers, uintptr(unsafe.Pointer(&buffer))); uint32(r)&0x80000000 != 0 {
		return hr(r, "juntar os pedaços do espelho")
	}
	defer buffer.soltar()

	var p uintptr
	var maximo, atual uint32
	r := buffer.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)),
		uintptr(unsafe.Pointer(&maximo)),
		uintptr(unsafe.Pointer(&atual)),
	)
	if err := hr(r, "abrir a miniatura para leitura"); err != nil {
		return err
	}

	// O TAMANHO É CONFERIDO, e esta é a linha que impede o defeito mais chato desta
	// função. Um buffer contíguo de NV12 tem passo igual à largura, e é sobre isso que o
	// `Passo` mandado adiante se apoia. Se um dia vier acolchoado, a conta não fecha — e
	// sem esta conferência o sintoma seria uma imagem ENVIESADA em diagonal, que manda
	// quem investiga procurar no shader, no cano e no descompressor antes de desconfiar
	// de uma multiplicação.
	esperado := e.l * e.a * 3 / 2
	if int(atual) != esperado {
		buffer.chamar(bufDestrancar)
		return fmt.Errorf("miniatura veio com %d bytes, esperava %d (%dx%d NV12)", atual, esperado, e.l, e.a)
	}

	if cap(e.bytes) < int(atual) {
		e.bytes = make([]byte, atual)
	}
	e.bytes = e.bytes[:atual]
	copy(e.bytes, unsafe.Slice((*byte)(unsafe.Pointer(p)), atual))
	buffer.chamar(bufDestrancar)

	e.entregar(Quadro{Dados: e.bytes, Largura: e.l, Altura: e.a, Passo: e.l})
	return nil
}

func (e *Espelho) Fechar() {
	if e == nil {
		return
	}
	e.reduzir.Fechar()
	e.reduzir = nil
}
