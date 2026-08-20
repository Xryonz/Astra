package main

// O COMPRESSOR DE VÍDEO — Media Foundation, e o quadro continua na placa.
//
// A captura (`tela.go`) entrega `ID3D11Texture2D`. O Pion não comprime nada: ele
// recebe H.264 pronto e empacota em RTP. Entre os dois falta exatamente uma coisa, e é
// o que este arquivo procura: um compressor que aceite a textura ONDE ELA JÁ ESTÁ.
//
// Media Foundation e não uma biblioteca de fora porque ele já vem no Windows — zero
// byte a mais no pacote, que foi metade do motivo de o ffmpeg (137 MB) ter saído.
//
// POR ORA ISTO SÓ PROCURA E RELATA. É de propósito, e é a lição do cancelador de eco:
// lá, montar tudo antes de provar as peças custou caro, e o que resolveu foi uma sonda
// que perguntava ao objeto em vez de supor. Aqui a pergunta que decide a arquitetura
// inteira é uma só — "existe nesta máquina um compressor que fala D3D11?" — e ela tem
// resposta antes de qualquer cano ser montado.
//
// O QUE A SONDA RESPONDEU NA MÁQUINA DO DONO (placa híbrida Intel + NVIDIA):
//
//	NVIDIA H.264 Encoder MFT                    nem liga: "falha catastrófica"
//	Intel Quick Sync Video H.264 Encoder MFT    fala D3D11
//	Intel Quick Sync Video H.264 Encoder MFT    fala D3D11
//	Microsoft AVC DX12 Encoder                  fala D3D11
//	H264 Encoder MFT (software)                 não fala, como esperado
//
// O caminho barato EXISTE aqui. E o compressor da NVIDIA não ligar não é problema a
// resolver: em máquina híbrida a duplicação de tela vem do adaptador que DESENHA o
// monitor, que costuma ser o integrado, e textura de um adaptador não serve no outro.
// Compressor tem de casar com a placa que produziu o quadro — usar a NVIDIA aqui
// obrigaria a copiar o quadro de uma placa para a outra, que é o vaivém que este
// arquivo inteiro existe para evitar. A escolha certa é o compressor do MESMO
// adaptador da captura, e nesta máquina ele está ali, funcionando.
//
// E A SEGUNDA PERGUNTA APAGOU UMA PILHA INTEIRA DE CÓDIGO. O plano previa converter
// BGRA→NV12 na placa antes de comprimir, porque é o que o livro-texto manda: H.264
// usa NV12 por dentro. Isso custaria o `ID3D11VideoProcessor` e umas cinco interfaces
// COM só para trocar o arranjo dos canais. Perguntado, o compressor da Intel
// respondeu que aceita:
//
//	{3231564E-3961-42AE-BA67-FF47CCC13EED}   NV12 próprio da Intel
//	NV12
//	ARGB32
//
// ARGB32 é, byte a byte, o que a Desktop Duplication entrega (B8G8R8A8). O quadro vai
// da captura ao compressor sem NENHUM passo no meio, e a conversão acontece dentro do
// próprio compressor, na placa, de graça. O de software não aceita RGB (IYUV, YV12,
// NV12, YUY2) — mas ele é o caminho de emergência, e lá um passo a mais não decide nada.

import (
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	mfplat             = windows.NewLazySystemDLL("mfplat.dll")
	procMFStartup      = mfplat.NewProc("MFStartup")
	procMFShutdown     = mfplat.NewProc("MFShutdown")
	procMFTEnumEx      = mfplat.NewProc("MFTEnumEx")
	procMFCriarTipo    = mfplat.NewProc("MFCreateMediaType")
	procMFCriarAmostra = mfplat.NewProc("MFCreateSample")
)

