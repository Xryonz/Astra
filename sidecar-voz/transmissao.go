package main

// A TRANSMISSÃO DE PONTA A PONTA — textura entra, H.264 sai, e o quadro nunca desce.
//
// `tela.go` entrega uma `ID3D11Texture2D`. `compressor.go` já provou que existe nesta
// máquina um compressor que aceita a textura onde ela está, e em ARGB32, que é
// exatamente o que a duplicação produz. Este arquivo é o cano entre os dois.
//
// A ÚNICA CÓPIA DO CAMINHO, e por que ela PRECISA existir. A textura que a duplicação
// entrega vale até `ReleaseFrame` — guardar uma referência não basta, porque o DXGI
// reaproveita a superfície por baixo, e o compressor assíncrono enfileira o quadro
// para devolver depois. Então o quadro é copiado para uma textura NOSSA antes de o
// original ser devolvido.
//
// Isso NÃO desfaz a migração. A cópia é de memória de vídeo para memória de vídeo:
// 1080p em BGRA são 8 MB contra centenas de GB/s de banda dentro da placa, ou seja,
// dezenas de microssegundos. O que custava 0,84 núcleo era ATRAVESSAR o barramento
// até a memória principal, e isso continua não acontecendo.
//
// TRÊS TEXTURAS EM RODÍZIO, e não uma. Um compressor assíncrono pode estar segurando
// dois ou três quadros ao mesmo tempo; escrever sempre na mesma textura sobrescreveria
// um que ele ainda não leu. O defeito daí não é travamento — é imagem rasgada de vez
// em quando, que se confunde com problema de rede e some em qualquer teste curto.
//
// O TAMANHO DA SAÍDA sai de `AlvoDeSaida`: 1080p a dois, 720p com três ou mais. É
// conta de banda da malha, não gosto — a subida é gasta uma vez por pessoa.
//
// Quando o alvo difere da tela, entra o `Redimensionador` (ver o arquivo dele), e ele
// precisou existir: a sonda perguntou se o compressor reduzia sozinho, como já havia
// perguntado sobre cor, e desta vez a resposta foi não —
//
//	Intel Quick Sync (x2)   entrada 1080p com saída 720p: "tipo de mídia inválido"
//	Microsoft AVC DX12      recusa a própria saída em 1280x720
//
// O QUE ESTE CANO CUSTA, medido três vezes nesta máquina para não confundir ruído com
// resultado — o que já aconteceu duas vezes aqui:
//
//	                 custo por quadro        banda        processador
//	1080p nativo     7,36 / 6,13 / 7,19ms    2679 kbps    0,06 a 0,12 núcleos
//	720p reduzido    6,78 / 7,64 / 6,74ms    1691 kbps    0,08 a 0,11 núcleos
//
//	orçamento a 60 por segundo: 16,67ms
//
// E O TEMPO VAI QUASE TODO NUM LUGAR SÓ:
//
//	copiar na placa        7 µs
//	reduzir              0-150 µs
//	comprimir           ~7000 µs   <- esperar a placa terminar
//	ler os NALs          20-60 µs
//
// Duas conclusões, e as duas mudam o que faz sentido otimizar.
//
// PRIMEIRA: o código deste arquivo custa uns 50 a 200 microssegundos por quadro. Todo
// o resto é ESPERA, e esperar não queima processador — a thread fica parada num evento
// do Windows. Por isso 7ms de relógio por quadro custam só um décimo de núcleo. Não há
// o que espremer aqui; o gargalo, quando houver, será da placa.
//
// SEGUNDA: reduzir para 720p NÃO economiza tempo nesta máquina — as duas colunas se
// confundem dentro do ruído. Economiza BANDA, que era o motivo de existir. Numa
// máquina cuja placa seja o gargalo a conta muda, e é lá que a redução vai pagar duas
// vezes.
//
// COMO NÃO MEDIR ISTO, porque eu errei assim duas vezes: a TAXA DE QUADROS não serve.
// `ProximoQuadro` espera a tela mudar, então ela mede o Windows e não o Astra — área
// parada rende poucos quadros, jogo rende muitos, e o mesmo código deu 79/s numa hora
// e 44/s noutra. O custo por quadro e o processador consumido são da máquina.

