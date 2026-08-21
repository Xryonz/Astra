package main

// CANCELAMENTO DE ECO pelo Voice Capture DSP do Windows.
//
// O PROBLEMA: quem usa caixas de som em vez de fone joga o áudio dos outros de volta
// no próprio microfone. Todo mundo se ouve com atraso, e a call fica insuportável sem
// que ninguém consiga apontar o culpado — cada um acha que o problema é do outro.
//
// A SOLUÇÃO NÃO É NOSSA, e essa é a melhor parte. O Windows traz um cancelador de
// eco pronto, o mesmo que os programas de chamada do sistema usam. Escrever um do
// zero seria meses de processamento de sinal para chegar pior.
//
// MODO FONTE, e é o que torna isto viável.
//
// O cancelador tem dois modos. No modo FILTRO, nós capturamos o microfone, nós
// capturamos o que sai no alto-falante, e alimentamos os dois nele. No modo FONTE,
// ele mesmo abre os dois aparelhos e nós só pedimos o resultado limpo.
//
// O modo fonte poupa metade do trabalho: some a necessidade de capturar o retorno do
// alto-falante (captura em laço, que tem armadilhas próprias) e some a sincronia
// entre os dois fluxos, que é onde canceladores de eco costumam morrer. Em troca,
// ele escolhe os aparelhos por ÍNDICE em vez de identificador — ver `indicesDe`.
//
// ESTRUTURA: `CapturaComEco` tem a mesma forma que `Captura`, então o motor consome
// as duas pela interface `FonteDeAudio` e não sabe qual está usando. Trocar uma pela
// outra (ou cair de uma para a outra quando o cancelador não abre) não toca em uma
// linha do laço de áudio.

import (
	"fmt"
	"os"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

// FonteDeAudio é de onde o motor tira a voz de quem está falando aqui.
//
// A interface existe para o laço de captura não precisar saber se há cancelamento de
// eco no caminho. É a diferença entre "trocar a fonte é uma linha" e "trocar a fonte
// é mexer no laço de áudio", e a segunda é onde se introduz defeito.
type FonteDeAudio interface {
	// Ler entrega o próximo bloco. Devolve ErrSemAudio quando não há nada — que é o
	// caso comum e não é falha.
	Ler(destino []int16) (int, bool, error)
	// Esperar dorme até haver material, ou até o tempo acabar.
	Esperar(limiteMs uint32) error
	// Taxa é a amostragem que ESTA fonte entrega, e é por isso que ela existe: a
	// captura crua entrega 48 kHz e o cancelador de eco entrega 16 kHz. Deixar isso
	// numa constante global obrigaria o laço a saber qual fonte está usando — que é
	// exatamente o que a interface existe para evitar.
	Taxa() int
	Fechar()
}

// As duas implementações. Declarado assim para o compilador reclamar aqui, e não
// longe daqui, se alguma delas divergir.
var (
	_ FonteDeAudio = (*Captura)(nil)
	_ FonteDeAudio = (*CapturaComEco)(nil)
)

// ---------------------------------------------------------------------------
// Identificadores.

var (
	// CLSID_CWMAudioAEC {745057c7-f353-4f2d-a7ee-58434477730e}
	// Conferido em duas fontes independentes: o registro do Windows (nome "AEC") e
	// o cabeçalho wmcodecdsp.h. Ver a sonda em eco_sonda_test.go.
	clsidCanceladorDeEco = guid(0x745057C7, 0xF353, 0x4F2D,
		[8]byte{0xA7, 0xEE, 0x58, 0x43, 0x44, 0x77, 0x73, 0x0E})

	// IID_IMediaObject {d8ad0f58-5494-4102-97c5-ec798e59bcf4}
	iidObjetoDeMidia = guid(0xD8AD0F58, 0x5494, 0x4102,
		[8]byte{0x97, 0xC5, 0xEC, 0x79, 0x8E, 0x59, 0xBC, 0xF4})

	// IID_IPropertyStore {886d8eeb-8cf2-4446-8d02-cdba1dbdcf99}
	iidLojaDePropriedades = guid(0x886D8EEB, 0x8CF2, 0x4446,
		[8]byte{0x8D, 0x02, 0xCD, 0xBA, 0x1D, 0xBD, 0xCF, 0x99})

	// MEDIATYPE_Audio {73647561-0000-0010-8000-00AA00389B71}
	tipoAudio = guid(0x73647561, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// MEDIASUBTYPE_PCM {00000001-0000-0010-8000-00AA00389B71}
	subtipoPCM = guid(0x00000001, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// FORMAT_WaveFormatEx {05589f81-c356-11ce-bf01-00aa0055595a}
	formatoOnda = guid(0x05589F81, 0xC356, 0x11CE,
		[8]byte{0xBF, 0x01, 0x00, 0xAA, 0x00, 0x55, 0x59, 0x5A})

	// O conjunto das propriedades do cancelador, descoberto pela sonda:
	// {6F52C567-0360-4BD2-9617-CCBF1421C939}
	conjuntoDoCancelador = guid(0x6F52C567, 0x0360, 0x4BD2,
		[8]byte{0x96, 0x17, 0xCC, 0xBF, 0x14, 0x21, 0xC9, 0x39})
)

// As propriedades que configuramos, com os identificadores que a sonda estabeleceu.
var (
	propModoDoSistema = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 2}
	propModoFonte     = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 3}
	propIndices       = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 4}

	// AS TRÊS QUE FAZEM A SUPRESSÃO DE RUÍDO E O GANHO AUTOMÁTICO EXISTIREM.
	//
	// Estabelecidas pela mesma sonda das de cima (`TestSondaDoEco`), e a identificação
	// se sustenta em duas coincidências independentes por chave: o TIPO e o VALOR
	// PADRÃO batem com o que a Microsoft documenta por nome, e elas caem em PIDs
	// consecutivos na ordem em que a documentação as apresenta.
	//
	//	pid=5  BOOL = false -> FEATURE_MODE  (o portão)
	//	pid=8  I4   = 1     -> FEATR_NS      (I4 e não BOOL: tem modos, não liga/desliga)
	//	pid=9  BOOL = false -> FEATR_AGC
	//
	// O PORTÃO É O QUE IMPORTA ENTENDER. Com FEATURE_MODE em falso — que é o padrão —
	// o cancelador roda a configuração de fábrica dele e IGNORA as duas outras. Era
	// exatamente esse o estado do Astra: a supressão de ruído estava ligada sempre
	// (padrão 1) e o ganho automático estava desligado sempre (padrão falso), desse
	// jeito e sem relação nenhuma com os interruptores da tela.
	propModoDeAjuste = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 5}
	propRuido        = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 8}
	propGanho        = chaveDePropriedade{conjunto: conjuntoDoCancelador, id: 9}
)

