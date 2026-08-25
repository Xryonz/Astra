package main

import (
	"fmt"
	"os"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

type FonteDeAudio interface {
	Ler(destino []int16) (int, bool, error)

	Esperar(limiteMs uint32) error

	Taxa() int
	Fechar()
}

var (
	_ FonteDeAudio = (*Captura)(nil)
	_ FonteDeAudio = (*CapturaComEco)(nil)
)

var (
	clsidCanceladorDeEco = guid(0x745057C7, 0xF353, 0x4F2D,
		[8]byte{0xA7, 0xEE, 0x58, 0x43, 0x44, 0x77, 0x73, 0x0E})

	iidObjetoDeMidia = guid(0xD8AD0F58, 0x5494, 0x4102,
		[8]byte{0x97, 0xC5, 0xEC, 0x79, 0x8E, 0x59, 0xBC, 0xF4})

	iidLojaDePropriedades = guid(0x886D8EEB, 0x8CF2, 0x4446,
		[8]byte{0x8D, 0x02, 0xCD, 0xBA, 0x1D, 0xBD, 0xCF, 0x99})

	tipoAudio = guid(0x73647561, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	subtipoPCM = guid(0x00000001, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	formatoOnda = guid(0x05589F81, 0xC356, 0x11CE,
		[8]byte{0xBF, 0x01, 0x00, 0xAA, 0x00, 0x55, 0x59, 0x5A})

	conjuntoDoCancelador = guid(0x6F52C567, 0x0360, 0x4BD2,
		[8]byte{0x96, 0x17, 0xCC, 0xBF, 0x14, 0x21, 0xC9, 0x39})
)

var (
	propModoDoSistema = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 2}
	propModoFonte     = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 3}
	propIndices       = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 4}

	propModoDeAjuste = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 5}
	propRuido        = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 8}
	propGanho        = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 9}
)

type AjustesDaVoz struct {
	Eco   bool
	Ruido bool
	Ganho bool
}

const modoSoCancelarEco = 0

const taxaDoCancelador = 16000

const (
	moContarFluxos       = 3
	_moInfoFluxoEntrada  = 4
	_moInfoFluxoSaida    = 5
	_moTipoEntrada       = 6
	_moTipoSaida         = 7
	moDefinirTipoEntrada = 8
	moDefinirTipoSaida   = 9
	_moTipoAtualEntrada  = 10
	_moTipoAtualSaida    = 11
	_moTamanhoEntrada    = 12
	_moTamanhoSaida      = 13
	_moLatenciaMax       = 14
	_moDefinirLatencia   = 15
	_moDescarregar       = 16
	_moDescontinuidade   = 17
	moAlocarRecursos     = 18
	moLiberarRecursos    = 19
	_moStatusEntrada     = 20
	_moProcessarEntrada  = 21
	moProcessarSaida     = 22
	_moTrancar           = 23
)

type tipoDeMidia struct {
	principal          windows.GUID
	subtipo            windows.GUID
	amostraFixa        int32
	compressaoTemporal int32
	tamanhoAmostra     uint32
	tipoDoFormato      windows.GUID
	desconhecido       uintptr
	tamanhoDoFormato   uint32
	_                  uint32
	formato            uintptr
}

type bufferDeSaida struct {
	buffer  uintptr
	status  uint32
	_       uint32
	carimbo int64
	duracao int64
}

const temMaisSaida = 0x01000000

type CapturaComEco struct {
	objeto objeto
	buffer *BufferDeMidia

	sobra []int16
}

func AbrirEntradaDeVoz(idAparelho string, aj AjustesDaVoz) (FonteDeAudio, error) {
	if aj.Eco {
		fonte, err := AbrirCapturaComEco(idAparelho, aj)
		if err == nil {
			return fonte, nil
		}
		fmt.Fprintf(os.Stderr, "cancelador de eco indisponível (%v); seguindo sem ele\n", err)
	}
	return AbrirCaptura(idAparelho)
}

func AbrirCapturaComEco(idAparelho string, aj AjustesDaVoz) (*CapturaComEco, error) {
	c := &CapturaComEco{}
	ok := false
	defer func() {
		if !ok {
			c.Fechar()
		}
	}()

	obj, err := criar(&clsidCanceladorDeEco, &iidObjetoDeMidia)
	if err != nil {
		return nil, fmt.Errorf("criar o cancelador: %w", err)
	}
	c.objeto = obj

	if err := c.configurar(idAparelho, aj); err != nil {
		return nil, err
	}

	if err := c.definirFormatoDeSaida(); err != nil {
		return nil, err
	}

	if err := hr(c.objeto.chamar(moAlocarRecursos), "preparar o cancelador"); err != nil {
		return nil, err
	}

	porQuadro := taxaDoCancelador * MilissegundosPorQuadro / 1000
	c.buffer = NovoBufferDeMidia(porQuadro * CanaisDeVoz * 2 * 8)

	ok = true
	return c, nil
}

