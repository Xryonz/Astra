package main

// O REDIMENSIONADOR — reduz o quadro DENTRO da placa, antes de comprimir.
//
// POR QUE ELE EXISTE, já que o conversor de cor não precisou existir. A pergunta foi a
// mesma nos dois casos ("o compressor não faz isso sozinho?") e a resposta foi
// diferente: em cor ele fazia, em tamanho ele recusa. Medido, com todas as letras:
//
//	Intel Quick Sync (x2)   entrada 1080p com saída 720p: "tipo de mídia
//	                        inválido, inconsistente ou sem suporte"
//	Microsoft AVC DX12      recusa a própria saída em 1280x720
//
// Entrada e saída do compressor têm de ter o mesmo tamanho. Então alguém precisa
// reduzir antes, e esse alguém é o Video Processor MFT — que vem no Windows, roda na
// placa, e é OUTRO `IMFTransform`, ou seja, reaproveita tudo que já está montado.
//
// A SONDA (`redimensionador_test.go`) respondeu três coisas que mudaram o formato
// deste arquivo, e nenhuma delas era adivinhável:
//
//  1. ELE TRAZ A PRÓPRIA AMOSTRA DE SAÍDA (bandeira 0x100). Isso apaga um anel
//     inteiro de texturas de destino que o plano previa — não alocamos nada.
//  2. NÃO é comandado por recados. Laço simples: entra quadro, sai quadro.
//  3. A ORDEM É ENTRADA E DEPOIS SAÍDA — o CONTRÁRIO do compressor de H.264, onde a
//     saída tem de vir primeiro. Duas peças vizinhas, do mesmo subsistema, com ordens
//     opostas: é exatamente o tipo de assimetria que custa uma tarde a quem supõe que
//     a de um vale para o outro.

// ELE TAMBÉM CONVERTE, e é isso que abre a transmissão na máquina sem compressor de
// placa. Nela o único compressor é o de software, que não fala D3D11 e não aceita RGB —
// só a família YUV. Pedir NV12 na saída daqui resolve as duas recusas de uma vez: a
// conversão acontece na placa (0,7ms, medido) e a amostra que sai já é legível pela CPU,
// que é onde esse compressor vive. Ver `sonda_software_test.go`.
//
// O CAMINHO DE HARDWARE CONTINUA PEDINDO ARGB32, e de propósito: lá o quadro nunca sai
// da placa, e converter seria pagar por um passo que o próprio compressor faz de graça.

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

// CLSID_VideoProcessorMFT {88753B26-5B24-49BD-B2E7-0C445C78C982}
var clsidVideoProcessor = guid(0x88753B26, 0x5B24, 0x49BD,
	[8]byte{0xB2, 0xE7, 0x0C, 0x44, 0x5C, 0x78, 0xC9, 0x82})

// IID_IMFTransform {BF94C121-5B05-4E6F-8000-BA598961414D}
var iidIMFTransform = guid(0xBF94C121, 0x5B05, 0x4E6F,
	[8]byte{0x80, 0x00, 0xBA, 0x59, 0x89, 0x61, 0x41, 0x4D})

// Redimensionador reduz um quadro de `deL x deA` para `paraL x paraA`, sem tirar nada
// da placa. A entrada é sempre ARGB32 (o que a captura entrega); a saída é escolhida
// por quem abre.
type Redimensionador struct {
	t objeto
}

// AbrirRedimensionador liga o Video Processor MFT e o amarra à mesma placa da captura.
//
// `gerente` é o MESMO gerenciador que o compressor recebeu. Compartilhar não é economia
// de linha: as duas peças precisam falar da MESMA placa, senão a textura que uma
// produz não serve para a outra — que é o problema que a máquina híbrida já apresenta
// entre a Intel e a NVIDIA.
//
// `formatoSaida` é o que o compressor do outro lado aceita: `formatoARGB32` no caminho
// de placa, `formatoNV12` no de software.
func AbrirRedimensionador(gerente objeto, deL, deA, paraL, paraA int, formatoSaida windows.GUID) (*Redimensionador, error) {
	t, err := criar(&clsidVideoProcessor, &iidIMFTransform)
	if err != nil {
		return nil, fmt.Errorf("o Video Processor MFT não existe nesta máquina: %w", err)
	}
	r := &Redimensionador{t: t}

	pronto := false
	defer func() {
		if !pronto {
			r.Fechar()
		}
	}()

	// A placa ANTES dos tipos, como no compressor: é o que faz ele trabalhar na
	// memória de vídeo em vez de na principal.
	if err := hr(t.chamar(transMandarRecado, recadoDefinirD3D, uintptr(gerente)),
		"dizer ao redimensionador qual é a placa"); err != nil {
		return nil, err
	}

	// Entrada e DEPOIS saída — ver a nota do cabeçalho. Trocar a ordem aqui faz o
	// segundo `Set` recusar com erro de tipo inválido, que manda quem lê procurar
	// defeito no formato em vez de na ordem.
	if err := r.definirLado(transDefinirEntrada, formatoARGB32, deL, deA, "entrada"); err != nil {
		return nil, err
	}
	if err := r.definirLado(transDefinirSaida, formatoSaida, paraL, paraA, "saída"); err != nil {
		return nil, err
	}

	t.chamar(transMandarRecado, recadoComecarFluxo, 0)
	t.chamar(transMandarRecado, recadoAbrirFluxo, 0)
	pronto = true
	return r, nil
}

func (r *Redimensionador) definirLado(indice int, formato windows.GUID, largura, altura int, qual string) error {
	var tipo objeto
	res, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(res, "criar o tipo da "+qual); err != nil {
		return err
	}
	defer tipo.soltar()

	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formato)
	definirNumero(tipo, &chaveEntrelacamento, progressivo)
	definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)

	return hr(r.t.chamar(indice, 0, uintptr(tipo), 0),
		fmt.Sprintf("amarrar a %s em %dx%d", qual, largura, altura))
}

// Reduzir passa um quadro pelo redimensionador e devolve o quadro menor.
//
// A amostra devolvida É DE QUEM CHAMA: precisa ser solta depois de entregue ao
// compressor. Ela vem do próprio redimensionador (ele aloca), e por isso este arquivo
// não guarda textura nenhuma.
func (r *Redimensionador) Reduzir(entrada objeto) (objeto, error) {
	if err := hr(r.t.chamar(transEntrarQuadro, 0, uintptr(entrada), 0),
		"entregar o quadro ao redimensionador"); err != nil {
		return 0, err
	}

	var saida saidaDoCompressor
	var estado uint32
	res := r.t.chamar(transSairQuadro, 0, 1,
		uintptr(unsafe.Pointer(&saida)),
		uintptr(unsafe.Pointer(&estado)),
	)
	if uint32(res) == querMaisEntrada {
		// Ele é síncrono e um quadro entra, um quadro sai — então isto não deveria
		// acontecer. Se acontecer, não é erro: é quadro perdido, e a transmissão
		// continua no próximo.
		return 0, nil
	}
	if err := hr(res, "pegar o quadro reduzido"); err != nil {
		return 0, err
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}
	return saida.Amostra, nil
}

func (r *Redimensionador) Fechar() {
	if r.t != 0 {
		r.t.chamar(transMandarRecado, recadoEncerrarFluxo, 0)
	}
	r.t.soltar()
	r.t = 0
}
