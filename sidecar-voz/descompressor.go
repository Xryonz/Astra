package main

import (
	"fmt"
	"strings"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var procMFCriarBufferDeMemoria = mfplat.NewProc("MFCreateMemoryBuffer")

var catDescompressorDeVideo = guid(0xD6C02D4B, 0x6833, 0x45B4,
	[8]byte{0x97, 0x1A, 0x05, 0xA4, 0xB0, 0x4B, 0xAB, 0x91})

const atrPegarUINT64 = 8

var chavePassoDaLinha = guid(0x644B4E48, 0x1E02, 0x4516,
	[8]byte{0xB0, 0xEB, 0xC0, 0x1C, 0xA9, 0xD4, 0x9A, 0xC6})

const (
	naoTemMaisTipos = 0xC00D36B9

	naoAceitaMaisAgora = 0xC00D36B5
)

type Quadro struct {
	Dados   []byte
	Largura int
	Altura  int
	Passo   int
}

type Descompressor struct {
	Nome string

	t objeto

	largura int
	altura  int
	passo   int

	saidaNossa  objeto
	bufferSaida objeto
	trazAmostra bool

	entrada       objeto
	bufferEntrada objeto
	capEntrada    int

	quadro Quadro
}

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

func (d *Descompressor) lerAForma(tipo objeto) {
	if l, a, ok := parDoAtributo(tipo, &chaveTamanhoDoQuadro); ok && l > 0 && a > 0 {
		d.largura, d.altura = l, a
	}
	d.passo = d.largura
	if p := int(int32(numeroDoAtributo(tipo, &chavePassoDaLinha))); p > 0 {
		d.passo = p
	}
}

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

func (d *Descompressor) Decodificar(h264 []byte, quando time.Duration, receber func(Quadro)) error {
	if len(h264) == 0 {
		return nil
	}
	if err := d.encherEntrada(h264, quando); err != nil {
		return err
	}

	r := d.t.chamar(transEntrarQuadro, 0, uintptr(d.entrada), 0)
	if uint32(r) == naoAceitaMaisAgora {

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

func (d *Descompressor) encherEntrada(h264 []byte, quando time.Duration) error {
	if d.capEntrada < len(h264) {
		d.soltarEntrada()

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
