package main

// O DECODIFICADOR DE VÍDEO — o caminho de volta da transmissão.
//
// A fatia 1 fez a tela SAIR da máquina: captura, comprime, escreve na faixa. Isto é a
// outra ponta — o H.264 que chega de outra pessoa virando quadro para desenhar.
//
// MESMO SUBSISTEMA DO COMPRESSOR, e por isso este arquivo é curto: Media Foundation já
// está ligado, `objeto` já sabe chamar vtable, e os índices do `IMFTransform` já foram
// conferidos um a um. O que muda é a direção — H.264 entra, quadro sai.
//
// SAI EM NV12, E NÃO EM BGRA, e a escolha é de largura de banda entre processos.
//
// O decodificador do Windows não oferece RGB: ele entrega NV12, YV12, IYUV, YUY2 — a
// família YUV, que é o que o H.264 guarda por dentro. Converter para BGRA aqui custaria
// ou um passo a mais na placa ou um laço por pixel na CPU, e a CPU é justamente o que
// falta na máquina que este projeto quer atender.
//
// E converter aqui SAIRIA CARO DUAS VEZES: o quadro ainda precisa atravessar a fronteira
// entre este processo e a JVM, e NV12 é 1,5 byte por pixel contra 4 do BGRA. Em 720p são
// 1,3 MB contra 3,5 MB por quadro — a 30 por segundo, 40 MB/s contra 105 MB/s. Deixar em
// NV12 até o fim e converter no shader (o Astra já desenha com SkSL) é mais barato em
// CPU, em banda e em código.
//
// ORDEM DOS TIPOS, e ela é o contrário do redimensionador: aqui a ENTRADA vem primeiro e
// a saída depois, porque a lista de saídas disponíveis só existe depois de o
// decodificador saber o que vai receber. É a mesma assimetria que o `redimensionador.go`
// documenta entre ele e o compressor — três peças vizinhas do mesmo subsistema, com
// ordens diferentes.
//
// A FORMA REAL VEM DO FLUXO, e não do que pedimos. O tamanho declarado na abertura é um
// palpite: quem manda é o SPS que chega dentro do H.264. O decodificador avisa isso
// devolvendo `mudouAFormaDaSaida` na primeira saída, e é nessa hora que se lê o tamanho
// de verdade. Ignorar esse recado é receber quadro com o passo errado — imagem
// enviesada, o defeito clássico de quem confiou no palpite.