import (
	"fmt"
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
	// IID_IMFDXGIDeviceManager {EB533D5D-2DB6-40F8-97A9-494692014F07}
	iidGerenciadorDXGI = guid(0xEB533D5D, 0x2DB6, 0x40F8,
		[8]byte{0x97, 0xA9, 0x49, 0x46, 0x92, 0x01, 0x4F, 0x07})

	// IID_IMFMediaEventGenerator {2CD0BD52-BCD5-4B89-B62C-EADC0C031E7D}
	iidGeradorDeEventos = guid(0x2CD0BD52, 0xBCD5, 0x4B89,
		[8]byte{0xB6, 0x2C, 0xEA, 0xDC, 0x0C, 0x03, 0x1E, 0x7D})

	// IID_IMF2DBuffer {7DC9D5F9-9ED9-44EC-9BBF-0600BB589FBB}
	iidBuffer2D = guid(0x7DC9D5F9, 0x9ED9, 0x44EC,
		[8]byte{0x9B, 0xBF, 0x06, 0x00, 0xBB, 0x58, 0x9F, 0xBB})

	// MFVideoFormat_ARGB32 — o número 21 do Direct3D antigo no molde de sempre.
	// É byte a byte o B8G8R8A8 que a Desktop Duplication entrega.
	formatoARGB32 = guid(21, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// MF_TRANSFORM_ASYNC {F81CFDCC-D5C2-4D5F-BAF3-9F9B2E27C0AE}
	chaveAssincrono = guid(0xF81CFDCC, 0xD5C2, 0x4D5F,
		[8]byte{0xBA, 0xF3, 0x9F, 0x9B, 0x2E, 0x27, 0xC0, 0xAE})
)

// Índices de vtable das interfaces que só este arquivo usa.
//
// As de Media Foundation herdam `IMFAttributes` INTEIRA (30 métodos, terminando no
// 32), e por isso os métodos próprios começam no 33. É a mesma contagem que já vale
// para o `IMFActivate` em `compressor.go`, e ela está provada lá: o nome do compressor
// sai legível do índice 13, o que só acontece se a tabela toda estiver certa.
const (
	// IMFDXGIDeviceManager — o IDL declara em ordem alfabética, e `ResetDevice` cai
	// depois de CloseDeviceHandle, GetVideoService, LockDevice e OpenDeviceHandle.
	gerTrocarDispositivo = 7 // ResetDevice

	// IMFSample : IMFAttributes
	amostraDefinirTempo   = 36 // SetSampleTime
	amostraDefinirDuracao = 38 // SetSampleDuration
	amostraJuntarBuffers  = 41 // ConvertToContiguousBuffer
	amostraSomarBuffer    = 42 // AddBuffer

	// IMFMediaBuffer
	bufTrancar        = 3 // Lock
	bufDestrancar     = 4 // Unlock
	bufTamanhoAtual   = 5 // GetCurrentLength
	bufDefinirTamanho = 6 // SetCurrentLength

	// IMF2DBuffer
	buf2DTrancar         = 3 // Lock2D
	buf2DDestrancar      = 4 // Unlock2D
	buf2DTamanhoContiguo = 7 // GetContiguousLength

	// IMFMediaEventGenerator
	geradorPegarEvento = 3 // GetEvent
	// IMFMediaEvent : IMFAttributes
	eventoTipo = 33 // GetType
)

// Índices do Direct3D 11. O `ID3D11DeviceContext` é a contagem mais funda de todo o
// projeto — `CopyResource` é o 47º da tabela —, e por isso ela não entrou aqui de
// cabeça: `transmissao_test.go` copia um quadro para uma textura de leitura e confere
// que os pixels chegaram. Índice errado em COM não dá erro, chama outra função.
const (
	// ID3D11Device — CreateBuffer é o 3, e as três texturas vêm logo atrás.
	d3dCriarTextura2D = 5 // CreateTexture2D

	// ID3D11DeviceContext — ID3D11DeviceChild traz quatro (3..6) e os próprios
	// começam no 7. Do 7 ao 46 são estados de pipeline e desenho; a cópia vem
	// depois deles.
	d3dMapear     = 14 // Map
	d3dDesmapear  = 15 // Unmap
	d3dCopiarTudo = 47 // CopyResource
)

const (
	// Recados do MFT_MESSAGE_TYPE.
	recadoDefinirD3D    = 2
	recadoComecarFluxo  = 0x10000000 // NOTIFY_BEGIN_STREAMING
	recadoEncerrarFluxo = 0x10000001 // NOTIFY_END_STREAMING
	recadoAbrirFluxo    = 0x10000003 // NOTIFY_START_OF_STREAM

	// MFT_OUTPUT_STREAM_PROVIDES_SAMPLES: o compressor traz a amostra de saída
	// pronta e nós não devemos alocar nada. Todo compressor de hardware faz isso.
	compressorTrazAmostra = 0x100

	// MF_E_TRANSFORM_NEED_MORE_INPUT. NÃO É ERRO: é o compressor dizendo que ainda
	// não juntou quadro suficiente para fechar um pedaço. No começo de uma
	// transmissão isso é o normal por alguns quadros.
	querMaisEntrada = 0xC00D6D72

	// MF_E_TRANSFORM_STREAM_CHANGE: ele decidiu mudar o formato de saída no meio.
	// Acontece de verdade na primeira saída de alguns compressores.
	mudouAFormaDaSaida = 0xC00D6D61

	// MediaEventType. Num compressor assíncrono é ELE quem manda: pede quadro
	// quando tem espaço e avisa quando tem saída pronta. Alimentar sem ter sido
	// pedido devolve erro.
	eventoQuerEntrada = 601 // METransformNeedInput
	eventoTemSaida    = 602 // METransformHaveOutput
)

// D3D11_TEXTURE2D_DESC. Onze inteiros, e a `SampleDesc` do meio são dois deles —
// achatada aqui porque struct aninhada de dois `UINT` não muda um byte do arranjo e
// só acrescentaria um nome.
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
	formatoBGRA        = 87      // DXGI_FORMAT_B8G8R8A8_UNORM
	usoPadrao          = 0       // D3D11_USAGE_DEFAULT
	amarrarComoAlvo    = 0x20    // D3D11_BIND_RENDER_TARGET
	amarrarComoTextura = 0x8     // D3D11_BIND_SHADER_RESOURCE
	usoDeLeitura       = 3       // D3D11_USAGE_STAGING
	cpuPodeLer         = 0x20000 // D3D11_CPU_ACCESS_READ (o WRITE é 0x10000)
	mapaDeLeitura      = 1       // D3D11_MAP_READ
)

// MFT_OUTPUT_DATA_BUFFER. Os dois campos em branco são o enchimento que o C põe
// sozinho para alinhar os ponteiros em oito bytes; escritos à mão porque um erro aqui
// desloca `Amostra` e faz o compressor escrever um ponteiro em cima de outra coisa.
type saidaDoCompressor struct {
	Fluxo   uint32
	_       uint32
	Amostra objeto
	Estado  uint32
	_       uint32
	Eventos objeto
}

// MFT_OUTPUT_STREAM_INFO.
type infoDaSaida struct {
	Bandeiras   uint32
	Tamanho     uint32
	Alinhamento uint32
}

// D3D11_MAPPED_SUBRESOURCE.
type mapaDaTextura struct {
	Dados      uintptr
	PassoLinha uint32
	PassoFundo uint32
}

// quadroDeEntrada é uma das texturas do rodízio, já embrulhada para o compressor.
//
// O embrulho é feito UMA vez, na abertura: a textura não muda de endereço, então a
// amostra e o buffer que apontam para ela servem para sempre. Por quadro, o único
// trabalho é a cópia e a marcação de tempo — nada é alocado.
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

// Compressor é o compressor ligado e amarrado ao dispositivo de vídeo DA CAPTURA.
//
// O mesmo dispositivo, e não um novo: textura de um dispositivo não serve em outro, e
// criar o segundo obrigaria a copiar o quadro entre os dois — o vaivém que esta
// migração inteira existe para evitar.
type Compressor struct {
	Nome    string // o compressor escolhido, para o relatório
	Formato string // o formato de entrada que pegou

	// Se ele é comandado por recados em vez de chamadas diretas. Fica exposto
	// porque os dois caminhos falham por motivos DIFERENTES, e saber qual rodou é a
	// primeira pergunta de qualquer defeito daqui.
	Assincrono bool

	// O CAMINHO DE SOFTWARE. Verdadeiro quando nenhum compressor de placa aceitou a
	// textura e caímos no de software, que vive na memória principal.
	//
	// Fica exposto porque muda o que se pode PROMETER: o de placa custa 0,9ms por
	// quadro, o de software custa 5 a 20 dependendo da máquina. Quem liga a transmissão
	// precisa saber disso para escolher a taxa — ver `TaxaQueCabe`.
	NaMemoria bool

	t        objeto // IMFTransform
	eventos  objeto // IMFMediaEventGenerator, 0 se for síncrono
	gerente  objeto // IMFDXGIDeviceManager
	contexto objeto // ID3D11DeviceContext, emprestado da captura

	// ICodecAPI — a única via para MANDAR um quadro-chave agora. Zero quando o
	// compressor não a expõe, e aí `ForcarQuadroChave` não faz nada em vez de falhar.
	comandos objeto

	// Quantos quadros ele já PEDIU e ainda não recebeu. Ver `pedidoDeEntrada`: contar
	// em vez de presumir é o que impede um impasse com compressor que enfileira.
	pedidos int

	anel    []quadroDeEntrada
	proximo int

	// Nulo quando não há nada a fazer entre a captura e o compressor. No caminho de
	// software ele existe SEMPRE, mesmo sem redução de tamanho: é ele quem converte
	// ARGB32 em NV12, que é a única família que o compressor de software aceita.
	reduzir *Redimensionador

	// O QUADRO CONVERTIDO DA VOLTA ANTERIOR, ainda não entregue ao compressor. Só o
	// caminho de software o usa, e ele é a diferença entre 9,4ms e 5,1ms por quadro.
	//
	// POR QUE ESPERAR UMA VOLTA: o compressor de software precisa dos bytes na memória
	// principal, e trazê-los da placa obriga a CPU a esperar a conversão TERMINAR.
	// Medido, essa espera é 73% do custo do quadro — e não é cópia (1,38 MB copiam em
	// meio milissegundo), é a CPU parada. Entregando o quadro de agora e só depois
	// pedindo o de antes, a placa ganha uma volta inteira para terminar e a espera some.
	//
	// É a MESMA FORMA do teto que segurava o caminho de placa em 45 quadros: uma espera
	// que transforma LATÊNCIA em VAZÃO por se recusar a seguir sem o resultado.
	pendente objeto

	// A saída, alocada por NÓS quando o compressor não traz a dele. Todo compressor de
	// placa traz; nenhum de software traz.
	saidaNossa  objeto
	bufferSaida objeto
	trazAmostra bool
	// Tamanho do que a CAPTURA entrega. É o das texturas do rodízio e o do tipo de
	// entrada — não muda enquanto o monitor for o mesmo.
	largura int
	altura  int
	// Tamanho do que SAI comprimido. Igual ao de cima quando não há redução.
	saidaL int
	saidaA int
	fps    int
	kbps   int

	// Reaproveitado a cada saída. Os pedaços de H.264 variam muito de tamanho (um
	// quadro-chave é dezenas de vezes maior que um quadro comum), então ele cresce
	// até o maior que já apareceu e para de crescer.
	saida []byte

	// Onde o tempo de cada quadro foi gasto. Ver `Custos`.
	Custos Custos
}

