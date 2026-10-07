package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"runtime"
	"strconv"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	mfreadwrite          = windows.NewLazySystemDLL("mfreadwrite.dll")
	procMFCriarLeitor    = mfreadwrite.NewProc("MFCreateSourceReaderFromURL")
	procMFCriarGravador  = mfreadwrite.NewProc("MFCreateSinkWriterFromURL")
	procMFCriarAtributos = mfplat.NewProc("MFCreateAttributes")
)

var (
	tipoMaiorAudio = guid(0x73647561, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	formatoSomPCM = guid(0x00000001, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	formatoSomAAC = guid(0x00001610, 0x0000, 0x0010,
		[8]byte{0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71})

	chaveProporcaoDoPixel = guid(0xC6376A1E, 0x8D0A, 0x4027,
		[8]byte{0xBE, 0x45, 0x6D, 0x9A, 0x0A, 0xD3, 0x9B, 0xB6})

	chaveCanaisDoSom = guid(0x37E48BF5, 0x645E, 0x4C5B,
		[8]byte{0x89, 0xDE, 0xAD, 0xA9, 0xE2, 0x9B, 0x69, 0x6A})

	chaveAmostrasPorSegundo = guid(0x5FAEEAE7, 0x0290, 0x4C31,
		[8]byte{0x9E, 0x8A, 0xC5, 0x34, 0xF6, 0x8D, 0x9D, 0xBA})

	chaveBitsPorAmostra = guid(0xF2DEB57F, 0x40FA, 0x4764,
		[8]byte{0xAA, 0x33, 0xED, 0x4F, 0x2D, 0x1F, 0xF6, 0x69})

	chaveBytesPorSegundo = guid(0x1AAB75C8, 0xCFEF, 0x451C,
		[8]byte{0xAB, 0x95, 0xAC, 0x03, 0x4B, 0x8E, 0x17, 0x31})

	chaveAlinhamentoDoSom = guid(0x322DE230, 0x9EEB, 0x43BD,
		[8]byte{0xAB, 0x7A, 0xFF, 0x41, 0x22, 0x51, 0x54, 0x1D})

	chaveDuracaoDaFonte = guid(0x6C990D33, 0xBB8E, 0x477A,
		[8]byte{0x85, 0x98, 0x0D, 0x5D, 0x96, 0xFC, 0xD8, 0x8A})

	chaveProcessamentoAvancado = guid(0x0F81DA2C, 0xB537, 0x4672,
		[8]byte{0xA8, 0xB2, 0xA6, 0x81, 0xB1, 0x73, 0x07, 0xA3})

	chaveTransformadoresDaPlaca = guid(0xA634A91C, 0x822B, 0x41B9,
		[8]byte{0xA4, 0x94, 0x4D, 0xE4, 0x64, 0x36, 0x12, 0xB0})

	chaveGravarSemFreio = guid(0x08B845D8, 0x2B74, 0x4AFE,
		[8]byte{0x9D, 0x53, 0xBE, 0x16, 0xD2, 0xD5, 0xAE, 0x4F})

	chaveConteiner = guid(0x150FF23F, 0x4ABC, 0x478B,
		[8]byte{0xAC, 0x4F, 0xE1, 0x91, 0x6F, 0xBA, 0x1C, 0xCA})

	conteinerMP4 = guid(0xDC6CD05D, 0xB9D0, 0x40EF,
		[8]byte{0xBD, 0x35, 0xFA, 0x62, 0x2C, 0x1A, 0xB2, 0x8A})
)

const (
	leitorSelecionarFluxo = 4
	leitorTipoNativo      = 5
	leitorTipoAtual       = 6
	leitorDefinirTipo     = 7
	leitorLerAmostra      = 9
	leitorAtributoDaFonte = 12

	gravadorSomarFluxo     = 3
	gravadorDefinirEntrada = 4
	gravadorComecar        = 5
	gravadorEscrever       = 6
	gravadorTique          = 7
	gravadorFinalizar      = 11

	todosOsFluxos = 0xFFFFFFFE
	aPropriaFonte = 0xFFFFFFFF

	fluxoComErro = 0x1
	fluxoNoFim   = 0x2
	fluxoTique   = 0x100

	propvariantUI8 = 21
)

type FichaDoVideo struct {
	DuracaoMs int64 `json:"duracaoMs"`
	Largura   int   `json:"largura"`
	Altura    int   `json:"altura"`
	TemSom    bool  `json:"temSom"`
}

type AjustesDaCompressao struct {
	LadoMenor int
	Kbps      int
	KbpsDoSom int
}

func tamanhoDeSaida(largura, altura, ladoMenor int) (int, int) {
	if largura <= 0 || altura <= 0 {
		return largura, altura
	}
	menor, maior := min(largura, altura), max(largura, altura)
	ladoMaior := ladoMenor * 16 / 9
	escala := 1.0
	if menor > ladoMenor {
		escala = float64(ladoMenor) / float64(menor)
	}
	if float64(maior)*escala > float64(ladoMaior) {
		escala = float64(ladoMaior) / float64(maior)
	}
	par := func(v int) int {
		r := int(float64(v)*escala + 0.5)
		r -= r % 2
		return max(r, 2)
	}
	return par(largura), par(altura)
}

func numero64DoAtributo(a objeto, chave *windows.GUID) (uint64, bool) {
	var v uint64
	r := a.chamar(atrPegarUINT64, uintptr(unsafe.Pointer(chave)), uintptr(unsafe.Pointer(&v)))
	return v, uint32(r)&0x80000000 == 0
}

func definirNumero64(a objeto, chave *windows.GUID, valor uint64) {
	a.chamar(atrDefinirUINT64, uintptr(unsafe.Pointer(chave)), uintptr(valor))
}

func novoTipo(montar func(objeto)) (objeto, error) {
	var tipo objeto
	r, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(r, "criar um tipo de mídia"); err != nil {
		return 0, err
	}
	montar(tipo)
	return tipo, nil
}

func novosAtributos(montar func(objeto)) (objeto, error) {
	var atributos objeto
	r, _, _ := procMFCriarAtributos.Call(uintptr(unsafe.Pointer(&atributos)), 4)
	if err := hr(r, "criar atributos"); err != nil {
		return 0, err
	}
	montar(atributos)
	return atributos, nil
}

func tipoNV12(largura, altura int, taxa uint64) (objeto, error) {
	return novoTipo(func(t objeto) {
		definirGUID(t, &chaveTipoMaior, tipoMaiorVideo)
		definirGUID(t, &chaveSubtipo, formatoNV12)
		definirPar(t, &chaveTamanhoDoQuadro, largura, altura)
		definirNumero64(t, &chaveTaxaDeQuadros, taxa)
		definirNumero(t, &chaveEntrelacamento, progressivo)
		definirPar(t, &chaveProporcaoDoPixel, 1, 1)
	})
}

func tipoH264(largura, altura int, taxa uint64, kbps int) (objeto, error) {
	return novoTipo(func(t objeto) {
		definirGUID(t, &chaveTipoMaior, tipoMaiorVideo)
		definirGUID(t, &chaveSubtipo, formatoH264)
		definirNumero(t, &chaveBandaMedia, uint32(kbps*1000))
		definirNumero(t, &chaveEntrelacamento, progressivo)
		definirPar(t, &chaveTamanhoDoQuadro, largura, altura)
		definirNumero64(t, &chaveTaxaDeQuadros, taxa)
		definirPar(t, &chaveProporcaoDoPixel, 1, 1)
		definirNumero(t, &chavePerfil, perfilMain)
	})
}

func tipoPCM(amostras, canais int) (objeto, error) {
	return novoTipo(func(t objeto) {
		definirGUID(t, &chaveTipoMaior, tipoMaiorAudio)
		definirGUID(t, &chaveSubtipo, formatoSomPCM)
		definirNumero(t, &chaveBitsPorAmostra, 16)
		definirNumero(t, &chaveAmostrasPorSegundo, uint32(amostras))
		definirNumero(t, &chaveCanaisDoSom, uint32(canais))
		definirNumero(t, &chaveAlinhamentoDoSom, uint32(canais*2))
		definirNumero(t, &chaveBytesPorSegundo, uint32(amostras*canais*2))
	})
}

func tipoAAC(amostras, canais, kbps int) (objeto, error) {
	return novoTipo(func(t objeto) {
		definirGUID(t, &chaveTipoMaior, tipoMaiorAudio)
		definirGUID(t, &chaveSubtipo, formatoSomAAC)
		definirNumero(t, &chaveBitsPorAmostra, 16)
		definirNumero(t, &chaveAmostrasPorSegundo, uint32(amostras))
		definirNumero(t, &chaveCanaisDoSom, uint32(canais))
		definirNumero(t, &chaveBytesPorSegundo, uint32(kbps*1000/8))
	})
}

func abrirLeitor(caminho string, processar bool) (objeto, error) {
	atributos, err := novosAtributos(func(a objeto) {
		if processar {
			definirNumero(a, &chaveProcessamentoAvancado, 1)
		}
	})
	if err != nil {
		return 0, err
	}
	defer atributos.soltar()
	nome, err := windows.UTF16PtrFromString(caminho)
	if err != nil {
		return 0, err
	}
	var leitor objeto
	r, _, _ := procMFCriarLeitor.Call(uintptr(unsafe.Pointer(nome)), uintptr(atributos), uintptr(unsafe.Pointer(&leitor)))
	if err := hr(r, "abrir o vídeo"); err != nil {
		return 0, err
	}
	return leitor, naoNulo(leitor, "abrir o vídeo")
}

func abrirGravador(caminho string) (objeto, error) {
	atributos, err := novosAtributos(func(a objeto) {
		definirNumero(a, &chaveTransformadoresDaPlaca, 1)
		definirNumero(a, &chaveGravarSemFreio, 1)
		definirGUID(a, &chaveConteiner, conteinerMP4)
	})
	if err != nil {
		return 0, err
	}
	defer atributos.soltar()
	nome, err := windows.UTF16PtrFromString(caminho)
	if err != nil {
		return 0, err
	}
	var gravador objeto
	r, _, _ := procMFCriarGravador.Call(uintptr(unsafe.Pointer(nome)), 0, uintptr(atributos), uintptr(unsafe.Pointer(&gravador)))
	if err := hr(r, "criar o arquivo comprimido"); err != nil {
		return 0, err
	}
	return gravador, naoNulo(gravador, "criar o arquivo comprimido")
}

func somarFluxo(gravador, saida, entrada objeto) (uint32, error) {
	var fluxo uint32
	if err := hr(gravador.chamar(gravadorSomarFluxo, uintptr(saida), uintptr(unsafe.Pointer(&fluxo))), "somar um fluxo ao arquivo"); err != nil {
		return 0, err
	}
	return fluxo, hr(gravador.chamar(gravadorDefinirEntrada, uintptr(fluxo), uintptr(entrada), 0), "ligar a entrada do fluxo")
}

func fluxosDoArquivo(leitor objeto) (video, som int) {
	video, som = -1, -1
	for i := 0; i < 32; i++ {
		var tipo objeto
		if uint32(leitor.chamar(leitorTipoNativo, uintptr(i), 0, uintptr(unsafe.Pointer(&tipo))))&0x80000000 != 0 {
			break
		}
		var maior windows.GUID
		tipo.chamar(atrPegarGUID, uintptr(unsafe.Pointer(&chaveTipoMaior)), uintptr(unsafe.Pointer(&maior)))
		tipo.soltar()
		switch {
		case maior == tipoMaiorVideo && video < 0:
			video = i
		case maior == tipoMaiorAudio && som < 0:
			som = i
		}
	}
	return video, som
}

func duracaoEm100ns(leitor objeto) uint64 {
	var valor propvariant
	r := leitor.chamar(leitorAtributoDaFonte, aPropriaFonte,
		uintptr(unsafe.Pointer(&chaveDuracaoDaFonte)), uintptr(unsafe.Pointer(&valor)))
	if uint32(r)&0x80000000 != 0 || valor.tipo != propvariantUI8 {
		return 0
	}
	return uint64(valor.ponteiro)
}

func tipoNativo(leitor objeto, fluxo int) (objeto, error) {
	var tipo objeto
	if err := hr(leitor.chamar(leitorTipoNativo, uintptr(fluxo), 0, uintptr(unsafe.Pointer(&tipo))), "ler o formato original"); err != nil {
		return 0, err
	}
	return tipo, nil
}

func SondarVideo(caminho string) (FichaDoVideo, error) {
	leitor, err := abrirLeitor(caminho, false)
	if err != nil {
		return FichaDoVideo{}, err
	}
	defer leitor.soltar()
	video, som := fluxosDoArquivo(leitor)
	if video < 0 {
		return FichaDoVideo{}, fmt.Errorf("o arquivo não tem imagem de vídeo")
	}
	nativo, err := tipoNativo(leitor, video)
	if err != nil {
		return FichaDoVideo{}, err
	}
	defer nativo.soltar()
	tamanho, _ := numero64DoAtributo(nativo, &chaveTamanhoDoQuadro)
	return FichaDoVideo{
		DuracaoMs: int64(duracaoEm100ns(leitor) / 10_000),
		Largura:   int(tamanho >> 32),
		Altura:    int(uint32(tamanho)),
		TemSom:    som >= 0,
	}, nil
}

func formatoDoSom(leitor objeto, fluxo int) (amostras, canais int, err error) {
	nativo, err := tipoNativo(leitor, fluxo)
	if err != nil {
		return 0, 0, err
	}
	defer nativo.soltar()
	amostras = 48000
	if numeroDoAtributo(nativo, &chaveAmostrasPorSegundo) == 44100 {
		amostras = 44100
	}
	canais = 2
	if numeroDoAtributo(nativo, &chaveCanaisDoSom) == 1 {
		canais = 1
	}
	return amostras, canais, nil
}

func ComprimirVideo(entrada, saida string, aj AjustesDaCompressao, andamento func(float64)) error {
	leitor, err := abrirLeitor(entrada, true)
	if err != nil {
		return err
	}
	defer leitor.soltar()

	video, som := fluxosDoArquivo(leitor)
	if video < 0 {
		return fmt.Errorf("o arquivo não tem imagem de vídeo")
	}
	duracao := duracaoEm100ns(leitor)

	nativo, err := tipoNativo(leitor, video)
	if err != nil {
		return err
	}
	tamanho, _ := numero64DoAtributo(nativo, &chaveTamanhoDoQuadro)
	taxa, temTaxa := numero64DoAtributo(nativo, &chaveTaxaDeQuadros)
	nativo.soltar()
	if !temTaxa || uint32(taxa) == 0 || taxa>>32 == 0 {
		taxa = 30<<32 | 1
	}
	largura, altura := tamanhoDeSaida(int(tamanho>>32), int(uint32(tamanho)), aj.LadoMenor)

	leitor.chamar(leitorSelecionarFluxo, todosOsFluxos, 0)
	leitor.chamar(leitorSelecionarFluxo, uintptr(video), 1)

	lidoDoVideo, err := tipoNV12(largura, altura, taxa)
	if err != nil {
		return err
	}
	defer lidoDoVideo.soltar()
	if err := hr(leitor.chamar(leitorDefinirTipo, uintptr(video), 0, uintptr(lidoDoVideo)), "preparar a leitura em tamanho menor"); err != nil {
		return err
	}

	amostras, canais := 0, 0
	if som >= 0 {
		if amostras, canais, err = formatoDoSom(leitor, som); err != nil {
			return err
		}
		leitor.chamar(leitorSelecionarFluxo, uintptr(som), 1)
		lidoDoSom, err := tipoPCM(amostras, canais)
		if err != nil {
			return err
		}
		defer lidoDoSom.soltar()
		if err := hr(leitor.chamar(leitorDefinirTipo, uintptr(som), 0, uintptr(lidoDoSom)), "preparar a leitura do som"); err != nil {
			return err
		}
	}

	gravador, err := abrirGravador(saida)
	if err != nil {
		return err
	}
	defer gravador.soltar()

	destino := map[uint32]uint32{}

	comprimidoDoVideo, err := tipoH264(largura, altura, taxa, aj.Kbps)
	if err != nil {
		return err
	}
	defer comprimidoDoVideo.soltar()
	var atualDoVideo objeto
	if err := hr(leitor.chamar(leitorTipoAtual, uintptr(video), uintptr(unsafe.Pointer(&atualDoVideo))), "ler o formato de leitura do vídeo"); err != nil {
		return err
	}
	defer atualDoVideo.soltar()
	fluxoDoVideo, err := somarFluxo(gravador, comprimidoDoVideo, atualDoVideo)
	if err != nil {
		return err
	}
	destino[uint32(video)] = fluxoDoVideo

	if som >= 0 {
		comprimidoDoSom, err := tipoAAC(amostras, canais, aj.KbpsDoSom)
		if err != nil {
			return err
		}
		defer comprimidoDoSom.soltar()
		var atualDoSom objeto
		if err := hr(leitor.chamar(leitorTipoAtual, uintptr(som), uintptr(unsafe.Pointer(&atualDoSom))), "ler o formato de leitura do som"); err != nil {
			return err
		}
		defer atualDoSom.soltar()
		fluxoDoSom, err := somarFluxo(gravador, comprimidoDoSom, atualDoSom)
		if err != nil {
			return err
		}
		destino[uint32(som)] = fluxoDoSom
	}

	if err := hr(gravador.chamar(gravadorComecar), "começar a gravar"); err != nil {
		return err
	}

	ultimo := -1.0
	for len(destino) > 0 {
		var real, sinais uint32
		var instante int64
		var amostra objeto
		r := leitor.chamar(leitorLerAmostra, todosOsFluxos, 0,
			uintptr(unsafe.Pointer(&real)), uintptr(unsafe.Pointer(&sinais)),
			uintptr(unsafe.Pointer(&instante)), uintptr(unsafe.Pointer(&amostra)))
		if err := hr(r, "ler o vídeo"); err != nil {
			return err
		}
		fluxo, conhecido := destino[real]
		if amostra != 0 {
			var err error
			if conhecido {
				err = hr(gravador.chamar(gravadorEscrever, uintptr(fluxo), uintptr(amostra)), "gravar o vídeo comprimido")
			}
			amostra.soltar()
			if err != nil {
				return err
			}
		}
		if sinais&fluxoComErro != 0 {
			return fmt.Errorf("o leitor parou com erro no fluxo %d", real)
		}
		if sinais&fluxoTique != 0 && conhecido {
			gravador.chamar(gravadorTique, uintptr(fluxo), uintptr(instante))
		}
		if sinais&fluxoNoFim != 0 {
			delete(destino, real)
		}
		if real == uint32(video) && duracao > 0 && andamento != nil {
			if f := min(1, float64(instante)/float64(duracao)); f-ultimo >= 0.01 {
				ultimo = f
				andamento(f)
			}
		}
	}
	return hr(gravador.chamar(gravadorFinalizar), "fechar o arquivo comprimido")
}

func rodarFerramenta(args []string) int {
	saida := json.NewEncoder(os.Stdout)
	falhar := func(err error) int {
		saida.Encode(map[string]string{"ev": "erro", "msg": err.Error()})
		return 1
	}

	go func() {
		io.Copy(io.Discard, os.Stdin)
		os.Exit(1)
	}()

	runtime.LockOSThread()
	if err := abrirCOM(); err != nil {
		return falhar(err)
	}
	defer fecharCOM()
	if err := abrirMF(); err != nil {
		return falhar(err)
	}
	defer fecharMF()

	switch {
	case args[0] == "sondar-video" && len(args) == 2:
		ficha, err := SondarVideo(args[1])
		if err != nil {
			return falhar(err)
		}
		saida.Encode(struct {
			Ev string `json:"ev"`
			FichaDoVideo
		}{"ficha", ficha})

	case args[0] == "comprimir-video" && len(args) == 6:
		numeros := make([]int, 3)
		for i, texto := range args[3:] {
			n, err := strconv.Atoi(texto)
			if err != nil || n <= 0 {
				return falhar(fmt.Errorf("ajuste inválido: %q", texto))
			}
			numeros[i] = n
		}
		aj := AjustesDaCompressao{LadoMenor: numeros[0], Kbps: numeros[1], KbpsDoSom: numeros[2]}
		err := ComprimirVideo(args[1], args[2], aj, func(f float64) {
			saida.Encode(map[string]any{"ev": "andamento", "fracao": f})
		})
		if err != nil {
			os.Remove(args[2])
			return falhar(err)
		}
		saida.Encode(map[string]string{"ev": "pronto"})

	default:
		return falhar(fmt.Errorf("uso: sondar-video <arquivo> | comprimir-video <entrada> <saída> <lado-menor> <kbps> <kbps-do-som>"))
	}
	return 0
}