import (
	"fmt"
	"strings"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var procMFCriarBufferDeMemoria = mfplat.NewProc("MFCreateMemoryBuffer")

// MFT_CATEGORY_VIDEO_DECODER {D6C02D4B-6833-45B4-971A-05A4B04BAB91}
var catDescompressorDeVideo = guid(0xD6C02D4B, 0x6833, 0x45B4,
	[8]byte{0x97, 0x1A, 0x05, 0xA4, 0xB0, 0x4B, 0xAB, 0x91})

// IMFAttributes::GetUINT64. Vizinho do GetUINT32 na tabela — ver a lista em
// `compressor.go`, onde a ordem inteira já foi conferida pelo nome legível.
const atrPegarUINT64 = 8

// MF_MT_DEFAULT_STRIDE {644B4E48-1E02-4516-B0EB-C01CA9D49AC6} — quantos bytes tem uma
// linha do quadro. Pode ser MAIOR que a largura, e pode ser NEGATIVO (quadro de baixo
// para cima), daí a leitura passar por `int32` antes de virar `int`.
var chavePassoDaLinha = guid(0x644B4E48, 0x1E02, 0x4516,
	[8]byte{0xB0, 0xEB, 0xC0, 0x1C, 0xA9, 0xD4, 0x9A, 0xC6})

const (
	// MF_E_NO_MORE_TYPES: acabou a lista de formatos que ele oferece. NÃO é erro — é
	// como toda enumeração do Media Foundation diz "chegamos ao fim".
	naoTemMaisTipos = 0xC00D36B9

	// MF_E_NOTACCEPTING: ele tem saída pendente e não aceita entrada enquanto não for
	// drenado. Também não é erro: é a fila pedindo vez.
	naoAceitaMaisAgora = 0xC00D36B5
)

// Quadro é um quadro decodificado, em NV12.
//
// TRÊS COISAS E NÃO SÓ OS BYTES, porque nenhuma delas se deduz das outras: o passo pode
// ser MAIOR que a largura (o decodificador alinha as linhas do jeito que a placa gosta),
// e sem ele o desenho sai enviesado.
type Quadro struct {
	Dados   []byte // Y de `Altura` linhas, depois UV entrelaçado de `Altura/2` linhas
	Largura int
	Altura  int
	Passo   int // bytes por linha; >= Largura
}

// Descompressor transforma H.264 em quadros.
type Descompressor struct {
	Nome string // qual pegou, para o relatório

	t objeto // IMFTransform

	largura int
	altura  int
	passo   int

	// A amostra de saída, alocada UMA vez quando o decodificador não traz a dele.
	// Realocada quando a forma muda, que acontece no máximo uma vez por transmissão.
	saidaNossa  objeto
	bufferSaida objeto
	trazAmostra bool

	// A amostra de entrada, reaproveitada quadro a quadro: alocar 30 vezes por segundo
	// para jogar fora é lixo que o coletor vai querer discutir na pior hora.
	entrada       objeto
	bufferEntrada objeto
	capEntrada    int

	quadro Quadro
}

// AbrirDescompressor liga o decodificador de H.264 desta máquina.
//
// `largura`/`altura` são um PALPITE — o tamanho que se espera receber. Serve para o
// decodificador dimensionar o primeiro buffer; o tamanho verdadeiro chega no fluxo e
// substitui este. Zero é aceito e vira 1280x720.
//
// PRECISA RODAR NUMA THREAD PRESA com COM e Media Foundation abertos, igual ao
// compressor.
func AbrirDescompressor(largura, altura int) (*Descompressor, error) {
	if largura <= 0 || altura <= 0 {
		largura, altura = 1280, 720
	}

	lista, err := procurarDescompressores()
	if err != nil {
		return nil, err
	}
	defer SoltarCompressores(lista)
	if len(lista) == 0 {
		return nil, fmt.Errorf("nenhum decodificador de H.264 nesta máquina")
	}

	// PERCORRE OS CANDIDATOS, mesma regra do compressor: numa máquina híbrida um deles
	// pode nem ligar, e guardar a recusa de CADA um é o que impede quem lê de
	// investigar o candidato errado.
	recusas := make([]string, 0, len(lista))
	for _, cand := range lista {
		d, err := amarrarDescompressor(cand, largura, altura)
		if err == nil {
			return d, nil
		}
		recusas = append(recusas, fmt.Sprintf("%s: %v", cand.Nome, err))
	}
	return nil, fmt.Errorf("nenhum decodificador aceitou H.264 %dx%d:\n  %s",
		largura, altura, strings.Join(recusas, "\n  "))
}

func procurarDescompressores() ([]CompressorDisponivel, error) {
	entrada := tipoRegistrado{Maior: tipoMaiorVideo, Formato: formatoH264}
	saida := tipoRegistrado{Maior: tipoMaiorVideo, Formato: formatoNV12}

	var lista *objeto
	var quantos uint32
	r, _, _ := procMFTEnumEx.Call(
		uintptr(unsafe.Pointer(&catDescompressorDeVideo)),
		// SÓ SÍNCRONOS, e é a diferença mais importante entre este e o compressor.
		//
		// Descompressor assíncrono é comandado por recados, e o laço de recados do
		// compressor existe porque lá o ganho vale: comprimir é a conta cara e a placa
		// precisa de fila. Aqui o quadro chega da REDE, um de cada vez, no ritmo de
		// quem manda — não há fila para encher, e um caminho assíncrono só
		// acrescentaria uma máquina de estados que pode travar.
		uintptr(mftHardware|mftSincrono|mftOrdenaEFiltra),
		uintptr(unsafe.Pointer(&entrada)),
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&lista)),
		uintptr(unsafe.Pointer(&quantos)),
	)
	if err := hr(r, "procurar decodificadores"); err != nil {
		return nil, err
	}
	if quantos == 0 {
		return nil, nil
	}
	defer liberarMemoriaDoCOM(uintptr(unsafe.Pointer(lista)))

	achados := make([]CompressorDisponivel, 0, quantos)
	for _, a := range unsafe.Slice(lista, quantos) {
		achados = append(achados, CompressorDisponivel{
			Nome:     textoDoAtributo(a, &chaveNomeDoCompressor),
			ativador: a,
		})
	}
	return achados, nil
}

