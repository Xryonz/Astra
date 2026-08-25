package main

import (
	"fmt"
	"os"
	"runtime"
	"strings"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	procMFCriarGerenciador   = mfplat.NewProc("MFCreateDXGIDeviceManager")
	procMFBufferDeSuperficie = mfplat.NewProc("MFCreateDXGISurfaceBuffer")
)

var (
	iidGerenciadorDXGI = guid(0xEB533D5D, 0x2DB6, 0x40F8,
		[8]byte{0x97, 0xA9, 0x49, 0x46, 0x92, 0x01, 0x4F, 0x07})

	iidGeradorDeEventos = guid(0x2CD0BD52, 0xBCD5, 0x4B89,
		[8]byte{0xB6, 0x2C, 0xEA, 0xDC, 0x0C, 0x03, 0x1E, 0x7D})

	iidBuffer2D = guid(0x7DC9D5F9, 0x9ED9, 0x44EC,
		[8]byte{0x9B, 0xBF, 0x06, 0x00, 0xBB, 0x58, 0x9F, 0xBB})

	formatoARGB32 = guid(21, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	chaveAssincrono = guid(0xF81CFDCC, 0xD5C2, 0x4D5F,
		[8]byte{0xBA, 0xF3, 0x9F, 0x9B, 0x2E, 0x27, 0xC0, 0xAE})
)

const (
	gerTrocarDispositivo = 7

	amostraDefinirTempo   = 36
	amostraDefinirDuracao = 38
	amostraJuntarBuffers  = 41
	amostraSomarBuffer    = 42

	bufTrancar        = 3
	bufDestrancar     = 4
	bufTamanhoAtual   = 5
	bufDefinirTamanho = 6

	buf2DTrancar         = 3
	buf2DDestrancar      = 4
	buf2DTamanhoContiguo = 7

	geradorPegarEvento = 3

	eventoTipo = 33
)

const (
	d3dCriarTextura2D = 5

	d3dMapear     = 14
	d3dDesmapear  = 15
	d3dCopiarTudo = 47
)

const (
	recadoDefinirD3D    = 2
	recadoComecarFluxo  = 0x10000000
	recadoEncerrarFluxo = 0x10000001
	recadoAbrirFluxo    = 0x10000003

	compressorTrazAmostra = 0x100

	querMaisEntrada = 0xC00D6D72

	mudouAFormaDaSaida = 0xC00D6D61

	eventoQuerEntrada = 601
	eventoTemSaida    = 602
)

type descricaoDeTextura struct {
	Largura        uint32
	Altura         uint32
	Niveis         uint32
	Camadas        uint32
	Formato        uint32
	AmostrasConta  uint32
	AmostrasQualid uint32
	Uso            uint32
	Amarracao      uint32
	AcessoDaCPU    uint32
	Diversos       uint32
}

const (
	formatoBGRA        = 87
	usoPadrao          = 0
	amarrarComoAlvo    = 0x20
	amarrarComoTextura = 0x8
	usoDeLeitura       = 3
	cpuPodeLer         = 0x20000
	mapaDeLeitura      = 1
)

type saidaDoCompressor struct {
	Fluxo   uint32
	_       uint32
	Amostra objeto
	Estado  uint32
	_       uint32
	Eventos objeto
}

type infoDaSaida struct {
	Bandeiras   uint32
	Tamanho     uint32
	Alinhamento uint32
}

type mapaDaTextura struct {
	Dados      uintptr
	PassoLinha uint32
	PassoFundo uint32
}

type quadroDeEntrada struct {
	textura objeto
	buffer  objeto
	amostra objeto
}

func (q quadroDeEntrada) soltar() {
	q.amostra.soltar()
	q.buffer.soltar()
	q.textura.soltar()
}

type Compressor struct {
	Nome    string
	Formato string

	Assincrono bool

	NaMemoria bool

	t        objeto
	eventos  objeto
	gerente  objeto
	contexto objeto

	comandos objeto

	pedidos int

	anel    []quadroDeEntrada
	proximo int

	reduzir *Redimensionador

	pendente objeto

	saidaNossa  objeto
	bufferSaida objeto
	trazAmostra bool

	largura int
	altura  int

	saidaL int
	saidaA int
	fps    int
	kbps   int

	saida []byte

	espelho *Espelho

	Custos Custos
}

type Custos struct {
	Copia      time.Duration
	Reducao    time.Duration
	Compressao time.Duration
	Leitura    time.Duration
	Quadros    int

	PedidoDeEntrada time.Duration
	SaidaPronta     time.Duration
}

func (c Custos) Media() Custos {
	if c.Quadros == 0 {
		return Custos{}
	}
	n := time.Duration(c.Quadros)
	return Custos{
		Copia:           c.Copia / n,
		Reducao:         c.Reducao / n,
		Compressao:      c.Compressao / n,
		Leitura:         c.Leitura / n,
		PedidoDeEntrada: c.PedidoDeEntrada / n,
		SaidaPronta:     c.SaidaPronta / n,
		Quadros:         c.Quadros,
	}
}

func (c Custos) Total() time.Duration { return c.Copia + c.Reducao + c.Compressao + c.Leitura }

type Ritmo struct {
	intervalo time.Duration
	proximo   time.Time
}

func NovoRitmo(fps int) *Ritmo {
	if fps <= 0 {
		fps = 60
	}
	return &Ritmo{intervalo: time.Second / time.Duration(fps), proximo: time.Now()}
}

func (r *Ritmo) Esperar() {
	agora := time.Now()
	if espera := r.proximo.Sub(agora); espera > 0 {
		time.Sleep(espera)
		r.proximo = r.proximo.Add(r.intervalo)
		return
	}
	r.proximo = agora.Add(r.intervalo)
}

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

var AsTaxasQueOAstraOferece = []int{60, 30, 15}

func TaxaQueCabe(custo time.Duration, teto int) int {
	if teto <= 0 {
		teto = AsTaxasQueOAstraOferece[0]
	}

	if custo <= 0 {
		return teto
	}
	menor := teto
	for _, taxa := range AsTaxasQueOAstraOferece {
		if taxa > teto {
			continue
		}
		menor = taxa
		if custo*2 <= time.Second/time.Duration(taxa) {
			return taxa
		}
	}

	return menor
}

func tetoDeSoftware(largura, altura int) (int, int) {
	const teto = 720
	if altura <= teto {
		return largura, altura
	}

	return (largura * teto / altura) &^ 1, teto &^ 1
}

func AbrirCompressor(tela *Tela, saidaL, saidaA, fps, kbps int) (*Compressor, error) {
	largura, altura := tela.Tamanho()
	if largura <= 0 || altura <= 0 {
		return nil, fmt.Errorf("a captura não sabe o tamanho da tela")
	}
	if saidaL <= 0 || saidaA <= 0 {
		saidaL, saidaA = largura, altura
	}

	if fps <= 0 {
		fps = tela.Hz()
	}
	if fps <= 0 || fps > 60 {
		fps = 60
	}

	lista, err := ProcurarCompressores()
	if err != nil {
		return nil, err
	}
	defer SoltarCompressores(lista)
	if len(lista) == 0 {
		return nil, fmt.Errorf("nenhum compressor de H.264 nesta máquina")
	}

	recusas := make([]string, 0, 2*len(lista))
	for _, cand := range lista {
		c, err := amarrar(cand, tela, largura, altura, saidaL, saidaA, fps, kbps, false)
		if err == nil {
			return c, nil
		}
		recusas = append(recusas, fmt.Sprintf("%s (na placa): %v", cand.Nome, err))
	}

	memL, memA := tetoDeSoftware(saidaL, saidaA)
	for _, cand := range lista {
		c, err := amarrar(cand, tela, largura, altura, memL, memA, fps, kbps, true)
		if err == nil {
			return c, nil
		}
		recusas = append(recusas, fmt.Sprintf("%s (na memória): %v", cand.Nome, err))
	}
	return nil, fmt.Errorf("nenhum compressor aceitou a textura da captura:\n  %s",
		strings.Join(recusas, "\n  "))
}

func amarrar(cand CompressorDisponivel, tela *Tela, largura, altura, saidaL, saidaA, fps, kbps int, naMemoria bool) (*Compressor, error) {
	t, err := cand.Montar()
	if err != nil {
		return nil, err
	}
	c := &Compressor{
		Nome:      cand.Nome,
		NaMemoria: naMemoria,
		t:         t,
		contexto:  tela.contexto,
		largura:   largura,
		altura:    altura,
		saidaL:    saidaL,
		saidaA:    saidaA,
		fps:       fps,
		kbps:      kbps,
	}

	pronto := false
	defer func() {
		if !pronto {
			c.Fechar()
		}
	}()

	if err := destrancarSeAssincrono(t); err != nil {
		return nil, err
	}

	if err := c.criarGerenciador(tela.dispositivo); err != nil {
		return nil, err
	}
	if !naMemoria {
		if err := c.entregarODispositivo(); err != nil {
			return nil, err
		}
	}
	if err := configurarSaida(t, saidaL, saidaA, fps, kbps); err != nil {
		return nil, err
	}

	formatoDaEntrada := formatoARGB32
	if naMemoria {
		formatoDaEntrada = formatoNV12
	}
	if err := c.definirEntrada(formatoDaEntrada); err != nil {
		return nil, err
	}

	if naMemoria || saidaL != largura || saidaA != altura {
		rd, err := AbrirRedimensionador(c.gerente, largura, altura, saidaL, saidaA, formatoDaEntrada)
		if err != nil {
			return nil, err
		}
		c.reduzir = rd
	}

	if api, err := t.consultar(&iidCodecAPI); err == nil {
		c.comandos = api
	}
	if err := c.abrirGeradorSeAssincrono(); err != nil {
		return nil, err
	}
	if err := c.medirASaida(); err != nil {
		return nil, err
	}
	if err := c.montarAnel(tela.dispositivo); err != nil {
		return nil, err
	}

	c.t.chamar(transMandarRecado, recadoComecarFluxo, 0)
	c.t.chamar(transMandarRecado, recadoAbrirFluxo, 0)
	pronto = true
	return c, nil
}

func (c *Compressor) criarGerenciador(dispositivo objeto) error {
	var ficha uint32
	r, _, _ := procMFCriarGerenciador.Call(
		uintptr(unsafe.Pointer(&ficha)),
		uintptr(unsafe.Pointer(&c.gerente)),
	)
	if err := hr(r, "criar o gerenciador de vídeo"); err != nil {
		return err
	}
	r = c.gerente.chamar(gerTrocarDispositivo, uintptr(dispositivo), uintptr(ficha))
	return hr(r, "entregar a placa ao gerenciador")
}

func (c *Compressor) entregarODispositivo() error {
	r := c.t.chamar(transMandarRecado, recadoDefinirD3D, uintptr(c.gerente))
	return hr(r, "dizer ao compressor qual é a placa")
}

func (c *Compressor) definirEntrada(formato windows.GUID) error {
	nome := "ARGB32"
	if formato == formatoNV12 {
		nome = "NV12"
	}
	for i := uint32(0); i < 64; i++ {
		var tipo objeto
		r := c.t.chamar(transTipoDeEntrada, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))
		if uint32(r)&0x80000000 != 0 {
			break
		}
		var sub windows.GUID
		lido := tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)),
			uintptr(unsafe.Pointer(&sub)),
		)
		if uint32(lido)&0x80000000 != 0 || sub != formato {
			tipo.soltar()
			continue
		}

		definirNumero(tipo, &chaveEntrelacamento, progressivo)
		definirPar(tipo, &chaveTamanhoDoQuadro, c.saidaL, c.saidaA)
		definirPar(tipo, &chaveTaxaDeQuadros, c.fps, 1)

		r = c.t.chamar(transDefinirEntrada, 0, uintptr(tipo), 0)
		tipo.soltar()
		if err := hr(r, "amarrar a entrada em "+nome); err != nil {
			return err
		}
		c.Formato = nome
		return nil
	}
	return fmt.Errorf("não aceita %s na entrada", nome)
}

