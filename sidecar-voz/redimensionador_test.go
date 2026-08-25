package main

import (
	"runtime"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

var clsidRedimensionador = guid(0x88753B26, 0x5B24, 0x49BD,
	[8]byte{0xB2, 0xE7, 0x0C, 0x44, 0x5C, 0x78, 0xC9, 0x82})

var iidTransformador = guid(0xBF94C121, 0x5B05, 0x4E6F,
	[8]byte{0x80, 0x00, 0xBA, 0x59, 0x89, 0x61, 0x41, 0x4D})

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

	temFila := false
	if g, err := rd.consultar(&iidGeradorDeEventos); err == nil {
		temFila = true
		g.soltar()
	}
	t.Logf("comandado por recados: %v", temFila)

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