var (
	// MFT_CATEGORY_VIDEO_ENCODER {F79EAC7D-E545-4387-BDEE-D647D7BDE42A}
	catCompressorDeVideo = guid(0xF79EAC7D, 0xE545, 0x4387,
		[8]byte{0xBD, 0xEE, 0xD6, 0x47, 0xD7, 0xBD, 0xE4, 0x2A})

	// MFMediaType_Video {73646976-0000-0010-8000-00AA00389B71} — os quatro
	// primeiros bytes são 'vids' ao contrário. Toda a família de GUIDs de mídia do
	// Windows é assim: um código de quatro letras embrulhado num sufixo fixo.
	tipoMaiorVideo = guid(0x73646976, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// MFVideoFormat_H264 — 'H264' no mesmo embrulho.
	formatoH264 = guid(0x34363248, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// MFVideoFormat_NV12 — 'NV12'. É o formato que o H.264 usa por dentro, e o que
	// TODO compressor aceita. Serve aqui só para a busca: um compressor que aceita
	// NV12 é um compressor de H.264 de verdade.
	//
	// A ENTRADA REAL NÃO É ESTA — ver `FormatosQueAceita`. O de hardware desta
	// máquina aceita ARGB32, que é exatamente o que a captura entrega, e isso apaga um
	// passo inteiro do caminho.
	formatoNV12 = guid(0x3231564E, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// MFT_FRIENDLY_NAME_Attribute {314FFBAE-5B41-4C95-9C19-4E7D586FACE3}
	chaveNomeDoCompressor = guid(0x314FFBAE, 0x5B41, 0x4C95,
		[8]byte{0x9C, 0x19, 0x4E, 0x7D, 0x58, 0x6F, 0xAC, 0xE3})

	// MF_MT_MAJOR_TYPE {48EBA18E-F8C9-4687-BF11-0A74C9F96A8F}
	chaveTipoMaior = guid(0x48EBA18E, 0xF8C9, 0x4687,
		[8]byte{0xBF, 0x11, 0x0A, 0x74, 0xC9, 0xF9, 0x6A, 0x8F})

	// MF_MT_AVG_BITRATE {20332624-FB0D-4D9E-BD0D-CBF6786C102E}
	chaveBandaMedia = guid(0x20332624, 0xFB0D, 0x4D9E,
		[8]byte{0xBD, 0x0D, 0xCB, 0xF6, 0x78, 0x6C, 0x10, 0x2E})

	// MF_MT_FRAME_SIZE {1652C33D-D6B2-4012-B834-72030849A37D}
	chaveTamanhoDoQuadro = guid(0x1652C33D, 0xD6B2, 0x4012,
		[8]byte{0xB8, 0x34, 0x72, 0x03, 0x08, 0x49, 0xA3, 0x7D})

	// MF_MT_FRAME_RATE {C459A2E8-3D2C-4E44-B132-FEE5156C7BB0}
	chaveTaxaDeQuadros = guid(0xC459A2E8, 0x3D2C, 0x4E44,
		[8]byte{0xB1, 0x32, 0xFE, 0xE5, 0x15, 0x6C, 0x7B, 0xB0})

	// MF_MT_INTERLACE_MODE {E2724BB8-E676-4806-B4B2-A8D6EFB44CCD}
	chaveEntrelacamento = guid(0xE2724BB8, 0xE676, 0x4806,
		[8]byte{0xB4, 0xB2, 0xA8, 0xD6, 0xEF, 0xB4, 0x4C, 0xCD})

	// MF_MT_SUBTYPE {F7E34C9A-42E8-4714-B74B-CB29D72C35E5} — o formato exato dentro
	// do tipo maior. É por ele que se pergunta "que pixel você aceita?".
	chaveSubtipo = guid(0xF7E34C9A, 0x42E8, 0x4714,
		[8]byte{0xB7, 0x4B, 0xCB, 0x29, 0xD7, 0x2C, 0x35, 0xE5})

	// MF_TRANSFORM_ASYNC_UNLOCK {E5666D6B-3422-4EB6-A421-DA7DB1F8E207}
	//
	// Compressor de hardware costuma ser assíncrono, e assíncrono nasce TRANCADO: sem
	// destrancar, quase tudo responde erro. Não é proteção contra nós — é para o
	// código antigo, escrito antes de MFTs assíncronos existirem, não tropeçar num.
	chaveDestrancar = guid(0xE5666D6B, 0x3422, 0x4EB6,
		[8]byte{0xA4, 0x21, 0xDA, 0x7D, 0xB1, 0xF8, 0xE2, 0x07})

	// MF_SA_D3D11_AWARE {206B4FC8-FCF9-4C51-AFE3-9764369E33A0}
	//
	// É A PERGUNTA QUE DECIDE TUDO. Verdadeiro = o compressor aceita textura em
	// memória de vídeo e o quadro nunca desce. Falso = ele quer o quadro na memória
	// principal, e aí voltaríamos aos 0,84 núcleo que a migração existe para evitar.
	chaveFalaD3D11 = guid(0x206B4FC8, 0xFCF9, 0x4C51,
		[8]byte{0xAF, 0xE3, 0x97, 0x64, 0x36, 0x9E, 0x33, 0xA0})
)

// Índices de vtable do IMFAttributes, na ordem de declaração do mfobjects.idl. O
// IMFActivate herda dela inteira e acrescenta os três do fim.
//
// Trinta e três métodos herdados antes do primeiro próprio é o tipo de contagem que
// não se confere de olho: por isso a sonda lê o NOME do compressor. Nome legível
// saindo do índice 13 prova a tabela toda de uma vez — lixo sairia de qualquer erro.
const (
	atrPegarUINT32       = 7
	atrPegarGUID         = 10
	atrPegarTextoAlocado = 13
	_atrContar           = 30
	atrDefinirUINT32     = 21
	atrDefinirUINT64     = 22
	atrDefinirGUID       = 24
	ativarObjeto         = 33
	_desligarObjeto      = 34
)

// Índices do IMFTransform. Aqui a herança é só de IUnknown, então o primeiro próprio
// é o 3 — ao contrário das interfaces de DXGI, que carregam sete antes.
const (
	_transLimitesDeFluxo = 3
	_transContarFluxos   = 4
	_transIdsDeFluxo     = 5
	_transInfoDaEntrada  = 6
	transInfoDaSaida     = 7
	transPegarAtributos  = 8
	transTipoDeEntrada   = 13 // GetInputAvailableType
	_transTipoDeSaida    = 14 // GetOutputAvailableType
	_transDefinirEntrada = 15 // SetInputType
	transDefinirSaida    = 16 // SetOutputType
	_transMandarRecado   = 23 // ProcessMessage
	_transEntrarQuadro   = 24 // ProcessInput
	_transSairQuadro     = 25 // ProcessOutput
)

// Bandeiras do MFTEnumEx.
const (
	mftSincrono      = 0x00000001
	mftAssincrono    = 0x00000002
	mftHardware      = 0x00000004
	mftOrdenaEFiltra = 0x00000040
)

// MFT_REGISTER_TYPE_INFO: o par (tipo maior, formato) que descreve uma ponta do
// compressor.
type tipoRegistrado struct {
	Maior   windows.GUID
	Formato windows.GUID
}

// versaoDoMF é o MF_VERSION do Windows 7 pra frente: versão do SDK na parte alta,
// versão da API na baixa.
// MFVideoInterlace_Progressive: a tela nao e entrelacada, e dizer isso explicitamente
// evita o compressor supor campos que nao existem.
const progressivo = 2

// versaoDoMF e o MF_VERSION do Windows 7 pra frente.
const versaoDoMF = 0x00020070

// abrirMF liga o Media Foundation nesta thread.
func abrirMF() error {
	const semSocket = 1 // MFSTARTUP_NOSOCKET: não precisamos da pilha de rede dele
	r, _, _ := procMFStartup.Call(versaoDoMF, semSocket)
	return hr(r, "iniciar o Media Foundation")
}

func fecharMF() { procMFShutdown.Call() }

// CompressorDisponivel é um compressor que o Windows oferece, ainda desligado.
//
// Só o nome, e de propósito: tudo o mais que interessa (se fala D3D11, que tamanhos
// aceita, se comprime rápido) só se sabe DEPOIS de ligar. Guardar aqui um campo que
// parece resposta mas foi lido do lugar errado é exatamente o defeito que esta sonda
// já cometeu uma vez.
type CompressorDisponivel struct {
	Nome     string
	ativador objeto
}

// ProcurarCompressores lista os compressores de H.264 desta máquina.
//
// Pede NV12 na entrada e H.264 na saída, que é exatamente o que a transmissão vai
// usar. Filtrar aqui, e não depois, evita achar um compressor que existe mas não serve.
func ProcurarCompressores() ([]CompressorDisponivel, error) {
	entrada := tipoRegistrado{Maior: tipoMaiorVideo, Formato: formatoNV12}
	saida := tipoRegistrado{Maior: tipoMaiorVideo, Formato: formatoH264}

	// `IMFActivate***` no cabeçalho, e é fácil errar a contagem de estrelas: como
	// `objeto` JÁ é o ponteiro da interface, o vetor é `*objeto` e o que se passa é o
	// endereço dele. Uma estrela a mais aqui compila, roda, e trava na primeira
	// chamada de método — porque o que chega ao COM é o endereço de um ponteiro em
	// vez do objeto.
	var lista *objeto
	var quantos uint32
	r, _, _ := procMFTEnumEx.Call(
		uintptr(unsafe.Pointer(&catCompressorDeVideo)),
		// Hardware primeiro na lista, mas os de software também entram: em máquina
		// sem placa dedicada eles são o único caminho, e é melhor transmitir caro do
		// que não transmitir.
		uintptr(mftHardware|mftSincrono|mftAssincrono|mftOrdenaEFiltra),
		uintptr(unsafe.Pointer(&entrada)),
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&lista)),
		uintptr(unsafe.Pointer(&quantos)),
	)
	if err := hr(r, "procurar compressores"); err != nil {
		return nil, err
	}
	if quantos == 0 {
		return nil, nil
	}
	// O vetor veio do alocador do COM e é nosso para liberar; cada elemento é um
	// objeto com contagem própria.
	defer liberarMemoriaDoCOM(uintptr(unsafe.Pointer(lista)))

	achados := make([]CompressorDisponivel, 0, quantos)
	vetor := unsafe.Slice(lista, quantos)
	for _, a := range vetor {
		achados = append(achados, CompressorDisponivel{
			Nome:     textoDoAtributo(a, &chaveNomeDoCompressor),
			ativador: a,
		})
	}
	return achados, nil
}

// FalaD3D11 responde a pergunta que decide o caminho: este compressor aceita a
// textura onde a captura a deixa?
//
// PRECISA LIGAR O COMPRESSOR PARA PERGUNTAR, e essa foi a primeira coisa que a sonda
// desmentiu. O `MF_SA_D3D11_AWARE` parece um atributo do ativador — o ativador é um
// saco de atributos, tem o nome, tem a categoria — mas não é: ele mora na loja de
// atributos do TRANSFORMADOR, que só existe depois de ligado. Perguntado ao ativador,
// ele responde "não tenho essa chave", e "não tenho" é indistinguível de "falso" se
// quem pergunta não souber a diferença. O relatório dizia que nenhuma placa desta
// máquina fala D3D11, o que era falso e teria condenado a arquitetura inteira por
// engano.
func (c CompressorDisponivel) FalaD3D11() (bool, error) {
	t, err := c.Montar()
	if err != nil {
		return false, err
	}
	defer t.soltar()

	var atributos objeto
	r := t.chamar(transPegarAtributos, uintptr(unsafe.Pointer(&atributos)))
	if err := hr(r, "ler os atributos do compressor"); err != nil {
		return false, err
	}
	defer atributos.soltar()

	return numeroDoAtributo(atributos, &chaveFalaD3D11) != 0, nil
}

// SoltarCompressores devolve os ativadores. Precisa ser chamado com a lista que
// `ProcurarCompressores` devolveu — cada um é um objeto COM vivo.
func SoltarCompressores(lista []CompressorDisponivel) {
	for _, c := range lista {
		c.ativador.soltar()
	}
}

// Montar liga o compressor de verdade e devolve o IMFTransform.
func (c CompressorDisponivel) Montar() (objeto, error) {
	// IID_IMFTransform {BF94C121-5B05-4E6F-8000-BA598961414D}
	iid := guid(0xBF94C121, 0x5B05, 0x4E6F,
		[8]byte{0x80, 0x00, 0xBA, 0x59, 0x89, 0x61, 0x41, 0x4D})
	var t objeto
	r := c.ativador.chamar(ativarObjeto,
		uintptr(unsafe.Pointer(&iid)),
		uintptr(unsafe.Pointer(&t)),
	)
	if err := hr(r, "ligar o compressor "+c.Nome); err != nil {
		return 0, err
	}
	return t, nil
}

func numeroDoAtributo(a objeto, chave *windows.GUID) uint32 {
	var v uint32
	r := a.chamar(atrPegarUINT32,
		uintptr(unsafe.Pointer(chave)),
		uintptr(unsafe.Pointer(&v)),
	)
	if uint32(r)&0x80000000 != 0 {
		return 0
	}
	return v
}

// textoDoAtributo lê uma string alocada pelo Media Foundation.
//
// A memória vem do alocador do COM e é nossa para liberar. Esquecer isso vaza a cada
// enumeração — pouco por vez, e nunca visível num teste curto, que é o pior formato de
// vazamento que existe.
func textoDoAtributo(a objeto, chave *windows.GUID) string {
	var ponteiro *uint16
	var tamanho uint32
	r := a.chamar(atrPegarTextoAlocado,
		uintptr(unsafe.Pointer(chave)),
		uintptr(unsafe.Pointer(&ponteiro)),
		uintptr(unsafe.Pointer(&tamanho)),
	)
	if uint32(r)&0x80000000 != 0 || ponteiro == nil {
		return ""
	}
	defer liberarMemoriaDoCOM(uintptr(unsafe.Pointer(ponteiro)))
	return windows.UTF16PtrToString(ponteiro)
}

// configurarSaida descreve o H.264 que queremos: tamanho, taxa e banda.
//
// A ORDEM IMPORTA E É CONTRA-INTUITIVA: no H.264 a SAÍDA vem primeiro. Enquanto ela
// não estiver definida, o compressor não revela sequer que formatos aceita na entrada
// — a lista volta vazia, sem erro. Faz sentido depois de pensado (o que ele aceita
// depende do perfil que vai produzir), mas custa uma tarde a quem espera a ordem
// natural de "entra, sai".
func configurarSaida(t objeto, largura, altura, fps, kbps int) error {
	var tipo objeto
	r, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "criar o tipo de saída"); err != nil {
		return err
	}
	defer tipo.soltar()

	def := func(chave *windows.GUID, valor windows.GUID) {
		tipo.chamar(atrDefinirGUID, uintptr(unsafe.Pointer(chave)), uintptr(unsafe.Pointer(&valor)))
	}
	def(&chaveTipoMaior, tipoMaiorVideo)
	def(&chaveSubtipo, formatoH264)

	num := func(chave *windows.GUID, valor uint32) {
		tipo.chamar(atrDefinirUINT32, uintptr(unsafe.Pointer(chave)), uintptr(valor))
	}
	num(&chaveBandaMedia, uint32(kbps*1000))
	num(&chaveEntrelacamento, progressivo)

	// Tamanho e taxa são pares empacotados num inteiro de 64 bits: a parte alta é
	// largura (ou numerador) e a baixa é altura (ou denominador). Empacotar assim é
	// escolha antiga do Windows, e trocar as metades dá um vídeo de 1080x1920 sem
	// nenhum erro no caminho.
	par := func(chave *windows.GUID, alto, baixo int) {
		tipo.chamar(atrDefinirUINT64,
			uintptr(unsafe.Pointer(chave)),
			uintptr(uint64(alto)<<32|uint64(uint32(baixo))),
		)
	}
	par(&chaveTamanhoDoQuadro, largura, altura)
	par(&chaveTaxaDeQuadros, fps, 1)

	r = t.chamar(transDefinirSaida, 0, uintptr(tipo), 0)
	return hr(r, fmt.Sprintf("definir a saída em %dx%d @%d", largura, altura, fps))
}