func (c *Compressor) abrirGeradorSeAssincrono() error {
	g, err := c.t.consultar(&iidGeradorDeEventos)
	if err != nil {
		return nil
	}
	c.eventos = g
	c.Assincrono = true
	return nil
}

func (c *Compressor) medirASaida() error {
	var info infoDaSaida
	r := c.t.chamar(transInfoDaSaida, 0, uintptr(unsafe.Pointer(&info)))
	if err := hr(r, "perguntar como sai o H.264"); err != nil {
		return err
	}
	c.trazAmostra = info.Bandeiras&compressorTrazAmostra != 0
	if c.trazAmostra {
		return nil
	}

	c.soltarSaidaNossa()

	tamanho := int(info.Tamanho)
	if tamanho <= 0 {

		tamanho = c.saidaL * c.saidaA * 3 / 2
	}

	var buffer objeto
	r, _, _ = procMFCriarBufferDeMemoria.Call(uintptr(tamanho), uintptr(unsafe.Pointer(&buffer)))
	if err := hr(r, "reservar a saída do compressor"); err != nil {
		return err
	}
	var amostra objeto
	r, _, _ = procMFCriarAmostra.Call(uintptr(unsafe.Pointer(&amostra)))
	if err := hr(r, "criar a amostra de saída"); err != nil {
		buffer.soltar()
		return err
	}
	if err := hr(amostra.chamar(amostraSomarBuffer, uintptr(buffer)), "amarrar a saída à amostra"); err != nil {
		buffer.soltar()
		amostra.soltar()
		return err
	}
	c.bufferSaida, c.saidaNossa = buffer, amostra
	return nil
}

