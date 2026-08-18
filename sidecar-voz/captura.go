package main

// CAPTURA DO MICROFONE por WASAPI.
//
// O formato pedido é sempre 48 kHz, mono, 16 bits — o que o Opus consome. O
// Windows reamostra sozinho quando o aparelho é de outra taxa (bandeira
// `converteFormato`), o que evita escrever um reamostrador à mão só para lidar com
// as muitas placas que rodam a 44,1 kHz.
//
// O laço é guiado por EVENTO, não por relógio nem por espera ocupada: o Windows
// avisa quando há material, e a goroutine dorme no resto do tempo. Um laço de
// espera ativa daria a mesma latência gastando um núcleo inteiro — num app que
// roda durante horas, isso é bateria de notebook indo embora por nada.

import (
	"errors"
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	TaxaDeAmostragem = 48000
	CanaisDeVoz      = 1
	// 20ms é o quadro do Opus e do WebRTC. Manter a captura no mesmo tamanho evita
	// um buffer intermediário só para reagrupar amostras.
	MilissegundosPorQuadro = 20
	AmostrasPorQuadro      = TaxaDeAmostragem * MilissegundosPorQuadro / 1000
)

// Captura é o microfone aberto.
type Captura struct {
	enumerador  objeto
	dispositivo objeto
	cliente     objeto
	captor      objeto
	evento      windows.Handle
	rodando     bool
}

// AbrirCaptura prepara o microfone de comunicação padrão do sistema.
//
// PRECISA ser chamada da mesma thread que vai ler — COM tem afinidade de thread, e
// o `runtime.LockOSThread` do laço de leitura é o que garante isso. Quem chamar
// isto de uma goroutine e ler de outra vai ver comportamento aleatório, que é o
// pior tipo de defeito.
func AbrirCaptura() (*Captura, error) {
	c := &Captura{}
	ok := false
	// Desmonta pela metade se qualquer passo falhar. Sem isto, uma falha no meio
	// deixaria o microfone preso e o aparelho indisponível para o resto do sistema
	// até o processo morrer.
	defer func() {
		if !ok {
			c.Fechar()
		}
	}()

	enumerador, err := criar(&clsidEnumeradorDeDispositivos, &iidEnumeradorDeDispositivos)
	if err != nil {
		return nil, fmt.Errorf("enumerador de áudio: %w", err)
	}
	c.enumerador = enumerador

	var dispositivo objeto
	r := enumerador.chamar(mmGetDefaultAudioEndpoint,
		uintptr(sentidoEntrada),
		uintptr(papelComunicacao),
		uintptr(unsafe.Pointer(&dispositivo)),
	)
	if err := hr(r, "pegar microfone padrão"); err != nil {
		return nil, err
	}
	c.dispositivo = dispositivo

	var cliente objeto
	r = dispositivo.chamar(mmDeviceActivate,
		uintptr(unsafe.Pointer(&iidClienteDeAudio)),
		1, // CLSCTX_INPROC_SERVER
		0, // sem parâmetros de ativação
		uintptr(unsafe.Pointer(&cliente)),
	)
	if err := hr(r, "abrir o microfone"); err != nil {
		return nil, err
	}
	c.cliente = cliente

	formato := formatoPCM(TaxaDeAmostragem, CanaisDeVoz)
	// Buffer de 200ms. Generoso de propósito: o evento acorda a cada ~20ms, e a
	// folga é o que absorve uma pausa do escalonador sem produzir falha de áudio.
	// Buffer apertado economiza memória que não faz falta e compra estouro que faz.
	duracao := int64(200 * porMilissegundo)
	r = cliente.chamar(acInitialize,
		uintptr(modoCompartilhado),
		uintptr(avisaPorEvento|converteFormato|qualidadePadraoSR),
		uintptr(duracao),
		uintptr(duracao>>32),
		uintptr(unsafe.Pointer(&formato)),
		0, // sessão de áudio padrão
	)
	if err := hr(r, "configurar a captura"); err != nil {
		return nil, err
	}

	evento, err := windows.CreateEvent(nil, 0, 0, nil)
	if err != nil {
		return nil, fmt.Errorf("criar aviso de buffer: %w", err)
	}
	c.evento = evento

	if err := hr(cliente.chamar(acSetEventHandle, uintptr(evento)), "ligar o aviso"); err != nil {
		return nil, err
	}

	var captor objeto
	r = cliente.chamar(acGetService,
		uintptr(unsafe.Pointer(&iidClienteDeCaptura)),
		uintptr(unsafe.Pointer(&captor)),
	)
	if err := hr(r, "obter o leitor de captura"); err != nil {
		return nil, err
	}
	c.captor = captor

	if err := hr(cliente.chamar(acStart), "iniciar a captura"); err != nil {
		return nil, err
	}
	c.rodando = true

	ok = true
	return c, nil
}