// FormatosQueAceita lista os formatos de pixel que este compressor aceita na entrada.
//
// A PERGUNTA POR TRÁS: a captura entrega BGRA e o livro-texto diz que H.264 quer NV12,
// o que exigiria um passo de conversão na placa — mais uma pilha de COM
// (ID3D11VideoProcessor, umas cinco interfaces) só para trocar o arranjo dos canais.
// Mas "o livro-texto diz" não é resposta: quem responde é o compressor desta máquina.
// Se ele aceitar BGRA, o passo inteiro deixa de existir.
func (c CompressorDisponivel) FormatosQueAceita() ([]string, error) {
	t, err := c.Montar()
	if err != nil {
		return nil, err
	}
	defer t.soltar()
	if err := destrancarSeAssincrono(t); err != nil {
		return nil, err
	}
	// A saída ANTES da entrada — ver o comentário de `configurarSaida`. 720p60 aqui é
	// só um formato plausível para a pergunta ser respondível; a transmissão de
	// verdade escolhe o dela.
	if err := configurarSaida(t, 1280, 720, 60, 4000); err != nil {
		return nil, err
	}

	formatos := []string{}
	for i := uint32(0); i < 64; i++ {
		var tipo objeto
		r := t.chamar(transTipoDeEntrada, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))
		// MF_E_NO_MORE_TYPES encerra a lista; qualquer outro erro também, porque a
		// lista parcial já responde a pergunta.
		if uint32(r)&0x80000000 != 0 {
			break
		}
		var sub windows.GUID
		if tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)),
			uintptr(unsafe.Pointer(&sub)),
		)&0x80000000 == 0 {
			formatos = append(formatos, nomeDoFormato(sub))
		}
		tipo.soltar()
	}
	return formatos, nil
}

