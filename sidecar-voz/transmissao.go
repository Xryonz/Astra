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
	bufDefinirTamanho = 6 // SetCurrentLength

	// IMF2DBuffer
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

	t        objeto // IMFTransform
	eventos  objeto // IMFMediaEventGenerator, 0 se for síncrono
	gerente  objeto // IMFDXGIDeviceManager
	contexto objeto // ID3D11DeviceContext, emprestado da captura

	// ICodecAPI — a única via para MANDAR um quadro-chave agora. Zero quando o
	// compressor não a expõe, e aí `ForcarQuadroChave` não faz nada em vez de falhar.
	comandos objeto

	anel    []quadroDeEntrada
	proximo int

	// Nulo quando não há redução: a tela já sai no tamanho pedido.
	reduzir *Redimensionador

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
}

// Media devolve os mesmos custos divididos pelo número de quadros.
func (c Custos) Media() Custos {
	if c.Quadros == 0 {
		return Custos{}
	}
	n := time.Duration(c.Quadros)
	return Custos{
		Copia:      c.Copia / n,
		Reducao:    c.Reducao / n,
		Compressao: c.Compressao / n,
		Leitura:    c.Leitura / n,
		Quadros:    c.Quadros,
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
	recusas := make([]string, 0, len(lista))
	for _, cand := range lista {
		c, err := amarrar(cand, tela, largura, altura, saidaL, saidaA, fps, kbps)
		if err == nil {
			return c, nil
		}
		recusas = append(recusas, fmt.Sprintf("%s: %v", cand.Nome, err))
	}
	return nil, fmt.Errorf("nenhum compressor aceitou a textura da captura:\n  %s",
		strings.Join(recusas, "\n  "))
}

func amarrar(cand CompressorDisponivel, tela *Tela, largura, altura, saidaL, saidaA, fps, kbps int) (*Compressor, error) {
	t, err := cand.Montar()
	if err != nil {
		return nil, err
	}
	c := &Compressor{
		Nome:     cand.Nome,
		t:        t,
		contexto: tela.contexto,
		largura:  largura,
		altura:   altura,
		saidaL:   saidaL,
		saidaA:   saidaA,
		fps:      fps,
		kbps:     kbps,
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
	if err := c.entregarODispositivo(tela.dispositivo); err != nil {
		return nil, err
	}
	if err := configurarSaida(t, saidaL, saidaA, fps, kbps); err != nil {
		return nil, err
	}
	// A ENTRADA DO COMPRESSOR É O TAMANHO JÁ REDUZIDO, e não o da tela. Quando há
	// redimensionador no meio, é ele quem recebe o quadro grande; o compressor só vê
	// o pequeno. Sem redução os dois são iguais e a linha continua valendo.
	if err := c.definirEntrada(); err != nil {
		return nil, err
	}
	if saidaL != largura || saidaA != altura {
		rd, err := AbrirRedimensionador(c.gerente, largura, altura, saidaL, saidaA)
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
func (c *Compressor) entregarODispositivo(dispositivo objeto) error {
	var ficha uint32
	r, _, _ := procMFCriarGerenciador.Call(
		uintptr(unsafe.Pointer(&ficha)),
		uintptr(unsafe.Pointer(&c.gerente)),
	)
	if err := hr(r, "criar o gerenciador de vídeo"); err != nil {
		return err
	}
	r = c.gerente.chamar(gerTrocarDispositivo, uintptr(dispositivo), uintptr(ficha))
	if err := hr(r, "entregar a placa ao gerenciador"); err != nil {
		return err
	}
	r = c.t.chamar(transMandarRecado, recadoDefinirD3D, uintptr(c.gerente))
	return hr(r, "dizer ao compressor qual é a placa")
}

// definirEntrada escolhe o formato de pixel e amarra a entrada.
//
// PARTE DO TIPO QUE O PRÓPRIO COMPRESSOR OFERECEU, em vez de montar um do zero. Um
// tipo enumerado já vem com tudo que aquele compressor exige e que a documentação não
// lista — perfil, arranjo de amostras, coisas que variam por driver. Montar na mão
// funciona até o driver que pede um campo a mais, e aí a falha é um erro genérico.
//
// Só ARGB32, e isso é uma decisão consciente de escopo: é o que a captura entrega
// pronto. Compressor que só aceita YUV (todos os de software) exige um passo de
// conversão que ainda não existe — e recusar aqui, com o nome do compressor no erro,
// é melhor que aceitar e transmitir imagem verde.
func (c *Compressor) definirEntrada() error {
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
		if uint32(lido)&0x80000000 != 0 || sub != formatoARGB32 {
			tipo.soltar()
			continue
		}

		definirNumero(tipo, &chaveEntrelacamento, progressivo)
		definirPar(tipo, &chaveTamanhoDoQuadro, c.saidaL, c.saidaA)
		definirPar(tipo, &chaveTaxaDeQuadros, c.fps, 1)

		r = c.t.chamar(transDefinirEntrada, 0, uintptr(tipo), 0)
		tipo.soltar()
		if err := hr(r, "amarrar a entrada em ARGB32"); err != nil {
			return err
		}
		c.Formato = "ARGB32"
		return nil
	}
	return fmt.Errorf("não aceita ARGB32 na entrada (a captura só entrega isso por ora)")
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

func (c *Compressor) medirASaida() error {
	var info infoDaSaida
	r := c.t.chamar(transInfoDaSaida, 0, uintptr(unsafe.Pointer(&info)))
	if err := hr(r, "perguntar como sai o H.264"); err != nil {
		return err
	}
	c.trazAmostra = info.Bandeiras&compressorTrazAmostra != 0
	if !c.trazAmostra {
		// Todo compressor de hardware traz a própria amostra. Chegar aqui significa
		// que caímos num de software, e o resto do arquivo não está preparado para
		// alimentá-lo — melhor dizer isso do que alocar um buffer e falhar adiante.
		return fmt.Errorf("este compressor quer que nós aloquemos a saída; só o caminho de hardware existe por ora")
	}
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

	// ASSÍNCRONO: espera ele PEDIR o quadro, atendendo as saídas que aparecerem no
	// caminho. Alimentar sem ter sido pedido devolve erro, e ficar só esperando sem
	// atender as saídas entope a fila dele e trava os dois lados.
	for {
		tipo, err := c.proximoRecado()
		if err != nil {
			return err
		}
		switch tipo {
		case eventoQuerEntrada:
			return c.entrar(entrada)
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
		return false, configurarSaida(c.t, c.saidaL, c.saidaA, c.fps, c.kbps)
	}
	if err := hr(r, "puxar o H.264"); err != nil {
		return false, err
	}
	if saida.Amostra == 0 {
		return false, nil
	}
	defer saida.Amostra.soltar()
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

// proximoRecado espera o próximo recado do compressor assíncrono.
//
// Espera BLOQUEANTE de propósito, e não uma consulta em laço: a 60 quadros por segundo
// o pedido de entrada já está na fila quando chegamos aqui, então na prática ela não
// espera nada — e quando espera, é porque o compressor está ocupado, que é exatamente
// a hora de não gastar processador perguntando.
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
	if c.reduzir != nil {
		c.reduzir.Fechar()
		c.reduzir = nil
	}
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