func (c *Compressor) montarAnel(dispositivo objeto) error {
	const quantas = 3
	desc := descricaoDeTextura{
		Largura:       uint32(c.largura),
		Altura:        uint32(c.altura),
		Niveis:        1,
		Camadas:       1,
		Formato:       formatoBGRA,
		AmostrasConta: 1,
		Uso:           usoPadrao,

		Amarracao: amarrarComoAlvo | amarrarComoTextura,
	}
	for i := 0; i < quantas; i++ {
		q, err := c.embrulharUmQuadro(dispositivo, desc)
		if err != nil {
			return err
		}
		c.anel = append(c.anel, q)
	}
	return nil
}

func (c *Compressor) embrulharUmQuadro(dispositivo objeto, desc descricaoDeTextura) (quadroDeEntrada, error) {
	var q quadroDeEntrada

	r := dispositivo.chamar(d3dCriarTextura2D,
		uintptr(unsafe.Pointer(&desc)), 0, uintptr(unsafe.Pointer(&q.textura)))
	if err := hr(r, "criar a textura de trabalho"); err != nil {
		return q, err
	}

	r, _, _ = procMFBufferDeSuperficie.Call(
		uintptr(unsafe.Pointer(&iidTextura2D)),
		uintptr(q.textura),
		0,
		0,
		uintptr(unsafe.Pointer(&q.buffer)),
	)
	if err := hr(r, "embrulhar a textura para o compressor"); err != nil {
		q.soltar()
		return q, err
	}

	if b2d, err := q.buffer.consultar(&iidBuffer2D); err == nil {
		var tamanho uint32
		if b2d.chamar(buf2DTamanhoContiguo, uintptr(unsafe.Pointer(&tamanho)))&0x80000000 == 0 {
			q.buffer.chamar(bufDefinirTamanho, uintptr(tamanho))
		}
		b2d.soltar()
	}

	r, _, _ = procMFCriarAmostra.Call(uintptr(unsafe.Pointer(&q.amostra)))
	if err := hr(r, "criar a amostra"); err != nil {
		q.soltar()
		return q, err
	}
	r = q.amostra.chamar(amostraSomarBuffer, uintptr(q.buffer))
	if err := hr(r, "juntar o buffer à amostra"); err != nil {
		q.soltar()
		return q, err
	}
	return q, nil
}