// Bandeiras que o WASAPI devolve junto de cada bloco (audioclient.h).
const (
	// AUDCLNT_BUFFERFLAGS_DATA_DISCONTINUITY — houve buraco. Serve para avisar o
	// outro lado, não para descartar o que veio.
	blocoDescontinuo = 0x1
	// AUDCLNT_BUFFERFLAGS_SILENT — o Windows diz "este trecho é silêncio e eu nem
	// escrevi os bytes". Ler o buffer nesse caso é ler lixo: a documentação manda
	// tratar como zeros. Ignorar esta bandeira produz estalos aleatórios que
	// parecem defeito de microfone.
	blocoSilencioso = 0x2
)

// ESTES DOIS NÚMEROS JÁ ESTIVERAM TROCADOS AQUI, e o teste não pegou — em sala
// silenciosa e sem falha de escalonador, nenhuma das duas bandeiras acende. O
// efeito seria pernicioso: zerar áudio válido quando houvesse engasgo, e reenviar
// o bloco anterior quando houvesse silêncio de verdade.
//
// A ordem certa está no cabeçalho `audioclient.h` e vale conferir antes de mexer:
// DATA_DISCONTINUITY vem PRIMEIRO (0x1), SILENT vem em SEGUIDO (0x2). A página da
// Microsoft lista os nomes sem os valores, o que convida exatamente a este erro.

var ErrSemAudio = errors.New("nada disponível agora")

// Ler entrega o próximo bloco do microfone em `destino`, devolvendo quantas
// amostras foram escritas.
//
// Devolve ErrSemAudio quando não há nada — que é o caso comum e não é falha. Quem
// chama deve esperar pelo evento antes de tentar de novo, e é assim que a espera
// sai de graça.
func (c *Captura) Ler(destino []int16) (int, bool, error) {
	var pacote uint32
	if err := hr(c.captor.chamar(capGetNextPacketSize, uintptr(unsafe.Pointer(&pacote))),
		"consultar o próximo bloco"); err != nil {
		return 0, false, err
	}
	if pacote == 0 {
		return 0, false, ErrSemAudio
	}

	// `unsafe.Pointer` e não `uintptr` de propósito: é o que o Windows escreve
	// aqui, e guardar como ponteiro de verdade evita a conversão que o `go vet`
	// marca — corretamente — como suspeita.
	var dados unsafe.Pointer
	var quadros, bandeiras uint32
	r := c.captor.chamar(capGetBuffer,
		uintptr(unsafe.Pointer(&dados)),
		uintptr(unsafe.Pointer(&quadros)),
		uintptr(unsafe.Pointer(&bandeiras)),
		0, // posição do aparelho: não usamos
		0, // marca de tempo (QPC): não usamos
	)
	if err := hr(r, "pegar o bloco"); err != nil {
		return 0, false, err
	}

	n := int(quadros) * CanaisDeVoz
	if n > len(destino) {
		n = len(destino)
	}

	if bandeiras&blocoSilencioso != 0 {
		// Zerar À MÃO. O Windows avisou que não escreveu nada; o que está ali é
		// resto do bloco anterior, e mandar isso para a call é mandar um pedaço de
		// áudio antigo de volta.
		for i := 0; i < n; i++ {
			destino[i] = 0
		}
	} else if n > 0 {
		origem := unsafe.Slice((*int16)(dados), n)
		copy(destino[:n], origem)
	}

	// ReleaseBuffer SEMPRE, e com a contagem que o GetBuffer devolveu — não com a
	// que coube em `destino`. Devolver menos do que se pegou desalinha o buffer
	// circular e o áudio começa a picotar alguns segundos depois, longe da causa.
	if err := hr(c.captor.chamar(capReleaseBuffer, uintptr(quadros)),
		"devolver o bloco"); err != nil {
		return 0, false, err
	}

	return n, bandeiras&blocoDescontinuo != 0, nil
}

// Esperar dorme até o Windows avisar que há material, ou até o tempo acabar.
//
// O tempo limite existe para o laço não ficar preso para sempre se o aparelho
// sumir (fone desconectado no meio da call é rotina, não exceção).
func (c *Captura) Esperar(limiteMs uint32) error {
	r, err := windows.WaitForSingleObject(c.evento, limiteMs)
	if err != nil {
		return fmt.Errorf("esperar pelo microfone: %w", err)
	}
	if r == uint32(windows.WAIT_TIMEOUT) {
		return ErrSemAudio
	}
	return nil
}

// Fechar solta tudo, na ordem inversa da abertura. Seguro em objeto meio montado.
func (c *Captura) Fechar() {
	if c.rodando {
		c.cliente.chamar(acStop)
		c.rodando = false
	}
	c.captor.soltar()
	c.captor = 0
	if c.evento != 0 {
		windows.CloseHandle(c.evento)
		c.evento = 0
	}
	c.cliente.soltar()
	c.cliente = 0
	c.dispositivo.soltar()
	c.dispositivo = 0
	c.enumerador.soltar()
	c.enumerador = 0
}

// PrenderNaThread trava a goroutine atual numa thread do sistema.
//
// Obrigatório antes de abrir e usar qualquer coisa daqui: COM é preso à thread que
// inicializou o apartamento, e o Go move goroutines entre threads quando bem
// entende. Sem isto, funciona nos testes e falha em produção — o pior padrão de
// falha que existe.
func PrenderNaThread() func() {
	runtime.LockOSThread()
	return runtime.UnlockOSThread
}