// Custos separa o tempo do cano por ETAPA, somado ao longo da transmissão.
//
// Existe porque "7,43ms por quadro" não diz o que otimizar. As etapas têm naturezas
// completamente diferentes e remédios opostos:
//
//	Copia       cópia dentro da placa. Deveria ser microssegundos; se não for, a
//	            placa está saturada por outra coisa.
//	Reducao     o Video Processor. Só existe quando há redução de escala.
//	Compressao  entregar o quadro e ESPERAR o compressor de hardware. É trabalho da
//	            placa, não nosso — não dá para escrever código que o encurte, só
//	            para pedir menos pixel.
//	Leitura     puxar os NALs prontos e copiá-los para a memória do Go. Este é
//	            nosso, e é o único que uma máquina fraca sente na CPU.
//
// Sem essa separação, uma máquina fraca engasgando não diz se o problema é a placa,
// o tamanho do quadro ou uma cópia mal feita nossa. Com ela, o remédio é imediato.
type Custos struct {
	Copia      time.Duration
	Reducao    time.Duration
	Compressao time.Duration
	Leitura    time.Duration
	Quadros    int

	// AS DUAS ESPERAS DO COMPRESSOR ASSÍNCRONO, separadas — e a separação decide uma
	// otimização inteira.
	//
	// Ele é comandado por recados, e há dois: "me dá o próximo quadro" e "tenho saída
	// pronta". Esperar por um ou pelo outro parece a mesma coisa no relógio e não é:
	//
	//	PedidoDeEntrada  — ele está OCUPADO comprimindo. Nada a ganhar do nosso lado;
	//	                   é a placa trabalhando, e o remédio é comprimir menos.
	//	SaidaPronta      — nós é que estamos parados esperando um resultado que poderia
	//	                   ser colhido depois. Aqui um pipeline recupera o tempo inteiro.
	//
	// Sem separar, os dois somam num número só e a conclusão vira chute — e as duas
	// conclusões levam a otimizações OPOSTAS.
	PedidoDeEntrada time.Duration
	SaidaPronta     time.Duration
}

// Media devolve os mesmos custos divididos pelo número de quadros.
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

// Ritmo segura o laço da transmissão no compasso pedido.
//
// POR QUE ELE ESPERA EM VEZ DE DESCARTAR, que foi a primeira tentativa e saiu pior.
// Num monitor de 165 Hz a captura entrega muito mais do que os 60 prometidos, e o
// instinto é pegar tudo e jogar fora o que sobra. Medido, isso deu 44 quadros por
// segundo — PIOR que os 79 de antes de existir ritmo nenhum.
//
// A razão é que o laço é serial: captura, comprime, captura. O quadro descartado já
// custou a ida e volta ao DXGI, e descartar triplicou o número dessas idas sem
// devolver nada. Jogar trabalho fora depois de tê-lo feito não é economia.
//
// Esperar até a hora resolve os dois lados: a ida ao DXGI só acontece quando vai
// render quadro, e a Desktop Duplication sempre entrega o MAIS RECENTE — então
// dormir não perde imagem nenhuma, só pula as intermediárias que ninguém veria.
//
// O custo é até um intervalo de atraso (16ms a 60 por segundo) num quadro que acabou
// de mudar. Para tela compartilhada isso não se percebe; para o compressor de uma
// máquina fraca, o terço de trabalho economizado se percebe muito.
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

// Esperar dorme até a próxima casa de tempo.
//
// Quando a volta anterior demorou MAIS que o intervalo — máquina fraca, quadro-chave
// pesado —, a casa é reposta a partir de agora em vez de acumular atraso. Sem isso o
// relógio ficaria devendo casas e o laço passaria a correr sem dormir, tentando
// recuperar um tempo que não volta.
func (r *Ritmo) Esperar() {
	agora := time.Now()
	if espera := r.proximo.Sub(agora); espera > 0 {
		time.Sleep(espera)
		r.proximo = r.proximo.Add(r.intervalo)
		return
	}
	r.proximo = agora.Add(r.intervalo)
}

// AlvoDeSaida diz em quanto a transmissão deve sair, dado o tamanho da tela.
//
// A REGRA VEIO DA CONTA DE BANDA, e não de gosto. Em malha ponto a ponto a subida é
// gasta uma vez POR PESSOA: 1080p custa ~5 Mbps, então numa sala de quatro seriam 15,
// que a maioria das conexões de casa não tem. Com três ou mais, cai para 720p e a
// conta volta a caber.
//
// Sozinho com uma pessoa vale a nitidez: compartilhar tela quase sempre é mostrar
// texto ou código, e em 720p texto pequeno some.
func AlvoDeSaida(largura, altura, pessoasNaSala int) (int, int) {
	teto := 1080
	if pessoasNaSala >= 3 {
		teto = 720
	}
	if altura <= teto {
		return largura, altura
	}
	// Mantém a proporção e ARREDONDA PARA PAR. Dimensão ímpar quebra o H.264, que
	// guarda a cor em blocos de dois por dois pixels — e o erro daí não é recusa, é
	// uma faixa de cor errada na borda.
	l := largura * teto / altura
	return l &^ 1, teto &^ 1
}

// AsTaxasQueOAstraOferece, da melhor para a pior. Três degraus e não uma escala
// contínua: quem assiste percebe a diferença entre 60 e 30, não entre 60 e 54, e cada
// degrau a mais é uma taxa a mais para testar quando algo der errado.
var AsTaxasQueOAstraOferece = []int{60, 30, 15}