func amarrarDescompressor(cand CompressorDisponivel, largura, altura int) (*Descompressor, error) {
	t, err := cand.Montar()
	if err != nil {
		return nil, err
	}
	d := &Descompressor{Nome: cand.Nome, t: t, largura: largura, altura: altura}

	pronto := false
	defer func() {
		if !pronto {
			d.Fechar()
		}
	}()

	if err := d.definirEntrada(); err != nil {
		return nil, err
	}
	if err := d.definirSaida(); err != nil {
		return nil, err
	}
	if err := d.medirASaida(); err != nil {
		return nil, err
	}

	t.chamar(transMandarRecado, recadoComecarFluxo, 0)
	t.chamar(transMandarRecado, recadoAbrirFluxo, 0)
	pronto = true
	return d, nil
}

func (d *Descompressor) definirEntrada() error {
	var tipo objeto
	r, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "criar o tipo de entrada"); err != nil {
		return err
	}
	defer tipo.soltar()

	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formatoH264)
	definirNumero(tipo, &chaveEntrelacamento, progressivo)
	definirPar(tipo, &chaveTamanhoDoQuadro, d.largura, d.altura)

	return hr(d.t.chamar(transDefinirEntrada, 0, uintptr(tipo), 0), "aceitar H.264 na entrada")
}

// definirSaida ESCOLHE ENTRE AS QUE ELE OFERECE em vez de montar uma do zero.
//
// Montar do zero é o que o compressor faz, e lá funciona porque nós é que ditamos a
// saída. Aqui é o contrário: o decodificador tem opinião sobre o arranjo interno do
// quadro (alinhamento de linha, tamanho do buffer) e uma descrição feita à mão que não
// bata em qualquer detalhe é recusada com "tipo inválido" — mensagem que não diz QUAL
// detalhe. Pedir a lista dele e escolher a NV12 evita a adivinhação inteira.
func (d *Descompressor) definirSaida() error {
	for i := uint32(0); ; i++ {
		var tipo objeto
		r := d.t.chamar(_transTipoDeSaida, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))
		if uint32(r) == naoTemMaisTipos {
			return fmt.Errorf("ele não oferece NV12 na saída")
		}
		if err := hr(r, "listar as saídas do decodificador"); err != nil {
			return err
		}

		var formato windows.GUID
		res := tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)),
			uintptr(unsafe.Pointer(&formato)),
		)
		if uint32(res)&0x80000000 == 0 && formato == formatoNV12 {
			err := hr(d.t.chamar(transDefinirSaida, 0, uintptr(tipo), 0), "escolher NV12 na saída")
			if err == nil {
				d.lerAForma(tipo)
			}
			tipo.soltar()
			return err
		}
		tipo.soltar()
	}
}

// lerAForma pega largura, altura e passo do tipo que o decodificador aceitou.
//
// O PASSO PODE SER MAIOR QUE A LARGURA, e é o detalhe que estraga a imagem em silêncio:
// o decodificador alinha as linhas ao que a placa gosta (múltiplos de 16, 64, 256), e
// desenhar assumindo passo igual à largura produz aquela imagem enviesada em diagonal.
// Quando o atributo não está lá, o passo É a largura — que é o caso do decodificador de
// software.
func (d *Descompressor) lerAForma(tipo objeto) {
	if l, a, ok := parDoAtributo(tipo, &chaveTamanhoDoQuadro); ok && l > 0 && a > 0 {
		d.largura, d.altura = l, a
	}
	d.passo = d.largura
	if p := int(int32(numeroDoAtributo(tipo, &chavePassoDaLinha))); p > 0 {
		d.passo = p
	}
}

// medirASaida descobre se o decodificador traz a própria amostra ou se nós alocamos.
//
// Os dois casos são reais e a diferença não é de gosto: o de hardware costuma trazer (a
// memória é da placa), o de software espera receber. Alocar quando ele traz faz a
// amostra ser ignorada em silêncio; não alocar quando ele espera devolve erro na
// primeira saída.
func (d *Descompressor) medirASaida() error {
	var info infoDaSaida
	if err := hr(d.t.chamar(transInfoDaSaida, 0, uintptr(unsafe.Pointer(&info))),
		"perguntar como ele entrega o quadro"); err != nil {
		return err
	}
	d.trazAmostra = info.Bandeiras&compressorTrazAmostra != 0
	if d.trazAmostra {
		return nil
	}

	tamanho := int(info.Tamanho)
	if tamanho <= 0 {
		// NV12 são 1,5 byte por pixel: o plano Y inteiro mais um quarto de UV para cada
		// uma das duas componentes.
		tamanho = d.passo * d.altura * 3 / 2
	}
	return d.montarSaida(tamanho)
}