// AjustesDaVoz é o que a pessoa escolheu em Configurações › Voz.
//
// UM TIPO E NÃO TRÊS PARÂMETROS porque os três viajam juntos do começo ao fim (ponte
// → motor → abertura da fonte), e três booleanos em sequência numa assinatura são um
// convite a trocar dois de lugar — erro que compila e só aparece no ouvido de alguém.
type AjustesDaVoz struct {
	Eco   bool
	Ruido bool
	Ganho bool
}

// Valor de SYSTEM_MODE. Só um nos interessa.
//
// SINGLE_CHANNEL_AEC é cancelamento de eco puro, sem processamento de arranjo de
// microfones. Os modos com arranjo exigem que a máquina TENHA um arranjo descrito, e
// falham ou pioram o som em máquina comum — que é a maioria.
const modoSoCancelarEco = 0

// A TAXA DO CANCELADOR É 16 kHz, E ISSO NÃO CUSTA QUALIDADE NENHUMA.
//
// Parece uma queda feia vindo dos 48 kHz da captura crua, e seria — se o Opus
// estivesse aproveitando os 48. Ele não está: o codificador é configurado em
// `OPUS_BANDWIDTH_WIDEBAND`, que é banda de áudio de 8 kHz, e 8 kHz de banda pedem
// exatamente 16 kHz de amostragem. Tudo acima disso já era descartado dentro do
// codificador, antes de virar pacote.
//
// Ou seja: o cancelamento de eco sai de graça em qualidade, e ainda economiza — o
// microfone e o Opus passam a trabalhar com um terço das amostras.
//
// O NÚMERO NÃO É ESCOLHA NOSSA, é o que o objeto aceita. Perguntamos a ele, com a
// bandeira de teste do `SetOutputType`, e a resposta foi: 8000, 11025, 16000 e 22050
// passam; 32000, 44100 e 48000 são recusados. 16000 é o melhor que serve à banda que
// já usamos. Ver `TestTaxasQueOCanceladorAceitaDeVerdade`.
const taxaDoCancelador = 16000

// Índices de vtable do IMediaObject (mediaobj.h), na ordem de declaração.
//
// ÍNDICE ERRADO AQUI NÃO DÁ ERRO: salta para a função vizinha, com os argumentos
// errados, e derruba o processo. Por isso `TestVtableDoCancelador` confere a base
// chamando GetStreamCount e exigindo a resposta certa (1 entrada, 1 saída) ANTES de
// qualquer coisa depender destes números.
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