// TaxaQueCabe escolhe a maior taxa cujo orçamento comporta o custo medido.
//
// POR QUE ISTO EXISTE. O caminho de placa custa 0,9ms por quadro; o de software custa 4,5
// nesta máquina e 2 a 4 vezes mais numa que não tem placa nenhuma. Pedir 60 quadros por
// segundo a uma máquina que gasta 25ms em cada um não dá 60: dá 40, com o laço rodando
// sem pausa nenhuma e um núcleo inteiro ocupado o tempo todo. É esse núcleo pregado, e
// não a taxa, que a pessoa sente como o aplicativo travando.
//
// A CONTA É METADE DO ORÇAMENTO, e a metade não é folga por medo. O custo medido aqui é
// só o do compressor; fora dele ainda há a captura, o empacotamento em RTP, a rede, e o
// resto do aplicativo desenhando a própria janela na mesma máquina. Deixar metade do
// tempo para tudo isso é o que separa "transmite a 30" de "transmite a 30 e a janela
// responde".
//
//	60/s -> o quadro precisa custar no máximo  8,3ms
//	30/s ->                                   16,7ms
//	15/s ->                                   33,3ms
//
// `teto` é o que a pessoa pediu no preset: esta função só ABAIXA. Uma máquina rápida com
// preset de 30 continua em 30 — o preset é escolha dela, não um alvo a superar.
func TaxaQueCabe(custo time.Duration, teto int) int {
	if teto <= 0 {
		teto = AsTaxasQueOAstraOferece[0]
	}
	// Sem medição não há decisão a tomar. Acontece quando a área de trabalho ficou
	// parada durante o aquecimento — e tela parada não é máquina em apuros.
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
	// Nem a menor taxa cabe. Devolver a menor mesmo assim é o certo: uma imagem lenta
	// ainda é uma imagem, e é o que a pessoa pediu ao apertar o botão.
	return menor
}

// tetoDeSoftware limita a saída do caminho de memória a 720p.
//
// É decisão de orçamento e não de gosto. Neste caminho o quadro atravessa da placa para
// a memória principal, e essa travessia é 73% do custo do quadro e escala direto com o
// número de pixels — medido em `sonda_software_test.go`. 1080p custaria mais que o dobro
// de 720p justamente na máquina que, por definição, é a mais fraca que temos: a que não
// tem compressor de placa nenhum.
//
// 720p e não menos porque compartilhar tela é quase sempre mostrar texto, e abaixo disso
// texto pequeno some — que é exatamente o que se queria mostrar.
func tetoDeSoftware(largura, altura int) (int, int) {
	const teto = 720
	if altura <= teto {
		return largura, altura
	}
	// ARREDONDA PARA PAR, mesma regra de `AlvoDeSaida`: dimensão ímpar quebra o H.264,
	// que guarda a cor em blocos de dois por dois pixels, e o estrago não é recusa — é
	// uma faixa de cor errada na borda.
	return (largura * teto / altura) &^ 1, teto &^ 1
}