func (d *Descompressor) montarSaida(tamanho int) error {
	d.soltarSaida()

	var buffer objeto
	r, _, _ := procMFCriarBufferDeMemoria.Call(uintptr(tamanho), uintptr(unsafe.Pointer(&buffer)))
	if err := hr(r, "reservar o quadro de saída"); err != nil {
		return err
	}

	var amostra objeto
	r, _, _ = procMFCriarAmostra.Call(uintptr(unsafe.Pointer(&amostra)))
	if err := hr(r, "criar a amostra de saída"); err != nil {
		buffer.soltar()
		return err
	}
	if err := hr(amostra.chamar(amostraSomarBuffer, uintptr(buffer)), "amarrar o quadro à amostra"); err != nil {
		buffer.soltar()
		amostra.soltar()
		return err
	}

	d.bufferSaida, d.saidaNossa = buffer, amostra
	return nil
}

// Decodificar entrega um pedaço de H.264 e chama `receber` para cada quadro que sair.
//
// UM PEDAÇO PODE RENDER ZERO OU VÁRIOS QUADROS. Zero é o caso normal no começo — o
// decodificador precisa da sequência de parâmetros e de um quadro-chave antes de abrir
// imagem nenhuma. Vários acontece quando chega um pedaço com mais de um quadro dentro.
//
// O quadro entregue vale SÓ ATÉ A CHAMADA SEGUINTE, mesma regra do compressor: quem
// quiser guardar, copia. É o que evita alocar um megabyte e meio trinta vezes por
// segundo.
func (d *Descompressor) Decodificar(h264 []byte, quando time.Duration, receber func(Quadro)) error {
	if len(h264) == 0 {
		return nil
	}
	if err := d.encherEntrada(h264, quando); err != nil {
		return err
	}

	r := d.t.chamar(transEntrarQuadro, 0, uintptr(d.entrada), 0)
	if uint32(r) == naoAceitaMaisAgora {
		// Ele ainda tem saída pendente. Drenar e tentar de novo é o caminho certo —
		// insistir sem drenar é o que trava os dois lados.
		if err := d.drenar(receber); err != nil {
			return err
		}
		r = d.t.chamar(transEntrarQuadro, 0, uintptr(d.entrada), 0)
	}
	if err := hr(r, "entregar o H.264 ao decodificador"); err != nil {
		return err
	}
	return d.drenar(receber)
}

func (d *Descompressor) drenar(receber func(Quadro)) error {
	for {
		veio, err := d.sair(receber)
		if err != nil {
			return err
		}
		if !veio {
			return nil
		}
	}
}

func (d *Descompressor) sair(receber func(Quadro)) (bool, error) {
	saida := saidaDoCompressor{}
	if !d.trazAmostra {
		saida.Amostra = d.saidaNossa
		// O TAMANHO USADO VOLTA A ZERO A CADA VOLTA. Sem isto o buffer chega ao
		// decodificador já "cheio" da vez anterior, e ele recusa por falta de espaço —
		// erro que só aparece no SEGUNDO quadro, que é o pior lugar para procurar.
		d.bufferSaida.chamar(bufDefinirTamanho, 0)
	}

	var estado uint32
	r := d.t.chamar(transSairQuadro, 0, 1,
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&estado)),
	)
	switch uint32(r) {
	case querMaisEntrada:
		return false, nil
	case mudouAFormaDaSaida:
		// AQUI CHEGA A FORMA DE VERDADE, vinda do SPS do fluxo. É o momento em que o
		// palpite da abertura é substituído pelo que a outra pessoa está mandando de
		// fato — e é obrigatório reatender, porque o buffer que reservamos foi
		// dimensionado pelo palpite.
		if err := d.definirSaida(); err != nil {
			return false, err
		}
		if err := d.medirASaida(); err != nil {
			return false, err
		}
		return true, nil
	}
	if err := hr(r, "puxar o quadro decodificado"); err != nil {
		return false, err
	}
	if saida.Amostra == 0 {
		return false, nil
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}
	// A amostra só é NOSSA para soltar quando foi ELE quem a trouxe. Soltar a que nós
	// alocamos aqui a destruiria antes da próxima volta.
	if d.trazAmostra {
		defer saida.Amostra.soltar()
	}

	var buffer objeto
	if r := saida.Amostra.chamar(amostraJuntarBuffers, uintptr(unsafe.Pointer(&buffer))); uint32(r)&0x80000000 != 0 {
		return false, hr(r, "juntar os pedaços do quadro")
	}
	defer buffer.soltar()

	var p uintptr
	var maximo, atual uint32
	r = buffer.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)),
		uintptr(unsafe.Pointer(&maximo)),
		uintptr(unsafe.Pointer(&atual)),
	)
	if err := hr(r, "abrir o quadro para leitura"); err != nil {
		return false, err
	}
	if atual > 0 {
		if cap(d.quadro.Dados) < int(atual) {
			d.quadro.Dados = make([]byte, atual)
		}
		d.quadro.Dados = d.quadro.Dados[:atual]
		copy(d.quadro.Dados, unsafe.Slice((*byte)(unsafe.Pointer(p)), atual))
	} else {
		d.quadro.Dados = d.quadro.Dados[:0]
	}
	buffer.chamar(bufDestrancar)

	if len(d.quadro.Dados) == 0 {
		return true, nil
	}
	d.quadro.Largura, d.quadro.Altura, d.quadro.Passo = d.largura, d.altura, d.passo
	if receber != nil {
		receber(d.quadro)
	}
	return true, nil
}

