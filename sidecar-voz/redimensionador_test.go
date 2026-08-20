package main

import (
	"runtime"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

// SONDA DO REDIMENSIONADOR, antes de escrever o redimensionador.
//
// Três perguntas decidem o formato do código, e nenhuma delas tem resposta confiável na
// documentação — as três já foram respondidas errado por ela neste mesmo projeto:
//
//  1. ele TRAZ a amostra de saída, ou temos de alocar a textura de destino? A
//     diferença é um anel inteiro de texturas a mais.
//  2. é comandado por recados, como o compressor de hardware? Muda o laço.
//  3. a ordem é saída-antes-de-entrada, como no H.264, ou o contrário?
//
// Perguntar custa este arquivo. Supor custa uma tarde, e já custou.

// CLSID_VideoProcessorMFT {88753B26-5B24-49BD-B2E7-0C445C78C982}
var clsidRedimensionador = guid(0x88753B26, 0x5B24, 0x49BD,
	[8]byte{0xB2, 0xE7, 0x0C, 0x44, 0x5C, 0x78, 0xC9, 0x82})

// IID_IMFTransform {BF94C121-5B05-4E6F-8000-BA598961414D}
var iidTransformador = guid(0xBF94C121, 0x5B05, 0x4E6F,
	[8]byte{0x80, 0x00, 0xBA, 0x59, 0x89, 0x61, 0x41, 0x4D})

// tipoDeVideo monta um tipo de mídia de vídeo cru, do jeito que as duas pontas do
// redimensionador querem.
func tipoDeVideo(formato windows.GUID, largura, altura int) (objeto, error) {
	var tipo objeto
	r, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "criar tipo de vídeo"); err != nil {
		return 0, err
	}
	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formato)
	definirNumero(tipo, &chaveEntrelacamento, progressivo)
	definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)
	return tipo, nil
}

func TestComoORedimensionadorSeComporta(t *testing.T) {
	precisaDeVideo(t)
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		t.Fatalf("iniciar Media Foundation: %v", err)
	}
	defer fecharMF()

	// Precisa de uma placa de verdade: sem entregar o dispositivo, ele trabalha na
	// memória principal e as respostas mudam.
	tela, err := AbrirTela(0)
	if err != nil {
		t.Skipf("sem tela: %v", err)
	}
	defer tela.Fechar()
	largura, altura := tela.Tamanho()

	rd, err := criar(&clsidRedimensionador, &iidTransformador)
	if err != nil {
		t.Fatalf("o Video Processor MFT nao existe nesta maquina: %v", err)
	}
	defer rd.soltar()
	t.Log("o Video Processor MFT existe e ligou")

	// PERGUNTA 2, antes de tudo: ele fala por recados?
	temFila := false
	if g, err := rd.consultar(&iidGeradorDeEventos); err == nil {
		temFila = true
		g.soltar()
	}
	t.Logf("comandado por recados: %v", temFila)

	// Entrega a placa, senão ele reduz na CPU — que é exatamente o que não queremos.
	var ficha uint32
	var gerente objeto
	r, _, _ := procMFCriarGerenciador.Call(
		uintptr(unsafe.Pointer(&ficha)), uintptr(unsafe.Pointer(&gerente)))
	if err := hr(r, "criar gerenciador"); err != nil {
		t.Fatalf("%v", err)
	}
	defer gerente.soltar()
	if err := hr(gerente.chamar(gerTrocarDispositivo, uintptr(tela.dispositivo), uintptr(ficha)),
		"entregar a placa"); err != nil {
		t.Fatalf("%v", err)
	}
	if err := hr(rd.chamar(transMandarRecado, recadoDefinirD3D, uintptr(gerente)),
		"dizer qual e a placa"); err != nil {
		t.Fatalf("%v -- sem isto ele reduz na CPU e a migracao inteira perde o sentido", err)
	}
	t.Log("aceitou a placa (vai reduzir dentro da GPU)")

	// PERGUNTA 3: em que ordem ele aceita os tipos? Tenta entrada primeiro, que é a
	// ordem natural; se recusar, tenta o contrário, que é a ordem do H.264.
	entrada, err := tipoDeVideo(formatoARGB32, largura, altura)
	if err != nil {
		t.Fatalf("%v", err)
	}
	defer entrada.soltar()
	saida, err := tipoDeVideo(formatoARGB32, 1280, 720)
	if err != nil {
		t.Fatalf("%v", err)
	}
	defer saida.soltar()

	errEntrada := hr(rd.chamar(transDefinirEntrada, 0, uintptr(entrada), 0), "entrada primeiro")
	errSaida := hr(rd.chamar(transDefinirSaida, 0, uintptr(saida), 0), "saida depois")
	ordem := "entrada -> saida"
	if errEntrada != nil || errSaida != nil {
		t.Logf("entrada-primeiro nao deu (%v / %v); tentando saida-primeiro", errEntrada, errSaida)
		errSaida = hr(rd.chamar(transDefinirSaida, 0, uintptr(saida), 0), "saida primeiro")
		errEntrada = hr(rd.chamar(transDefinirEntrada, 0, uintptr(entrada), 0), "entrada depois")
		ordem = "saida -> entrada"
	}
	if errEntrada != nil || errSaida != nil {
		t.Fatalf("nao aceitou %dx%d ARGB32 -> 1280x720 ARGB32 (%v / %v)",
			largura, altura, errEntrada, errSaida)
	}
	t.Logf("aceitou reduzir %dx%d -> 1280x720 em ARGB32, na ordem: %s", largura, altura, ordem)

	// PERGUNTA 1, a que decide se existe um anel de texturas a mais.
	var info infoDaSaida
	if err := hr(rd.chamar(transInfoDaSaida, 0, uintptr(unsafe.Pointer(&info))),
		"perguntar como sai"); err != nil {
		t.Fatalf("%v", err)
	}
	traz := info.Bandeiras&compressorTrazAmostra != 0
	t.Logf("traz a propria amostra: %v (bandeiras=%#x, tamanho=%d)", traz, info.Bandeiras, info.Tamanho)

	if !traz {
		t.Log("=> vamos precisar alocar as texturas de destino (mais um anel)")
	} else {
		t.Log("=> ele aloca a saida; o anel de destino nao precisa existir")
	}
}
