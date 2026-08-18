package main

// OPUS SEM CGO.
//
// O codificador Opus é uma biblioteca em C, e o caminho comum em Go é o cgo
// (`hraban/opus`). Aqui não: carregamos a DLL em tempo de execução e chamamos as
// funções por `syscall`. O build continua sendo `go build` puro, sem compilador C,
// e o release automatizado no Windows não ganha uma dependência que quebra sozinha
// meses depois. É o mesmo padrão que o Astra já usa com o ffmpeg e o GStreamer.
//
// O PREÇO, dito em voz alta: sem cgo não há compilador conferindo assinatura. Se um
// tipo aqui estiver errado, o resultado não é erro de compilação — é memória
// corrompida e travamento. Por isso cada função abaixo carrega, em comentário, a
// assinatura em C de onde ela veio (`opus.h`), e os números mágicos vêm com o nome
// da constante original (`opus_defines.h`). Conferir isso é o que substitui o
// compilador.
//
// Também é Windows-only por construção. É o único sistema que o Astra desktop tem
// hoje; se um dia houver Linux, este arquivo ganha um irmão.

import (
	"errors"
	"fmt"
	"syscall"
	"unsafe"
)

// Constantes de `opus_defines.h`. Os nomes originais estão ao lado de propósito:
// é o que permite conferir qualquer um destes números contra o cabeçalho oficial
// sem ter que adivinhar o que ele significa.
const (
	opusOK = 0 // OPUS_OK

	appVoIP = 2048 // OPUS_APPLICATION_VOIP — voz, não música

	ctlSetBitrate      = 4002 // OPUS_SET_BITRATE_REQUEST
	ctlSetMaxBandwidth = 4004 // OPUS_SET_MAX_BANDWIDTH_REQUEST
	ctlSetComplexity   = 4010 // OPUS_SET_COMPLEXITY_REQUEST
	ctlSetInbandFEC    = 4012 // OPUS_SET_INBAND_FEC_REQUEST
	ctlSetPacketLoss   = 4014 // OPUS_SET_PACKET_LOSS_PERC_REQUEST
	ctlSetDTX          = 4016 // OPUS_SET_DTX_REQUEST
	ctlSetSignal       = 4024 // OPUS_SET_SIGNAL_REQUEST

	bandaLarga = 1103 // OPUS_BANDWIDTH_WIDEBAND
	sinalDeVoz = 3001 // OPUS_SIGNAL_VOICE
)

var (
	dll *syscall.LazyDLL

	procCriarCodificador      *syscall.LazyProc
	procControlar             *syscall.LazyProc
	procCodificar             *syscall.LazyProc
	procDestruirCodificador   *syscall.LazyProc
	procCriarDecodificador    *syscall.LazyProc
	procDecodificar           *syscall.LazyProc
	procDestruirDecodificador *syscall.LazyProc
)

// AbrirOpus carrega a biblioteca. Chamar antes de qualquer outra coisa daqui.
//
// A carga é explícita, e não preguiçosa no primeiro uso, porque falta de DLL tem
// que aparecer ao ligar o processo — não no meio de uma chamada, quando a pessoa
// já está esperando ouvir alguém.
func AbrirOpus(caminho string) error {
	dll = syscall.NewLazyDLL(caminho)
	if err := dll.Load(); err != nil {
		return fmt.Errorf("carregar %s: %w", caminho, err)
	}

	procCriarCodificador = dll.NewProc("opus_encoder_create")
	procControlar = dll.NewProc("opus_encoder_ctl")
	procCodificar = dll.NewProc("opus_encode")
	procDestruirCodificador = dll.NewProc("opus_encoder_destroy")
	procCriarDecodificador = dll.NewProc("opus_decoder_create")
	procDecodificar = dll.NewProc("opus_decode")
	procDestruirDecodificador = dll.NewProc("opus_decoder_destroy")

	// Confere que cada símbolo existe AGORA. Sem isto, um nome errado só
	// apareceria na primeira chamada, dentro de uma call, como travamento.
	for nome, p := range map[string]*syscall.LazyProc{
		"opus_encoder_create":  procCriarCodificador,
		"opus_encoder_ctl":     procControlar,
		"opus_encode":          procCodificar,
		"opus_encoder_destroy": procDestruirCodificador,
		"opus_decoder_create":  procCriarDecodificador,
		"opus_decode":          procDecodificar,
		"opus_decoder_destroy": procDestruirDecodificador,
	} {
		if err := p.Find(); err != nil {
			return fmt.Errorf("símbolo %s ausente em %s: %w", nome, caminho, err)
		}
	}
	return nil
}