func (c *Compressor) LigarEspelho(mandar func(Quadro)) {
	if c == nil || c.espelho != nil {
		return
	}
	e, err := AbrirEspelho(c.gerente, c.largura, c.altura, mandar)
	if err != nil {
		fmt.Fprintf(os.Stderr, "sem prévia da própria tela: %v\n", err)
		return
	}
	c.espelho = e
}

func (c *Compressor) Comprimir(textura objeto, quando time.Duration, receber func([]byte)) error {
	q := c.anel[c.proximo]
	c.proximo = (c.proximo + 1) % len(c.anel)
	c.Custos.Quadros++

	marco := time.Now()
	c.contexto.chamar(d3dCopiarTudo, uintptr(q.textura), uintptr(textura))
	c.Custos.Copia += time.Since(marco)

	const porSegundo = 10_000_000
	marcarTempo := func(a objeto) {
		a.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))
		a.chamar(amostraDefinirDuracao, uintptr(porSegundo/int64(c.fps)))
	}
	marcarTempo(q.amostra)

	c.espelho.Talvez(q.amostra)

	if c.NaMemoria {
		return c.comprimirNaMemoria(q.amostra, quando, marcarTempo, receber)
	}

	entrada := q.amostra
	if c.reduzir != nil {
		marco = time.Now()
		menor, err := c.reduzir.Reduzir(q.amostra)
		c.Custos.Reducao += time.Since(marco)
		if err != nil {
			return err
		}
		if menor == 0 {
			return nil
		}
		defer menor.soltar()

		marcarTempo(menor)
		entrada = menor
	}

	marco = time.Now()
	defer func() { c.Custos.Compressao += time.Since(marco) }()

	if c.eventos == 0 {
		if err := c.entrar(entrada); err != nil {
			return err
		}
		return c.esvaziar(receber)
	}

	if err := c.pedidoDeEntrada(receber); err != nil {
		return err
	}
	if err := c.entrar(entrada); err != nil {
		return err
	}
	return c.Drenar(receber)
}