// destrancarSeAssincrono libera um compressor de hardware para uso.
//
// Chamado sempre, e sem conferir antes se é assíncrono: em compressor síncrono a
// chave simplesmente não significa nada, e conferir custaria uma leitura para evitar
// uma escrita inofensiva.
func destrancarSeAssincrono(t objeto) error {
	var atributos objeto
	if r := t.chamar(transPegarAtributos, uintptr(unsafe.Pointer(&atributos))); uint32(r)&0x80000000 != 0 {
		return nil // sem loja de atributos não há o que destrancar
	}
	defer atributos.soltar()
	atributos.chamar(atrDefinirUINT32, uintptr(unsafe.Pointer(&chaveDestrancar)), 1)
	return nil
}

// nomeDoFormato traduz um GUID de formato de mídia para algo legível.
//
// A família inteira segue o mesmo molde: {XXXXXXXX-0000-0010-8000-00AA00389B71}, em
// que os quatro primeiros bytes são um código de quatro letras ('NV12', 'H264') ou um
// número pequeno herdado do Direct3D antigo (21 = ARGB32, 22 = RGB32). Decodificar
// assim, em vez de manter uma tabela de nomes, faz aparecer no relatório até o formato
// que eu não previ — que é o ponto de uma sonda.
func nomeDoFormato(g windows.GUID) string {
	molde := [8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71}
	if g.Data2 != 0x0000 || g.Data3 != 0x0010 || g.Data4 != molde {
		return g.String()
	}
	switch g.Data1 {
	case 21:
		return "ARGB32"
	case 22:
		return "RGB32"
	}
	// Código de quatro letras: legível se os quatro bytes forem imprimíveis.
	quatro := [4]byte{byte(g.Data1), byte(g.Data1 >> 8), byte(g.Data1 >> 16), byte(g.Data1 >> 24)}
	for _, b := range quatro {
		if b < 0x20 || b > 0x7E {
			return fmt.Sprintf("formato %d", g.Data1)
		}
	}
	return string(quatro[:])
}