// Codificador transforma PCM em quadros Opus.
//
// NÃO É SEGURO usar de várias goroutines: o estado interno da libopus não é. Só a
// goroutine de captura mexe nele, e é assim que tem que continuar.
type Codificador struct {
	st uintptr
}

// NovoCodificador cria o codificador já ajustado para voz em malha.
//
//	OpusEncoder *opus_encoder_create(opus_int32 Fs, int channels, int application, int *error);
func NovoCodificador(taxa, canais int) (*Codificador, error) {
	var errC int32
	st, _, _ := procCriarCodificador.Call(
		uintptr(taxa),
		uintptr(canais),
		uintptr(appVoIP),
		uintptr(unsafe.Pointer(&errC)),
	)
	if errC != opusOK || st == 0 {
		return nil, fmt.Errorf("opus_encoder_create devolveu %d", errC)
	}

	c := &Codificador{st: st}

	// OS AJUSTES SÃO A OTIMIZAÇÃO, e é por isso que eles vivem aqui e não numa
	// função "configurar" que alguém pode esquecer de chamar.
	//
	// - 24 kbps mono: voz inteligível de sobra. Música pediria 64+, e não é o caso.
	// - Banda larga (até 8 kHz): voz humana não usa a faixa acima disso, então
	//   gastar bits ali é desperdício puro.
	// - Sinal de voz: diz ao codificador o que ele está ouvindo, e ele escolhe
	//   melhor onde economizar.
	// - DTX: em silêncio, manda um pacote minúsculo a cada ~400ms em vez de um
	//   quadro cheio a cada 20ms. É a maior economia de banda por linha escrita.
	// - FEC embutido: uma cópia de baixa taxa do quadro anterior viaja dentro do
	//   atual, então perder um pacote não vira um buraco audível. Preferido ao
	//   NACK porque não custa uma ida e volta — e num buffer de voz, que é curto,
	//   retransmissão quase sempre chega tarde demais para servir.
	// - Perda declarada em 10%: é o que diz ao FEC quanta redundância vale a pena.
	// - Complexidade 5 (de 10): metade do custo de CPU do máximo, com diferença
	//   inaudível em voz. Numa malha isso multiplica pelo número de pessoas.
	ajustes := []struct {
		pedido int
		valor  int
		nome   string
	}{
		{ctlSetBitrate, 24000, "bitrate"},
		{ctlSetMaxBandwidth, bandaLarga, "banda máxima"},
		{ctlSetSignal, sinalDeVoz, "tipo de sinal"},
		{ctlSetDTX, 1, "DTX"},
		{ctlSetInbandFEC, 1, "FEC embutido"},
		{ctlSetPacketLoss, 10, "perda esperada"},
		{ctlSetComplexity, 5, "complexidade"},
	}
	for _, a := range ajustes {
		if err := c.controlar(a.pedido, a.valor); err != nil {
			c.Fechar()
			return nil, fmt.Errorf("ajustar %s: %w", a.nome, err)
		}
	}
	return c, nil
}

// controlar chama o `opus_encoder_ctl`, que é variádico em C.
//
//	int opus_encoder_ctl(OpusEncoder *st, int request, ...);
//
// Variádico chamado por syscall funciona aqui porque no Windows x64 os argumentos
// inteiros de uma função variádica seguem a MESMA ordem de registradores dos
// argumentos fixos. Isso vale para os pedidos que recebem um inteiro, que são
// todos os usados acima. Pedido que receba ponteiro ou double precisaria de
// cuidado próprio — se algum entrar aqui um dia, este comentário é o aviso.
func (c *Codificador) controlar(pedido, valor int) error {
	r, _, _ := procControlar.Call(c.st, uintptr(pedido), uintptr(valor))
	if int32(r) != opusOK {
		return fmt.Errorf("opus_encoder_ctl(%d) devolveu %d", pedido, int32(r))
	}
	return nil
}