func (c *CapturaComEco) configurar(idAparelho string, aj AjustesDaVoz) error {
	loja, err := c.objeto.consultar(&iidLojaDePropriedades)
	if err != nil {
		return fmt.Errorf("abrir as propriedades do cancelador: %w", err)
	}
	defer loja.soltar()

	if err := escreverPropI4(loja, propModoDoSistema, modoSoCancelarEco); err != nil {
		return fmt.Errorf("escolher o modo de cancelamento: %w", err)
	}
	if err := escreverPropBool(loja, propModoFonte, true); err != nil {
		return fmt.Errorf("ligar o modo fonte: %w", err)
	}

	entrada, saida := indicesDe(idAparelho)
	empacotado := int32(saida)<<16 | (int32(entrada) & 0xFFFF)
	if err := escreverPropI4(loja, propIndices, empacotado); err != nil {
		return fmt.Errorf("apontar os aparelhos: %w", err)
	}

	if err := escreverPropBool(loja, propModoDeAjuste, true); err != nil {
		return fmt.Errorf("abrir o ajuste fino do cancelador: %w", err)
	}

	ruido := int32(0)
	if aj.Ruido {
		ruido = 1
	}
	if err := escreverPropI4(loja, propRuido, ruido); err != nil {
		return fmt.Errorf("ajustar a supressão de ruído: %w", err)
	}
	if err := escreverPropBool(loja, propGanho, aj.Ganho); err != nil {
		return fmt.Errorf("ajustar o ganho automático: %w", err)
	}
	return nil
}

func (c *CapturaComEco) Taxa() int { return taxaDoCancelador }

func (c *CapturaComEco) definirFormatoDeSaida() error {
	onda := formatoPCM(taxaDoCancelador, CanaisDeVoz)
	tipo := tipoDeMidia{
		principal:      tipoAudio,
		subtipo:        subtipoPCM,
		amostraFixa:    1,
		tamanhoAmostra: uint32(CanaisDeVoz * 2),
		tipoDoFormato:  formatoOnda,

		tamanhoDoFormato: tamanhoDoFormatoDeOnda,
		formato:          uintptr(unsafe.Pointer(&onda)),
	}

	r := c.objeto.chamar(moDefinirTipoSaida, 0, uintptr(unsafe.Pointer(&tipo)), 0)
	return hr(r, "definir o formato de saída do cancelador")
}

func (c *CapturaComEco) Ler(destino []int16) (int, bool, error) {

	if len(c.sobra) > 0 {
		n := copy(destino, c.sobra)
		c.sobra = c.sobra[n:]
		if len(c.sobra) == 0 {
			c.sobra = nil
		}
		return n, false, nil
	}

	c.buffer.Zerar()
	pedido := bufferDeSaida{buffer: c.buffer.Ponteiro()}
	var status uint32
	r := c.objeto.chamar(moProcessarSaida,
		0,
		1,
		uintptr(unsafe.Pointer(&pedido)),
		uintptr(unsafe.Pointer(&status)),
	)
	if err := hr(r, "pedir voz ao cancelador"); err != nil {
		return 0, false, err
	}

	bytes := c.buffer.Conteudo()
	if len(bytes) == 0 {
		return 0, false, ErrSemAudio
	}

	amostras := unsafe.Slice((*int16)(unsafe.Pointer(&bytes[0])), len(bytes)/2)
	n := copy(destino, amostras)
	if n < len(amostras) {

		sobrou := amostras[n:]
		c.sobra = make([]int16, len(sobrou))
		copy(c.sobra, sobrou)
	}
	return n, false, nil
}

func (c *CapturaComEco) Esperar(limiteMs uint32) error {
	if len(c.sobra) > 0 {
		return nil
	}
	espera := 5 * time.Millisecond
	if limiteMs > 0 && time.Duration(limiteMs)*time.Millisecond < espera {
		espera = time.Duration(limiteMs) * time.Millisecond
	}
	time.Sleep(espera)
	return nil
}

func (c *CapturaComEco) Fechar() {
	if c.objeto != 0 {
		c.objeto.chamar(moLiberarRecursos)
		c.objeto.soltar()
		c.objeto = 0
	}
	if c.buffer != nil {
		c.buffer.Fechar()
		c.buffer = nil
	}
	c.sobra = nil
}

func escreverPropI4(loja objeto, chave chaveDePropriedade, valor int32) error {
	v := propvariant{tipo: 3, ponteiro: uintptr(uint32(valor))}
	return hr(loja.chamar(lojaEscrever,
		uintptr(unsafe.Pointer(&chave)),
		uintptr(unsafe.Pointer(&v)),
	), "escrever propriedade")
}

func escreverPropBool(loja objeto, chave chaveDePropriedade, valor bool) error {

	bruto := uintptr(0)
	if valor {
		bruto = uintptr(uint16(0xFFFF))
	}
	v := propvariant{tipo: 11, ponteiro: bruto}
	return hr(loja.chamar(lojaEscrever,
		uintptr(unsafe.Pointer(&chave)),
		uintptr(unsafe.Pointer(&v)),
	), "escrever propriedade booleana")
}

func indicesDe(idAparelho string) (entrada, saida int) {
	entrada, saida = -1, -1
	if idAparelho == "" {
		return
	}
	lista, err := ListarAparelhos(sentidoEntrada)
	if err != nil {
		return
	}
	for i, a := range lista {
		if a.ID == idAparelho {
			return i, -1
		}
	}
	return
}