// AbrirCompressor escolhe, liga e amarra um compressor ao dispositivo da captura.
//
// `saidaL`/`saidaA` é o tamanho comprimido. Zero em qualquer um deles quer dizer "o
// mesmo da tela", que é o caminho sem redução nenhuma.
//
// `fps` zero quer dizer "o do monitor, capado em 60". Quando a pessoa escolheu um
// preset (720p30, por exemplo), o número dela precisa chegar aqui: o controle de banda
// do compressor DIVIDE a banda pela taxa declarada, então declarar 60 e entregar 30
// faz cada quadro sair com metade dos bits que poderia — imagem pior pela mesma banda,
// sem nada no código dizendo por quê.
//
// PRECISA RODAR NA MESMA THREAD PRESA da captura — vale a regra de COM de sempre.
func AbrirCompressor(tela *Tela, saidaL, saidaA, fps, kbps int) (*Compressor, error) {
	largura, altura := tela.Tamanho()
	if largura <= 0 || altura <= 0 {
		return nil, fmt.Errorf("a captura não sabe o tamanho da tela")
	}
	if saidaL <= 0 || saidaA <= 0 {
		saidaL, saidaA = largura, altura
	}

	// A taxa DECLARADA é o teto do que vamos mandar, não o do monitor. Declarar 165
	// num monitor de 165 Hz faria o controle de banda distribuir a banda por 165
	// quadros e cada um sair mais pobre — e 60 já é mais do que o olho cobra numa
	// tela de conversa.
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

	// PERCORRE OS CANDIDATOS EM VEZ DE CONFIAR NO PRIMEIRO. Numa máquina híbrida a
	// duplicação vem do adaptador que desenha o monitor, quase sempre o integrado, e
	// o compressor da placa dedicada não serve para a textura dele — na máquina onde
	// isto foi escrito, o da NVIDIA nem liga. Tentar até um amarrar é mais curto que
	// descobrir qual placa desenha o monitor.
	// GUARDA A RECUSA DE CADA UM, e não só a do último. São compressores diferentes
	// que falham por motivos diferentes — o de software não fala D3D11, o da placa
	// dedicada nem liga —, e mostrar apenas o último manda quem lê investigar o
	// candidato errado. Já custou uma volta inteira aqui.
	recusas := make([]string, 0, 2*len(lista))
	for _, cand := range lista {
		c, err := amarrar(cand, tela, largura, altura, saidaL, saidaA, fps, kbps, false)
		if err == nil {
			return c, nil
		}
		recusas = append(recusas, fmt.Sprintf("%s (na placa): %v", cand.Nome, err))
	}

	// SEGUNDA PASSADA: O CAMINHO DE SOFTWARE.
	//
	// Chegar aqui é o caso da máquina virtual, do notebook antigo e da área de trabalho
	// remota — e até esta linha existir, era o caso de quem simplesmente NÃO TRANSMITIA.
	// Não "transmitia pior": a função voltava erro e o botão de compartilhar tela não
	// fazia nada.
	//
	// TENTAR EM VEZ DE PERGUNTAR, e isso é deliberado. Dava para ler o
	// `MF_SA_D3D11_AWARE` de cada candidato e mandar os que não falam D3D11 direto para
	// cá — mas esse atributo já foi pego mentindo neste projeto (ver
	// `abrirGeradorSeAssincrono`), e uma passada extra que só roda quando a primeira
	// falhou inteira custa nada e não depende de o atributo estar dizendo a verdade.
	//
	// O TETO DE 720p É AQUI, e é decisão de orçamento, não de gosto: neste caminho o
	// quadro atravessa da placa para a memória principal, e essa travessia é 73% do
	// custo e escala com o número de pixels. 1080p custaria mais que o dobro de 720p
	// numa máquina que, por definição, é a mais fraca que temos.
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
	// Qualquer erro daqui pra frente desmonta o que já subiu. Sem isto, tentar cinco
	// candidatos deixaria cinco compressores ligados segurando a placa.
	pronto := false
	defer func() {
		if !pronto {
			c.Fechar()
		}
	}()

	// A ORDEM DAQUI É OBRIGATÓRIA e cada passo tem seu motivo:
	//
	//  1. destrancar    — compressor de hardware nasce trancado, e trancado recusa
	//                     quase tudo com erro que não explica nada
	//  2. dizer o D3D11 — TEM de vir antes dos tipos: é o que faz ele passar a
	//                     aceitar textura em vez de bytes na memória principal
	//  3. saída         — no H.264 a saída vem antes; enquanto ela não estiver
	//                     definida ele não revela sequer o que aceita na entrada
	//  4. entrada       — só agora a lista existe
	if err := destrancarSeAssincrono(t); err != nil {
		return nil, err
	}
	// A PLACA VAI PARA O GERENCIADOR NOS DOIS CAMINHOS, mas só o de placa a entrega ao
	// COMPRESSOR. No de software o gerenciador ainda é obrigatório — é ele que deixa o
	// Video Processor ler a textura da captura —, e entregá-lo ao compressor de software
	// é justamente a recusa que trouxe a transmissão até aqui.
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
	// A ENTRADA DO COMPRESSOR É O TAMANHO JÁ REDUZIDO, e não o da tela. Quando há
	// redimensionador no meio, é ele quem recebe o quadro grande; o compressor só vê
	// o pequeno. Sem redução os dois são iguais e a linha continua valendo.
	formatoDaEntrada := formatoARGB32
	if naMemoria {
		formatoDaEntrada = formatoNV12
	}
	if err := c.definirEntrada(formatoDaEntrada); err != nil {
		return nil, err
	}
	// NO CAMINHO DE SOFTWARE O REDIMENSIONADOR EXISTE SEMPRE, mesmo quando não há nada
	// a reduzir: é ele quem converte ARGB32 em NV12. Amarrar essa existência só à
	// diferença de tamanho faria a transmissão funcionar em 1080p→720p e falhar em
	// 720p→720p, que é o tipo de defeito que parece aleatório de fora.
	if naMemoria || saidaL != largura || saidaA != altura {
		rd, err := AbrirRedimensionador(c.gerente, largura, altura, saidaL, saidaA, formatoDaEntrada)
		if err != nil {
			return nil, err
		}
		c.reduzir = rd
	}
	// A VIA DE COMANDO, se ele tiver. Perguntar aqui e guardar o resultado evita
	// consultar a interface a cada pedido — e o `nil` guardado é a resposta "este
	// compressor não atende pedido de quadro-chave", que o emissor trata sem ramificar.
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

// entregarODispositivo é o passo que mantém o quadro na placa.
//
// O compressor não aceita um `ID3D11Device` direto: ele quer um "gerenciador", que é
// uma casca do Media Foundation em volta do dispositivo, feita para vários
// componentes o dividirem sem brigar pelo acesso. A ficha que o `MFCreateDXGIDeviceManager`
// devolve não é enfeite — é ela que autoriza o `ResetDevice` a seguir, e trocá-la por
// um zero faz a chamada falhar sem dizer por quê.
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

// entregarODispositivo diz ao COMPRESSOR qual é a placa. Só o caminho de placa faz isto:
// é exatamente a chamada que o compressor de software recusa.
func (c *Compressor) entregarODispositivo() error {
	r := c.t.chamar(transMandarRecado, recadoDefinirD3D, uintptr(c.gerente))
	return hr(r, "dizer ao compressor qual é a placa")
}

// definirEntrada escolhe o formato de pixel e amarra a entrada.
//
// PARTE DO TIPO QUE O PRÓPRIO COMPRESSOR OFERECEU, em vez de montar um do zero. Um
// tipo enumerado já vem com tudo que aquele compressor exige e que a documentação não
// lista — perfil, arranjo de amostras, coisas que variam por driver. Montar na mão
// funciona até o driver que pede um campo a mais, e aí a falha é um erro genérico.
//
// DOIS FORMATOS, UM POR CAMINHO. ARGB32 é o que a captura entrega pronto e o que o
// compressor de placa aceita — o quadro vai da tela ao compressor sem passo nenhum no
// meio. NV12 é o que TODO compressor de software aceita e o único que eles aceitam:
// nenhum deles fala RGB. No caminho de software, quem produz esse NV12 é o Video
// Processor (ver `redimensionador.go`), e ele o faz na placa, de graça.
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

// abrirGeradorSeAssincrono descobre se o compressor é comandado por recados.
//
// Compressor assíncrono não é alimentado quando nós queremos: é ELE quem pede o quadro
// e avisa quando há saída. Entregar um quadro sem ter sido pedido volta como "no
// momento não está aceitando mais entrada" — erro que soa como fila cheia e significa
// "você falou fora da vez".
//
// A DECISÃO SAI DE `QueryInterface`, E NÃO DO `MF_TRANSFORM_ASYNC`. O atributo é o
// caminho documentado e nesta máquina ele MENTE: vale zero no ativador e zero no
// transformador, nos quatro compressores, inclusive nos três de hardware que se
// comportam como assíncronos. Já tínhamos levado esse golpe com o `MF_SA_D3D11_AWARE`,
// e a lição se repete: chave ausente é indistinguível de chave falsa.
//
// Ter a fila de recados, por outro lado, separa com precisão — medido:
//
//	Intel Quick Sync (x2)     tem fila
//	Microsoft AVC DX12        tem fila
//	H264 Encoder MFT (soft)   NÃO tem
//
// Que é exatamente a divisão entre hardware e software, ou seja, entre assíncrono e
// síncrono. Perguntar ao objeto o que ele SABE FAZER vale mais que ler o que ele diz
// ser.
func (c *Compressor) abrirGeradorSeAssincrono() error {
	g, err := c.t.consultar(&iidGeradorDeEventos)
	if err != nil {
		return nil // sem fila de recados: é dos síncronos
	}
	c.eventos = g
	c.Assincrono = true
	return nil
}

// medirASaida descobre se o compressor traz a própria amostra de saída ou espera a
// nossa. A divisão é exata e não é de gosto: todo compressor de placa traz (a memória é
// dela), nenhum de software traz.
//
// ESTA FUNÇÃO ERA A SEGUNDA RECUSA DO CAMINHO DE SOFTWARE. Ela devolvia erro dizendo que
// só o caminho de placa existia — o que era verdade quando foi escrita, e é o motivo de
// máquina sem placa não transmitir nada. Alocar aqui é o mesmo que o `Descompressor` já
// fazia do outro lado, e por isso o formato é o mesmo.
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

	// SOLTA O ANTERIOR ANTES DE RESERVAR OUTRO. Esta função roda uma vez na abertura e
	// de novo a cada renegociação de formato; sem esta linha, a segunda vez abandonaria
	// um buffer de saída vivo por transmissão. Vazamento em objeto COM não aparece no
	// perfil do Go — aparece como a memória do processo subindo sem explicação.
	c.soltarSaidaNossa()

	tamanho := int(info.Tamanho)
	if tamanho <= 0 {
		// Um quadro-chave de 720p passa longe de um megabyte; este teto é folga com
		// sobra e só existe porque um compressor pode não dizer de quanto precisa.
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

// montarAnel cria as três texturas do rodízio e embrulha cada uma numa amostra.
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
		// As duas amarrações porque o compressor pode querer ler a textura como
		// imagem ou desenhar sobre ela, e qual das duas depende do driver. Pedir as
		// duas custa nada e evita um "formato não suportado" que não explica nada.
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

	// O último argumento é "de baixo pra cima quando for linear": falso, porque a
	// área de trabalho do Windows já vem na ordem que o compressor espera. Verdadeiro
	// aqui entrega o vídeo de cabeça para baixo, sem nenhum erro no caminho.
	r, _, _ = procMFBufferDeSuperficie.Call(
		uintptr(unsafe.Pointer(&iidTextura2D)),
		uintptr(q.textura),
		0, // subrecurso 0: a textura não tem níveis nem camadas
		0, // de cima pra baixo
		uintptr(unsafe.Pointer(&q.buffer)),
	)
	if err := hr(r, "embrulhar a textura para o compressor"); err != nil {
		q.soltar()
		return q, err
	}

	// O TAMANHO PRECISA SER DITO À MÃO. Um buffer que embrulha textura nasce com
	// comprimento zero, porque o Media Foundation não sabe quanto daquela superfície
	// é conteúdo. Compressor que recebe buffer de comprimento zero não reclama: ele
	// comprime nada, e a transmissão sai preta.
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

// Comprimir entrega um quadro da captura e devolve o H.264 que ficou pronto.
//
// A textura pode ser devolvida à captura ASSIM QUE ESTA FUNÇÃO RETORNA: a primeira
// coisa que ela faz é copiar o quadro para uma textura nossa.
//
// `receber` é chamado uma vez por pedaço pronto, e o fatiamento entregue vale só até a
// chamada seguinte — quem quiser guardar copia. Isso é de propósito: a transmissão
// entrega ao pion na hora, e alocar por quadro a 60 por segundo é lixo de sobra para o
// coletor ter opinião sobre a hora de rodar.
func (c *Compressor) Comprimir(textura objeto, quando time.Duration, receber func([]byte)) error {
	q := c.anel[c.proximo]
	c.proximo = (c.proximo + 1) % len(c.anel)
	c.Custos.Quadros++

	marco := time.Now()
	c.contexto.chamar(d3dCopiarTudo, uintptr(q.textura), uintptr(textura))
	c.Custos.Copia += time.Since(marco)

	// Tempo em unidades de 100 nanossegundos, que é o relógio do Media Foundation.
	// A duração declarada é a do quadro na taxa que prometemos.
	const porSegundo = 10_000_000
	marcarTempo := func(a objeto) {
		a.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))
		a.chamar(amostraDefinirDuracao, uintptr(porSegundo/int64(c.fps)))
	}
	marcarTempo(q.amostra)

	// O CAMINHO DE SOFTWARE SAI AQUI, e sai porque a ordem dele é outra: entrega o
	// quadro de AGORA ao Video Processor e comprime o de ANTES. Ver `pendente`.
	if c.NaMemoria {
		return c.comprimirNaMemoria(q.amostra, quando, marcarTempo, receber)
	}

	// A REDUÇÃO ENTRA AQUI, entre a cópia e o compressor. A amostra que sai dela é
	// alocada por ela e é nossa para soltar — soltar depois de o compressor receber,
	// porque ele fica com a própria referência.
	entrada := q.amostra
	if c.reduzir != nil {
		marco = time.Now()
		menor, err := c.reduzir.Reduzir(q.amostra)
		c.Custos.Reducao += time.Since(marco)
		if err != nil {
			return err
		}
		if menor == 0 {
			return nil // quadro perdido na redução; o próximo vem
		}
		defer menor.soltar()
		// O tempo é remarcado na amostra pequena porque nem todo redimensionador o
		// carrega adiante — e quadro sem tempo faz o compressor inventar o ritmo.
		marcarTempo(menor)
		entrada = menor
	}

	// Daqui até o fim é conta do compressor. A leitura dos NALs, que acontece lá
	// dentro, se desconta sozinha em `sair` — senão o mesmo tempo seria contado duas
	// vezes e o relatório culparia a placa por trabalho nosso.
	marco = time.Now()
	defer func() { c.Custos.Compressao += time.Since(marco) }()

	if c.eventos == 0 {
		if err := c.entrar(entrada); err != nil {
			return err
		}
		return c.esvaziar(receber)
	}

	// ASSÍNCRONO — E AQUI ESTAVA O TETO DA TRANSMISSÃO INTEIRA.
	//
	// O desenho antigo esperava BLOQUEANDO até o compressor pedir o quadro, atendendo
	// as saídas que aparecessem no caminho, e só voltava depois de alimentar. Parecia
	// certo e custava caro: medido, 7,5ms por quadro, dos quais
	//
	//	esperando ele pedir entrada      5us
	//	esperando a saída ficar pronta  5179us
	//
	// A placa NUNCA estava ocupada — ela aceita o próximo quadro em cinco
	// microssegundos. Os cinco milissegundos eram nós parados colhendo o resultado do
	// quadro que acabáramos de entregar. Isso é LATÊNCIA do compressor (quadro entra,
	// quadro sai uns 5ms depois), e o laço a transformava em VAZÃO ao se recusar a
	// seguir em frente sem o resultado.
	//
	// Agora: espera o pedido (barato), alimenta, e colhe SEM ESPERAR o que já estiver
	// pronto. O que não estiver é colhido na volta seguinte. A latência vira atraso de
	// um quadro em vez de teto de taxa — e é por isso que ela some do orçamento.
	if err := c.pedidoDeEntrada(receber); err != nil {
		return err
	}
	if err := c.entrar(entrada); err != nil {
		return err
	}
	return c.Drenar(receber)
}