// Codificar transforma um quadro de PCM em bytes Opus, escrevendo em `saida`.
// Devolve quantos bytes valem.
//
//	opus_int32 opus_encode(OpusEncoder *st, const opus_int16 *pcm, int frame_size,
//	                       unsigned char *data, opus_int32 max_data_bytes);
//
// `quadros` é a contagem POR CANAL, não o tamanho da fatia — confundir os dois é o
// erro clássico desta API e produz áudio acelerado ou lento.
//
// Um retorno de 1 byte NÃO é erro: é o DTX dizendo "silêncio". Esse pacote ainda
// deve ser enviado, porque é ele que mantém o outro lado sabendo que a conexão
// está viva.
func (c *Codificador) Codificar(pcm []int16, saida []byte) (int, error) {
	if len(pcm) == 0 {
		return 0, errors.New("quadro de áudio vazio")
	}
	n, _, _ := procCodificar.Call(
		c.st,
		uintptr(unsafe.Pointer(&pcm[0])),
		uintptr(len(pcm)),
		uintptr(unsafe.Pointer(&saida[0])),
		uintptr(len(saida)),
	)
	escritos := int32(n)
	if escritos < 0 {
		return 0, fmt.Errorf("opus_encode devolveu %d", escritos)
	}
	return int(escritos), nil
}

// void opus_encoder_destroy(OpusEncoder *st);
func (c *Codificador) Fechar() {
	if c.st == 0 {
		return
	}
	procDestruirCodificador.Call(c.st)
	c.st = 0
}

// Decodificador transforma quadros Opus de volta em PCM. Um por pessoa que fala —
// e, como o codificador, não é seguro entre goroutines.
type Decodificador struct {
	st uintptr
}

// OpusDecoder *opus_decoder_create(opus_int32 Fs, int channels, int *error);
func NovoDecodificador(taxa, canais int) (*Decodificador, error) {
	var errC int32
	st, _, _ := procCriarDecodificador.Call(
		uintptr(taxa),
		uintptr(canais),
		uintptr(unsafe.Pointer(&errC)),
	)
	if errC != opusOK || st == 0 {
		return nil, fmt.Errorf("opus_decoder_create devolveu %d", errC)
	}
	return &Decodificador{st: st}, nil
}

// Decodificar devolve quantos quadros por canal foram escritos em `pcm`.
//
//	int opus_decode(OpusDecoder *st, const unsigned char *data, opus_int32 len,
//	                opus_int16 *pcm, int frame_size, int decode_fec);
//
// `dados` vazio significa PERDA, e é assim que se usa o FEC: o decodificador
// reconstrói o que faltou a partir da cópia embutida no pacote seguinte, ou
// inventa um trecho plausível. Pular a chamada em vez de avisar a perda produz o
// clique que todo mundo reconhece como "falhou a internet".
func (d *Decodificador) Decodificar(dados []byte, pcm []int16, recuperando bool) (int, error) {
	var ptr uintptr
	if len(dados) > 0 {
		ptr = uintptr(unsafe.Pointer(&dados[0]))
	}
	fec := uintptr(0)
	if recuperando {
		fec = 1
	}
	n, _, _ := procDecodificar.Call(
		d.st,
		ptr,
		uintptr(len(dados)),
		uintptr(unsafe.Pointer(&pcm[0])),
		uintptr(len(pcm)),
		fec,
	)
	lidos := int32(n)
	if lidos < 0 {
		return 0, fmt.Errorf("opus_decode devolveu %d", lidos)
	}
	return int(lidos), nil
}

// void opus_decoder_destroy(OpusDecoder *st);
func (d *Decodificador) Fechar() {
	if d.st == 0 {
		return
	}
	procDestruirDecodificador.Call(d.st)
	d.st = 0
}