// encherEntrada põe os bytes numa amostra, reaproveitando o buffer quando cabe.
func (d *Descompressor) encherEntrada(h264 []byte, quando time.Duration) error {
	if d.capEntrada < len(h264) {
		d.soltarEntrada()
		// Folga de 50% para não realocar a cada quadro-chave, que é sempre maior que
		// os outros: sem folga, todo quadro-chave pagaria uma alocação.
		novo := len(h264) * 3 / 2
		var buffer objeto
		r, _, _ := procMFCriarBufferDeMemoria.Call(uintptr(novo), uintptr(unsafe.Pointer(&buffer)))
		if err := hr(r, "reservar a entrada do decodificador"); err != nil {
			return err
		}
		var amostra objeto
		r, _, _ = procMFCriarAmostra.Call(uintptr(unsafe.Pointer(&amostra)))
		if err := hr(r, "criar a amostra de entrada"); err != nil {
			buffer.soltar()
			return err
		}
		if err := hr(amostra.chamar(amostraSomarBuffer, uintptr(buffer)), "amarrar a entrada"); err != nil {
			buffer.soltar()
			amostra.soltar()
			return err
		}
		d.bufferEntrada, d.entrada, d.capEntrada = buffer, amostra, novo
	}

	var p uintptr
	var maximo, atual uint32
	r := d.bufferEntrada.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)),
		uintptr(unsafe.Pointer(&maximo)),
		uintptr(unsafe.Pointer(&atual)),
	)
	if err := hr(r, "abrir a entrada para escrita"); err != nil {
		return err
	}
	copy(unsafe.Slice((*byte)(unsafe.Pointer(p)), maximo), h264)
	d.bufferEntrada.chamar(bufDestrancar)
	if err := hr(d.bufferEntrada.chamar(bufDefinirTamanho, uintptr(len(h264))),
		"marcar o tamanho da entrada"); err != nil {
		return err
	}

	const porSegundo = 10_000_000
	d.entrada.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))
	return nil
}

func (d *Descompressor) soltarSaida() {
	if d.saidaNossa != 0 {
		d.saidaNossa.soltar()
		d.saidaNossa = 0
	}
	if d.bufferSaida != 0 {
		d.bufferSaida.soltar()
		d.bufferSaida = 0
	}
}

func (d *Descompressor) soltarEntrada() {
	if d.entrada != 0 {
		d.entrada.soltar()
		d.entrada = 0
	}
	if d.bufferEntrada != 0 {
		d.bufferEntrada.soltar()
		d.bufferEntrada = 0
	}
	d.capEntrada = 0
}

func (d *Descompressor) Fechar() {
	if d.t != 0 {
		d.t.chamar(transMandarRecado, recadoEncerrarFluxo, 0)
		d.t.soltar()
		d.t = 0
	}
	d.soltarSaida()
	d.soltarEntrada()
}

// parDoAtributo lê um atributo de 64 bits que carrega DOIS inteiros de 32.
//
// É como o Media Foundation guarda tamanho de quadro e proporção: a parte alta é o
// primeiro número. Ler como um só devolve um número gigante que não significa nada — e
// não dá erro.
func parDoAtributo(a objeto, chave *windows.GUID) (int, int, bool) {
	var v uint64
	r := a.chamar(atrPegarUINT64,
		uintptr(unsafe.Pointer(chave)),
		uintptr(unsafe.Pointer(&v)),
	)
	if uint32(r)&0x80000000 != 0 {
		return 0, 0, false
	}
	return int(v >> 32), int(v & 0xFFFFFFFF), true
}