// DMO_MEDIA_TYPE — como se descreve um formato para um DMO.
type tipoDeMidia struct {
	principal        windows.GUID
	subtipo          windows.GUID
	amostraFixa      int32
	compressaoTemporal int32
	tamanhoAmostra   uint32
	tipoDoFormato    windows.GUID
	desconhecido     uintptr
	tamanhoDoFormato uint32
	_                uint32 // preenchimento até o alinhamento de ponteiro
	formato          uintptr
}

// DMO_OUTPUT_DATA_BUFFER — o pedido de saída.
type bufferDeSaida struct {
	buffer    uintptr
	status    uint32
	_         uint32 // alinhamento do carimbo de tempo, que é de 64 bits
	carimbo   int64
	duracao   int64
}

// Bandeira de status: ainda há material esperando para sair, então vale chamar de
// novo antes de dormir.
const temMaisSaida = 0x01000000 // DMO_OUTPUT_DATA_BUFFERF_INCOMPLETE

// ---------------------------------------------------------------------------

// CapturaComEco é o microfone passando pelo cancelador do Windows.
type CapturaComEco struct {
	objeto objeto
	buffer *BufferDeMidia

	// Sobra do bloco anterior. O cancelador entrega o tamanho que quiser, e quem
	// chama pede o tamanho que couber — sem guardar a sobra, o áudio que não coube
	// seria jogado fora e a voz sairia picotada.
	sobra []int16
}

// AbrirEntradaDeVoz é a porta única para o motor: devolve a melhor fonte de áudio
// disponível.
//
// A QUEDA PARA A CAPTURA CRUA É PARTE DO DESENHO, e não remendo. O cancelador
// depende de o Windows ter o componente registrado e de os aparelhos cooperarem;
// numa máquina onde ele não abre, a escolha certa é call com eco e não call nenhuma.
// O motivo vai para o registro, para a queda não ser silenciosa.
// A SUPRESSÃO DE RUÍDO E O GANHO CAEM JUNTO COM O ECO, e isso não é escolha nossa: no
// Windows os três moram no MESMO objeto. A captura crua é o microfone sem tratamento
// nenhum — não existe "só supressão de ruído" para oferecer. Quem desliga o eco está
// desligando os três, e a tela precisa dizer isso em vez de deixar dois interruptores
// acesos sobre um caminho que não passa por eles.
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

// AbrirCapturaComEco monta o cancelador e o deixa pronto para entregar áudio limpo.
//
// PRECISA ser chamada da mesma thread que vai ler — COM tem afinidade de thread,
// igual à captura crua.
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

	// A CONFIGURAÇÃO VEM ANTES DO FORMATO, e a ordem não é gosto: o cancelador
	// decide o que aceita como saída a partir do modo em que está. Definir o formato
	// primeiro faz ele recusar com um erro que não explica nada.
	if err := c.configurar(idAparelho, aj); err != nil {
		return nil, err
	}

	if err := c.definirFormatoDeSaida(); err != nil {
		return nil, err
	}

	if err := hr(c.objeto.chamar(moAlocarRecursos), "preparar o cancelador"); err != nil {
		return nil, err
	}

	// Um bloco generoso: o cancelador entrega o que tiver acumulado, e um buffer
	// apertado obrigaria a várias voltas para drenar o mesmo material. Oito quadros
	// de 20ms na taxa dele.
	porQuadro := taxaDoCancelador * MilissegundosPorQuadro / 1000
	c.buffer = NovoBufferDeMidia(porQuadro * CanaisDeVoz * 2 * 8)

	ok = true
	return c, nil
}

// configurar liga o modo fonte, o cancelamento de eco e o tratamento do microfone.
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

	// Aparelhos por ÍNDICE, e não pelo identificador que o resto do projeto usa.
	// -1 nos dois é "os padrão do Windows", que é o que queremos quando a pessoa não
	// escolheu nada — e também quando o escolhido não pôde ser traduzido.
	entrada, saida := indicesDe(idAparelho)
	empacotado := int32(saida)<<16 | (int32(entrada) & 0xFFFF)
	if err := escreverPropI4(loja, propIndices, empacotado); err != nil {
		return fmt.Errorf("apontar os aparelhos: %w", err)
	}

	// O PORTÃO PRIMEIRO, E SEMPRE ABERTO. Com ele fechado o cancelador ignora as duas
	// linhas seguintes e roda a configuração de fábrica — que é o estado em que os
	// interruptores da tela não mandavam em nada. Abrir sempre custa nada e garante
	// que o que está na tela é o que está no ar, inclusive quando as escolhas por
	// acaso coincidem com o padrão.
	//
	// As demais propriedades do bloco (tamanho de quadro, comprimento do eco) ficam no
	// padrão de propósito: a sonda leu 0 e 256, que são exatamente os valores que a
	// Microsoft documenta, então não escrevê-las é escolher o padrão e não esquecê-lo.
	if err := escreverPropBool(loja, propModoDeAjuste, true); err != nil {
		return fmt.Errorf("abrir o ajuste fino do cancelador: %w", err)
	}
	// I4 e não BOOL: 0 desliga, 1 é a supressão normal. Escrever um booleano aqui
	// seria um tipo errado num PROPVARIANT — recusa silenciosa, do jeito que dói.
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