// comprimirNaMemoria é o caminho da máquina sem compressor de placa.
//
// A ORDEM É O CONTRÁRIO DA INTUIÇÃO, e é ela que faz o caminho caber no orçamento:
// entrega o quadro de AGORA ao Video Processor, e comprime o de ANTES.
//
// Por quê: o compressor de software precisa dos bytes na memória principal, e trazê-los
// da placa obriga a CPU a esperar a conversão terminar. Medido, essa espera é 73% do
// custo do quadro — 6,9ms de 9,4. Não é cópia; 1,38 MB copiam em meio milissegundo. É a
// CPU parada. Dando ao quadro uma volta inteira para maturar, a espera cai para quase
// nada e o custo total vai a 5,1ms.
//
// A AMOSTRA VAI DIRETO, sem passar pela memória do Go. O compressor de software aceita o
// `IMFSample` que o Video Processor devolve (medido — `sonda_software_test.go`), então
// não há leitura explícita, não há buffer de entrada nosso, e não há duas cópias de um
// megabyte e meio por quadro. Ele destranca por dentro; quem escolhe a VOLTA em que isso
// acontece continua sendo este laço.
func (c *Compressor) comprimirNaMemoria(quadro objeto, quando time.Duration, marcarTempo func(objeto), receber func([]byte)) error {
	marco := time.Now()
	nova, err := c.reduzir.Reduzir(quadro)
	c.Custos.Reducao += time.Since(marco)
	if err != nil {
		return err
	}
	if nova != 0 {
		// O tempo é marcado AQUI, na amostra convertida, e não na hora de entregá-la ao
		// compressor. Ela vai ser comprimida na volta seguinte, e marcá-la lá carimbaria
		// o quadro com o instante do quadro SEGUINTE — todo o vídeo andaria adiantado
		// um quadro, sem nada no código dizendo por quê.
		marcarTempo(nova)
	}

	// Comprime o de antes ANTES de guardar o de agora: trocar a ordem apagaria a
	// referência do anterior sem tê-lo entregue, e o vídeo sairia pela metade da taxa.
	if err := c.entregarPendente(receber); err != nil {
		return err
	}
	c.pendente = nova
	return nil
}