func (c *Compressor) comprimirNaMemoria(quadro objeto, quando time.Duration, marcarTempo func(objeto), receber func([]byte)) error {
	marco := time.Now()
	nova, err := c.reduzir.Reduzir(quadro)
	c.Custos.Reducao += time.Since(marco)
	if err != nil {
		return err
	}
	if nova != 0 {

		marcarTempo(nova)
	}

	if err := c.entregarPendente(receber); err != nil {
		return err
	}
	c.pendente = nova
	return nil
}

func (c *Compressor) entregarPendente(receber func([]byte)) error {
	p := c.pendente
	if p == 0 {
		return nil
	}
	c.pendente = 0
	defer p.soltar()

	marco := time.Now()
	defer func() { c.Custos.Compressao += time.Since(marco) }()

	if c.eventos != 0 {
		if err := c.pedidoDeEntrada(receber); err != nil {
			return err
		}
	}
	if err := c.entrar(p); err != nil {
		return err
	}
	return c.drenarFila(receber)
}

func (c *Compressor) pedidoDeEntrada(receber func([]byte)) error {
	for c.pedidos == 0 {
		antes := time.Now()
		tipo, err := c.proximoRecado()
		if err != nil {
			return err
		}
		espera := time.Since(antes)
		switch tipo {
		case eventoQuerEntrada:
			c.Custos.PedidoDeEntrada += espera
			c.pedidos++
		case eventoTemSaida:
			c.Custos.SaidaPronta += espera
			if _, err := c.sair(receber); err != nil {
				return err
			}
		}
	}
	c.pedidos--
	return nil
}