// definirFormatoDeSaida pede PCM de 16 bits na taxa que o cancelador aceita.
func (c *CapturaComEco) definirFormatoDeSaida() error {
	onda := formatoPCM(taxaDoCancelador, CanaisDeVoz)
	tipo := tipoDeMidia{
		principal:        tipoAudio,
		subtipo:          subtipoPCM,
		amostraFixa:      1,
		tamanhoAmostra:   uint32(CanaisDeVoz * 2),
		tipoDoFormato:    formatoOnda,
		// 18, e não `unsafe.Sizeof(onda)` — ver a constante, que explica por quê.
		tamanhoDoFormato: tamanhoDoFormatoDeOnda,
		formato:          uintptr(unsafe.Pointer(&onda)),
	}
	// O fluxo de saída é o zero: o cancelador tem um só.
	r := c.objeto.chamar(moDefinirTipoSaida, 0, uintptr(unsafe.Pointer(&tipo)), 0)
	return hr(r, "definir o formato de saída do cancelador")
}

// Ler entrega o próximo bloco de voz JÁ SEM ECO.
func (c *CapturaComEco) Ler(destino []int16) (int, bool, error) {
	// A sobra vem primeiro: é áudio mais antigo que o que o cancelador tem agora, e
	// entregar fora de ordem embaralharia a voz.
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
		0, // sem bandeiras
		1, // um buffer de saída
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

	// Reinterpreta os bytes como amostras. O cancelador escreveu PCM de 16 bits
	// porque foi isso que pedimos no formato de saída.
	amostras := unsafe.Slice((*int16)(unsafe.Pointer(&bytes[0])), len(bytes)/2)
	n := copy(destino, amostras)
	if n < len(amostras) {
		// GUARDA O QUE NÃO COUBE, em vez de descartar. Descartar produziria uma voz
		// picotada de um jeito que parece problema de rede — e ninguém iria procurar
		// aqui.
		sobrou := amostras[n:]
		c.sobra = make([]int16, len(sobrou))
		copy(c.sobra, sobrou)
	}
	return n, false, nil
}

// Esperar dorme um pouco antes da próxima tentativa.
//
// NÃO HÁ AVISO POR EVENTO no modo fonte: o cancelador não entrega um `HANDLE` para
// esperar, então a única opção é perguntar de novo. Cinco milissegundos é metade do
// bloco que ele produz — perde-se pouca latência e não se queima processador num
// laço de pergunta contínua.
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

// ---------------------------------------------------------------------------
// Auxiliares de propriedade.

func escreverPropI4(loja objeto, chave chaveDePropriedade, valor int32) error {
	v := propvariant{tipo: 3 /* VT_I4 */, ponteiro: uintptr(uint32(valor))}
	return hr(loja.chamar(lojaEscrever,
		uintptr(unsafe.Pointer(&chave)),
		uintptr(unsafe.Pointer(&v)),
	), "escrever propriedade")
}

func escreverPropBool(loja objeto, chave chaveDePropriedade, valor bool) error {
	// VARIANT_TRUE é -1, e não 1. Escrever 1 aqui é um clássico: o objeto aceita, e
	// depois se comporta como se fosse falso.
	bruto := uintptr(0)
	if valor {
		bruto = uintptr(uint16(0xFFFF))
	}
	v := propvariant{tipo: 11 /* VT_BOOL */, ponteiro: bruto}
	return hr(loja.chamar(lojaEscrever,
		uintptr(unsafe.Pointer(&chave)),
		uintptr(unsafe.Pointer(&v)),
	), "escrever propriedade booleana")
}

// indicesDe traduz o identificador de aparelho que o app usa para o índice que o
// cancelador quer.
//
// SÃO DUAS NUMERAÇÕES DIFERENTES, e essa é a costura mais frágil do modo fonte: o
// resto do projeto identifica aparelho pelo id do Windows (estável, único), e o
// cancelador só aceita a posição na enumeração. Posição muda quando se pluga um fone.
//
// Quando não dá para traduzir, -1 manda usar o padrão do sistema — que é o
// comportamento certo, e o mesmo da captura crua.
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