// entregarPendente comprime o quadro que estava maturando, se houver.
func (c *Compressor) entregarPendente(receber func([]byte)) error {
	p := c.pendente
	if p == 0 {
		return nil
	}
	c.pendente = 0
	defer p.soltar()

	marco := time.Now()
	defer func() { c.Custos.Compressao += time.Since(marco) }()

	// Os dois protocolos, porque o caminho de memória não garante um compressor
	// síncrono: nesta máquina o de software não tem fila de recados, mas nada impede um
	// de placa de cair aqui por recusar a textura e aceitar NV12 na memória. Alimentar
	// um assíncrono sem crédito volta como "não está aceitando entrada agora".
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

// pedidoDeEntrada garante que há UM crédito de entrada, esperando por ele se preciso.
//
// O CRÉDITO É CONTADO, e não presumido. Um compressor com fila interna pede mais de um
// quadro antes de receber qualquer um, e jogar fora o pedido excedente faria a volta
// seguinte esperar por um recado que JÁ TINHA CHEGADO — travando a transmissão num
// impasse em que os dois lados esperam o outro. É o tipo de defeito que só aparece sob
// carga, que é o pior lugar para descobri-lo.
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

// Drenar colhe o que já estiver pronto SEM ESPERAR por nada.
//
// PRECISA SER CHAMADA TAMBÉM QUANDO NÃO HÁ QUADRO A ENVIAR, e essa é a contrapartida de
// não bloquear mais. Com a tela parada a captura não devolve nada, `Comprimir` não é
// chamada, e os últimos quadros ficariam presos dentro do compressor — a imagem de quem
// assiste congelaria um quadro antes do que deveria, justamente no instante em que a
// pessoa parou de mexer para alguém ler o que está na tela.
func (c *Compressor) Drenar(receber func([]byte)) error {
	// NO CAMINHO DE SOFTWARE HÁ UM QUADRO A MAIS PRESO, o que o pipeline deixou
	// maturando. Sem esta linha, parar de mexer na tela congelaria a imagem de quem
	// assiste DOIS quadros antes do que deveria em vez de um — e o segundo é o que o
	// pipeline acrescentou, ou seja, um defeito que a otimização criaria sozinha.
	if c.NaMemoria {
		if err := c.entregarPendente(receber); err != nil {
			return err
		}
	}
	return c.drenarFila(receber)
}

// drenarFila colhe o que já estiver pronto DENTRO do compressor, sem mexer no quadro
// que está maturando no Video Processor.
func (c *Compressor) drenarFila(receber func([]byte)) error {
	if c.eventos == 0 {
		// O síncrono não tem fila de recados: `esvaziar` já pergunta e volta na hora.
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

// esvaziar puxa TUDO que estiver pronto, e o "tudo" é o ponto.
//
// Só serve ao caminho síncrono: no assíncrono quem diz que há saída é o recado, e
// perguntar por conta própria devolve erro.
//
// ESVAZIAR PELA METADE É O QUE TRAVA A TRANSMISSÃO. Um compressor síncrono recusa o
// próximo quadro enquanto tiver saída pendente, e a recusa vem como "no momento não
// está aceitando mais entrada" — que soa como problema de ritmo e é, na verdade, fila
// não esvaziada. A saída de um quadro pode render mais de um pedaço, então parar no
// primeiro deixa resto para sempre.
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

// sair puxa UM pedaço. O booleano diz se veio alguma coisa — falso significa que o
// compressor ainda está juntando quadro, que é o caso normal no começo.
func (c *Compressor) sair(receber func([]byte)) (bool, error) {
	// A leitura é NOSSO trabalho, não da placa, e sai da conta da compressão. É o
	// único custo deste arquivo que uma máquina fraca sente na CPU.
	marco := time.Now()
	defer func() {
		gasto := time.Since(marco)
		c.Custos.Leitura += gasto
		c.Custos.Compressao -= gasto
	}()

	var saida saidaDoCompressor
	if !c.trazAmostra {
		saida.Amostra = c.saidaNossa
		// O TAMANHO USADO VOLTA A ZERO A CADA VOLTA. Sem isto o buffer chega ao
		// compressor já "cheio" da vez anterior e ele recusa por falta de espaço — erro
		// que só aparece no SEGUNDO quadro, que é o pior lugar para procurar. É a mesma
		// pegadinha que o `Descompressor` já documenta do outro lado.
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
		// Ele quer renegociar. Repor o mesmo tipo de saída é o que a documentação
		// manda, e o quadro desta volta se perde — um quadro, no começo da
		// transmissão, não é perda que alguém enxergue.
		if err := configurarSaida(c.t, c.saidaL, c.saidaA, c.fps, c.kbps); err != nil {
			return false, err
		}
		// O TAMANHO DA SAÍDA PODE TER MUDADO JUNTO, e o buffer que reservamos foi
		// dimensionado pelo tipo antigo. `medirASaida` solta o antigo sozinha.
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
	// A amostra só é NOSSA para soltar quando foi ELE quem a trouxe. Soltar a que nós
	// alocamos a destruiria antes da próxima volta.
	if c.trazAmostra {
		defer saida.Amostra.soltar()
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}

	// Os pedaços podem vir em vários buffers; juntar num só é uma chamada, e é o que
	// o pion espera receber.
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

// MF_EVENT_FLAG_NO_WAIT / MF_E_NO_EVENTS_AVAILABLE — a diferença entre perguntar e
// esperar. É essa bandeira que separa "colher o que está pronto" de "parar até ficar".
const (
	recadoSemEsperar = 0x00000001
	semRecadoNaFila  = 0xC00D3E80
)

// recadoSeHouver pega um recado SÓ SE já estiver na fila. O booleano é "veio algum".
//
// É o par da `proximoRecado`, e a existência das duas é a diferença entre esperar a
// placa e conviver com ela. Ver o comentário longo em `Comprimir`.
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

// proximoRecado espera o próximo recado do compressor assíncrono.
//
// Espera BLOQUEANTE de propósito, e não uma consulta em laço. Hoje ela é usada só para
// esperar o PEDIDO DE ENTRADA, que medido custa cinco microssegundos — o compressor
// quase sempre já está pedindo quando chegamos aqui. Quando de fato espera, é porque ele
// está ocupado, e essa é exatamente a hora de não gastar processador perguntando.
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

// Fechar desmonta tudo.
//
// NÃO DRENA os últimos quadros de propósito. Drenar exige mandar o recado de
// esvaziamento e esperar a confirmação, e essa espera pode não voltar se o compressor
// já estiver em mau estado — travar o app ao encerrar uma call, para salvar dois
// quadros que ninguém vai ver, é troca ruim.
func (c *Compressor) Fechar() {
	if c.comandos != 0 {
		c.comandos.soltar()
		c.comandos = 0
	}
	if c.t != 0 {
		c.t.chamar(transMandarRecado, recadoEncerrarFluxo, 0)
	}
	// O QUADRO QUE ESTAVA MATURANDO SE PERDE, e é o certo: ele pertence ao Video
	// Processor, que está prestes a fechar. Um quadro no encerramento não é perda que
	// alguém enxergue — segurar o fechamento para salvá-lo, sim.
	if c.pendente != 0 {
		c.pendente.soltar()
		c.pendente = 0
	}
	if c.reduzir != nil {
		c.reduzir.Fechar()
		c.reduzir = nil
	}
	c.soltarSaidaNossa()
	for _, q := range c.anel {
		q.soltar()
	}
	c.anel = nil
	c.eventos.soltar()
	c.gerente.soltar()
	c.t.soltar()
	c.eventos, c.gerente, c.t = 0, 0, 0
	// O contexto é EMPRESTADO da captura e não se solta aqui — quem o criou é quem o
	// fecha. Soltá-lo duas vezes derruba o processo em algum lugar sem relação.
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

// ---------------------------------------------------------------------------
// Auxiliares de atributo, para o tipo de mídia não virar três linhas por campo.

// IID_ICodecAPI {901DB4C7-31CE-41A2-85DC-8FA0BF41B8DA}
//
// A interface de COMANDO do compressor, separada da de configuração. Ela existe porque
// há coisas que não são "como comprimir" e sim "faça isto agora" — e a única que
// interessa aqui é o quadro-chave sob demanda.
var iidCodecAPI = guid(0x901DB4C7, 0x31CE, 0x41A2,
	[8]byte{0x85, 0xDC, 0x8F, 0xA0, 0xBF, 0x41, 0xB8, 0xDA})

// CODECAPI_AVEncVideoForceKeyFrame {398C1B98-8353-475A-9EF2-8F265D260345}
//
// CONFERIDO PELA SONDA (`TestSondaDoCodecAPI`), e não copiado: o Quick Sync desta
// máquina responde "suportado" e "modificável" a esta chave, e responde "não
// implementado" ao controle de espaçamento (`CODECAPI_AVEncMPVGOPSize`) — o que explica
// por que `MF_MT_MAX_KEYFRAME_SPACING` não muda nada aqui.
//
// A conclusão desenha a arquitetura: NÃO dá para encurtar o intervalo entre quadros-
// chave, mas DÁ para pedir um na hora. É exatamente o mecanismo que o WebRTC usa.
var chaveForcarQuadroChave = guid(0x398C1B98, 0x8353, 0x475A,
	[8]byte{0x9E, 0xF2, 0x8F, 0x26, 0x5D, 0x26, 0x03, 0x45})

// Índices do ICodecAPI, na ordem de declaração do icodecapi.h.
const (
	_codecSuportado   = 3 // IsSupported
	_codecModificavel = 4 // IsModifiable
	codecDefinirValor = 9 // SetValue
)

// VARIANT do Windows. VINTE E QUATRO BYTES em 64 bits, e o tamanho não é escolha: o
// campo de valor é uma união que precisa caber um BRECORD (dois ponteiros). Errar aqui
// não dá erro — faz o compressor ler o tipo de um lugar e o valor de outro.
type variante struct {
	tipo  uint16
	_     [3]uint16
	valor uintptr
	_     uintptr
}

const varInteiroSemSinal = 19 // VT_UI4

// ForcarQuadroChave manda o compressor produzir um quadro-chave no PRÓXIMO quadro.
//
// POR QUE ISTO PRECISA EXISTIR. Um decodificador de H.264 não abre imagem nenhuma antes
// de um quadro-chave — os outros quadros só descrevem a diferença em relação ao
// anterior. Quem entra na sala com a transmissão em curso, e quem perde o quadro-chave
// numa oscilação de rede, fica olhando para o vazio até o próximo. Medido nesta máquina:
// CINCO SEGUNDOS de espera, e o compressor não aceita encurtar esse intervalo.
//
// Devolve falso quando o compressor não expõe a via de comando. Não é erro: é um
// compressor que só sabe seguir o próprio compasso, e aí a espera continua sendo o
// intervalo dele.
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

// Tamanho e taxa são pares empacotados num inteiro de 64 bits: parte alta é largura
// (ou numerador), parte baixa é altura (ou denominador). Trocar as metades dá um vídeo
// de 1080x1920 sem nenhum erro no caminho.
func definirPar(a objeto, chave *windows.GUID, alto, baixo int) {
	a.chamar(atrDefinirUINT64,
		uintptr(unsafe.Pointer(chave)),
		uintptr(uint64(alto)<<32|uint64(uint32(baixo))),
	)
}

// ---------------------------------------------------------------------------

// MedidaDaTransmissao é o que uma volta de medição descobriu.
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

	// O TEMPO GASTO DENTRO DO CANO, somado — copiar na placa, reduzir e comprimir.
	//
	// É ESTE o número que diz se a máquina dá conta, e não a taxa de quadros. A taxa
	// depende do que está acontecendo NA TELA: `ProximoQuadro` espera a duplicação
	// avisar que algo mudou, então área de trabalho parada rende poucos quadros por
	// segundo e um jogo rende muitos. Medir quadros por segundo num desktop quieto
	// mede o Windows, não o Astra — foi o que me fez perseguir um defeito inexistente,
	// vendo 79/s numa hora e 44/s noutra sem ter mudado nada que importasse.
	//
	// O custo POR QUADRO, esse, é da máquina. Se couber no orçamento (16,7ms a 60 por
	// segundo), o cano não é o gargalo. É a medida que serve para decidir se um
	// computador fraco aguenta.
	TempoNoCano time.Duration

	// O mesmo tempo, separado por etapa. É o que diz O QUE otimizar.
	Custos Custos

	// Processador consumido pelo processo durante a medição. É a resposta para a
	// máquina fraca — ver `cpu.go`.
	Processador time.Duration
}

// Nucleos diz quantos núcleos a transmissão ocupou, em média. É o número que se compara
// com os 0,07 do caminho na placa e os 0,84 do caminho antigo pela memória principal.
func (m MedidaDaTransmissao) Nucleos() float64 {
	if m.Duracao <= 0 {
		return 0
	}
	return float64(m.Processador) / float64(m.Duracao)
}

// CustoPorQuadro é quanto o cano gasta em cada quadro. Comparar com 1/fps diz se a
// máquina aguenta o que foi pedido.
func (m MedidaDaTransmissao) CustoPorQuadro() time.Duration {
	if m.Quadros == 0 {
		return 0
	}
	return m.TempoNoCano / time.Duration(m.Quadros)
}

// Folga é quanto do orçamento de tempo sobra, de 0 a 1. Negativo quer dizer que a
// máquina não sustenta a taxa pedida e a transmissão vai engasgar.
func (m MedidaDaTransmissao) Folga() float64 {
	orcamento := time.Second / time.Duration(m.Fps)
	if m.Quadros == 0 || orcamento == 0 {
		return 0
	}
	return 1 - float64(m.CustoPorQuadro())/float64(orcamento)
}

// PorSegundo é a conta que interessa: quadros comprimidos por segundo.
func (m MedidaDaTransmissao) PorSegundo() float64 {
	if m.Duracao <= 0 {
		return 0
	}
	return float64(m.Quadros) / m.Duracao.Seconds()
}

// Kbps é a banda que a transmissão gastaria neste ritmo.
func (m MedidaDaTransmissao) Kbps() float64 {
	if m.Duracao <= 0 {
		return 0
	}
	return float64(m.Bytes) * 8 / 1000 / m.Duracao.Seconds()
}

// MedirTransmissao roda o caminho inteiro e conta.
//
// Existe pelo mesmo motivo do `MedirTela`: a pergunta "dá 60 quadros por segundo?" só
// tem uma resposta honesta na máquina de quem pergunta. Aqui ela sai com o nome do
// compressor que respondeu e a banda que aquilo custaria.
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
		// A ESPERA VEM ANTES DA CAPTURA. Ir ao DXGI mais rápido que o compasso só
		// produz quadro para jogar fora — e a ida em si já custa.
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

		// O CRONÔMETRO PEGA SÓ O CANO, e não a espera pela tela mudar. Misturar as
		// duas coisas foi o erro que tornou a medição inútil.
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