func (c *Compressor) Drenar(receber func([]byte)) error {

	if c.NaMemoria {
		if err := c.entregarPendente(receber); err != nil {
			return err
		}
	}
	return c.drenarFila(receber)
}

func (c *Compressor) drenarFila(receber func([]byte)) error {
	if c.eventos == 0 {

		return c.esvaziar(receber)
	}
	for {
		tipo, tem, err := c.recadoSeHouver()
		if err != nil {
			return err
		}
		if !tem {
			return nil
		}
		switch tipo {
		case eventoQuerEntrada:
			c.pedidos++
		case eventoTemSaida:
			if _, err := c.sair(receber); err != nil {
				return err
			}
		}
	}
}

func (c *Compressor) entrar(amostra objeto) error {
	r := c.t.chamar(transEntrarQuadro, 0, uintptr(amostra), 0)
	return hr(r, "entregar o quadro ao compressor")
}

func (c *Compressor) esvaziar(receber func([]byte)) error {
	for {
		veio, err := c.sair(receber)
		if err != nil {
			return err
		}
		if !veio {
			return nil
		}
	}
}

func (c *Compressor) sair(receber func([]byte)) (bool, error) {

	marco := time.Now()
	defer func() {
		gasto := time.Since(marco)
		c.Custos.Leitura += gasto
		c.Custos.Compressao -= gasto
	}()

	var saida saidaDoCompressor
	if !c.trazAmostra {
		saida.Amostra = c.saidaNossa

		c.bufferSaida.chamar(bufDefinirTamanho, 0)
	}

	var estado uint32
	r := c.t.chamar(transSairQuadro, 0, 1,
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&estado)),
	)
	switch uint32(r) {
	case querMaisEntrada:
		return false, nil
	case mudouAFormaDaSaida:

		if err := configurarSaida(c.t, c.saidaL, c.saidaA, c.fps, c.kbps); err != nil {
			return false, err
		}

		if !c.trazAmostra {
			return false, c.medirASaida()
		}
		return false, nil
	}
	if err := hr(r, "puxar o H.264"); err != nil {
		return false, err
	}
	if saida.Amostra == 0 {
		return false, nil
	}

	if c.trazAmostra {
		defer saida.Amostra.soltar()
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}

	var buffer objeto
	if r := saida.Amostra.chamar(amostraJuntarBuffers, uintptr(unsafe.Pointer(&buffer))); uint32(r)&0x80000000 != 0 {
		return false, hr(r, "juntar os pedaços da saída")
	}
	defer buffer.soltar()

	var p uintptr
	var maximo, atual uint32
	r = buffer.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)),
		uintptr(unsafe.Pointer(&maximo)),
		uintptr(unsafe.Pointer(&atual)),
	)
	if err := hr(r, "abrir o H.264 para leitura"); err != nil {
		return false, err
	}
	if atual > 0 {
		if cap(c.saida) < int(atual) {
			c.saida = make([]byte, atual)
		}
		c.saida = c.saida[:atual]
		copy(c.saida, unsafe.Slice((*byte)(unsafe.Pointer(p)), atual))
	} else {
		c.saida = c.saida[:0]
	}
	buffer.chamar(bufDestrancar)

	if len(c.saida) > 0 && receber != nil {
		receber(c.saida)
	}
	return true, nil
}

const (
	recadoSemEsperar = 0x00000001
	semRecadoNaFila  = 0xC00D3E80
)

func (c *Compressor) recadoSeHouver() (uint32, bool, error) {
	var ev objeto
	r := c.eventos.chamar(geradorPegarEvento, recadoSemEsperar, uintptr(unsafe.Pointer(&ev)))
	if uint32(r) == semRecadoNaFila {
		return 0, false, nil
	}
	if err := hr(r, "perguntar ao compressor"); err != nil {
		return 0, false, err
	}
	defer ev.soltar()

	var tipo uint32
	r = ev.chamar(eventoTipo, uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "ler o recado do compressor"); err != nil {
		return 0, false, err
	}
	return tipo, true, nil
}

