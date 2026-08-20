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

import (
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	mfplat            = windows.NewLazySystemDLL("mfplat.dll")
	procMFStartup     = mfplat.NewProc("MFStartup")
	procMFShutdown    = mfplat.NewProc("MFShutdown")
	procMFTEnumEx     = mfplat.NewProc("MFTEnumEx")
	_procMFCriarTipo  = mfplat.NewProc("MFCreateMediaType")
	_procMFCriarAmost = mfplat.NewProc("MFCreateSample")
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

	// MFVideoFormat_NV12 — 'NV12'. É o que TODO compressor de H.264 quer na
	// entrada, e a captura entrega BGRA: a conversão é o passo do meio, e ela também
	// tem que acontecer na placa (ID3D11VideoProcessor), senão o quadro desce e o
	// caminho inteiro perde a razão de existir.
	formatoNV12 = guid(0x3231564E, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	// MFT_FRIENDLY_NAME_Attribute {314FFBAE-5B41-4C95-9C19-4E7D586FACE3}
	chaveNomeDoCompressor = guid(0x314FFBAE, 0x5B41, 0x4C95,
		[8]byte{0x9C, 0x19, 0x4E, 0x7D, 0x58, 0x6F, 0xAC, 0xE3})

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
	atrPegarTextoAlocado = 13
	_atrContar           = 30
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
	_transTipoDeEntrada  = 13 // GetInputAvailableType
	_transTipoDeSaida    = 14 // GetOutputAvailableType
	_transDefinirEntrada = 15 // SetInputType
	_transDefinirSaida   = 16 // SetOutputType
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