// RelatarCompressores devolve o que esta máquina oferece, em texto. É o que a sonda
// imprime e o que uma pessoa com problema de transmissão pode mandar junto do relato.
func RelatarCompressores() (string, error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err := abrirCOM(); err != nil {
		return "", err
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return "", err
	}
	defer fecharMF()

	lista, err := ProcurarCompressores()
	if err != nil {
		return "", err
	}
	defer SoltarCompressores(lista)

	if len(lista) == 0 {
		return "nenhum compressor de H.264 nesta máquina", nil
	}
	texto := ""
	for _, c := range lista {
		fala, err := c.FalaD3D11()
		switch {
		// NÃO LIGOU é uma resposta, e diferente de "não fala". Placa ocupada por
		// outro programa e driver a meio caminho de uma atualização dão isto, e o
		// remédio é outro — dizer "não fala D3D11" mandaria a pessoa trocar de
		// placa por causa de um jogo aberto.
		case err != nil:
			texto += fmt.Sprintf("%-46s não ligou: %v\n", c.Nome, err)
		case fala:
			texto += fmt.Sprintf("%-46s fala D3D11 (o quadro fica na placa)\n", c.Nome)
		default:
			texto += fmt.Sprintf("%-46s NÃO fala D3D11 (o quadro teria que descer)\n", c.Nome)
		}
	}
	return texto, nil
}