func (c *Compressor) proximoRecado() (uint32, error) {
	var ev objeto
	r := c.eventos.chamar(geradorPegarEvento, 0, uintptr(unsafe.Pointer(&ev)))
	if err := hr(r, "esperar o compressor"); err != nil {
		return 0, err
	}
	defer ev.soltar()

	var tipo uint32
	r = ev.chamar(eventoTipo, uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "ler o recado do compressor"); err != nil {
		return 0, err
	}
	return tipo, nil
}

func (c *Compressor) Fechar() {
	if c.comandos != 0 {
		c.comandos.soltar()
		c.comandos = 0
	}
	if c.t != 0 {
		c.t.chamar(transMandarRecado, recadoEncerrarFluxo, 0)
	}

	if c.pendente != 0 {
		c.pendente.soltar()
		c.pendente = 0
	}
	if c.reduzir != nil {
		c.reduzir.Fechar()
		c.reduzir = nil
	}
	c.espelho.Fechar()
	c.espelho = nil
	c.soltarSaidaNossa()
	for _, q := range c.anel {
		q.soltar()
	}
	c.anel = nil
	c.eventos.soltar()
	c.gerente.soltar()
	c.t.soltar()
	c.eventos, c.gerente, c.t = 0, 0, 0

	c.contexto = 0
}

func (c *Compressor) soltarSaidaNossa() {
	if c.saidaNossa != 0 {
		c.saidaNossa.soltar()
		c.saidaNossa = 0
	}
	if c.bufferSaida != 0 {
		c.bufferSaida.soltar()
		c.bufferSaida = 0
	}
}

var iidCodecAPI = guid(0x901DB4C7, 0x31CE, 0x41A2,
	[8]byte{0x85, 0xDC, 0x8F, 0xA0, 0xBF, 0x41, 0xB8, 0xDA})

var chaveForcarQuadroChave = guid(0x398C1B98, 0x8353, 0x475A,
	[8]byte{0x9E, 0xF2, 0x8F, 0x26, 0x5D, 0x26, 0x03, 0x45})

var chaveBandaMediaDoCodec = guid(0xF7222374, 0x2144, 0x4815,
	[8]byte{0xB5, 0x50, 0xA3, 0x7F, 0x8E, 0x12, 0xEE, 0x52})

const (
	_codecSuportado   = 3
	_codecModificavel = 4
	codecDefinirValor = 9
)

type variante struct {
	tipo  uint16
	_     [3]uint16
	valor uintptr
	_     uintptr
}

const varInteiroSemSinal = 19

func (c *Compressor) ForcarQuadroChave() bool {
	if c.comandos == 0 {
		return false
	}
	v := variante{tipo: varInteiroSemSinal, valor: 1}
	chave := chaveForcarQuadroChave
	r := c.comandos.chamar(codecDefinirValor,
		uintptr(unsafe.Pointer(&chave)),
		uintptr(unsafe.Pointer(&v)),
	)
	return uint32(r)&0x80000000 == 0
}

func definirGUID(a objeto, chave *windows.GUID, valor windows.GUID) {
	a.chamar(atrDefinirGUID, uintptr(unsafe.Pointer(chave)), uintptr(unsafe.Pointer(&valor)))
}

func definirNumero(a objeto, chave *windows.GUID, valor uint32) {
	a.chamar(atrDefinirUINT32, uintptr(unsafe.Pointer(chave)), uintptr(valor))
}

func definirPar(a objeto, chave *windows.GUID, alto, baixo int) {
	a.chamar(atrDefinirUINT64,
		uintptr(unsafe.Pointer(chave)),
		uintptr(uint64(alto)<<32|uint64(uint32(baixo))),
	)
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
		err := c.Comprimir(textura, time.Since(comeco), func(nal []byte) {
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
