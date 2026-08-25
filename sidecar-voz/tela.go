package main

import (
	"fmt"
	"runtime"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	d3d11             = windows.NewLazySystemDLL("d3d11.dll")
	procCriarD3D11    = d3d11.NewProc("D3D11CreateDevice")
	dxgi              = windows.NewLazySystemDLL("dxgi.dll")
	_procCriarFabrica = dxgi.NewProc("CreateDXGIFactory1")
)

var (
	iidDispositivoDXGI = guid(0x54EC77FA, 0x1377, 0x44E6,
		[8]byte{0x8C, 0x32, 0x88, 0xFD, 0x5F, 0x44, 0xC8, 0x4C})

	iidAdaptador = guid(0x2411E7E1, 0x12AC, 0x4CCF,
		[8]byte{0xBD, 0x14, 0x97, 0x98, 0xE8, 0x53, 0x4D, 0xC9})

	iidSaidaDeVideo1 = guid(0x00CDDEA8, 0x939B, 0x4B83,
		[8]byte{0xA3, 0x40, 0xA6, 0x85, 0x22, 0x66, 0x66, 0xCC})

	iidTextura2D = guid(0x6F15AAF2, 0xD208, 0x4E89,
		[8]byte{0x9A, 0xB4, 0x48, 0x95, 0x35, 0xD3, 0x4F, 0x9C})
)

const (
	dxgiPegarAdaptador = 7

	dxgiEnumerarSaidas = 7

	_dxgiListarModos1 = 19
	_dxgiModoProximo1 = 20
	_dxgiSuperficie1  = 21
	dxgiDuplicarSaida = 22

	dupPegarDescricao   = 7
	dupPegarProximo     = 8
	_dupRetangulosSujos = 9
	_dupRetangulosMove  = 10
	_dupFormaDoPonteiro = 11
	_dupMapear          = 12
	_dupDesmapear       = 13
	dupSoltarQuadro     = 14

	texturaDescricao = 10
)

const (
	tipoDeDriverHardware = 1
	versaoDoSDKD3D11     = 7

	esperaEstourou = 0x887A0027

	acessoPerdido = 0x887A0026
)

type descricaoDaDuplicacao struct {
	Largura            uint32
	Altura             uint32
	NumeradorHz        uint32
	DenominadorHz      uint32
	Formato            uint32
	OrdemDeVarredura   uint32
	Escala             uint32
	Rotacao            uint32
	NaMemoriaDoSistema int32
}

type infoDoQuadro struct {
	UltimaApresentacao int64
	UltimoMouse        int64
	QuadrosAcumulados  uint32
	RetangulosJuntados int32
	ConteudoProtegido  int32
	PonteiroX          int32
	PonteiroY          int32
	PonteiroVisivel    int32
	TamanhoMetadados   uint32
	TamanhoDoPonteiro  uint32
}

type Tela struct {
	dispositivo objeto
	contexto    objeto
	duplicacao  objeto
	desc        descricaoDaDuplicacao

	janela *CapturaDeJanela

	quadroRetido bool
}

func AbrirJanela(janela uintptr, largura, altura int) (*Tela, error) {
	t, err := abrirPlacaDeVideo()
	if err != nil {
		return nil, err
	}
	c, err := AbrirCapturaDeJanela(t.dispositivo, janela, largura, altura)
	if err != nil {
		t.Fechar()
		return nil, err
	}
	t.janela = c
	return t, nil
}

func AbrirTela(indiceDoMonitor int) (*Tela, error) {
	t, err := abrirPlacaDeVideo()
	if err != nil {
		return nil, err
	}
	if err := t.montarDuplicacao(indiceDoMonitor); err != nil {
		t.Fechar()
		return nil, err
	}
	return t, nil
}

func abrirPlacaDeVideo() (*Tela, error) {
	t := &Tela{}

	var nivel uint32
	r, _, _ := procCriarD3D11.Call(
		0,
		tipoDeDriverHardware,
		0,
		0,
		0, 0,
		versaoDoSDKD3D11,
		uintptr(unsafe.Pointer(&t.dispositivo)),
		uintptr(unsafe.Pointer(&nivel)),
		uintptr(unsafe.Pointer(&t.contexto)),
	)
	if err := hr(r, "criar dispositivo de vídeo"); err != nil {
		return nil, err
	}

	if err := naoNulo(t.dispositivo, "criar dispositivo de vídeo"); err != nil {
		t.Fechar()
		return nil, err
	}
	return t, nil
}

func (t *Tela) montarDuplicacao(indiceDoMonitor int) error {
	dispDXGI, err := t.dispositivo.consultar(&iidDispositivoDXGI)
	if err != nil {
		return fmt.Errorf("o dispositivo não fala DXGI: %w", err)
	}
	defer dispDXGI.soltar()

	var adaptador objeto
	r := dispDXGI.chamar(dxgiPegarAdaptador, uintptr(unsafe.Pointer(&adaptador)))
	if err := hr(r, "achar a placa de vídeo"); err != nil {
		return err
	}
	if err := naoNulo(adaptador, "achar a placa de vídeo"); err != nil {
		return err
	}
	defer adaptador.soltar()

	var saida objeto
	r = adaptador.chamar(dxgiEnumerarSaidas,
		uintptr(indiceDoMonitor), uintptr(unsafe.Pointer(&saida)))
	if err := hr(r, fmt.Sprintf("achar o monitor %d", indiceDoMonitor)); err != nil {
		return err
	}
	if err := naoNulo(saida, fmt.Sprintf("achar o monitor %d", indiceDoMonitor)); err != nil {
		return err
	}
	defer saida.soltar()

	saida1, err := saida.consultar(&iidSaidaDeVideo1)
	if err != nil {
		return fmt.Errorf("monitor sem suporte a duplicação: %w", err)
	}
	defer saida1.soltar()

	r = saida1.chamar(dxgiDuplicarSaida,
		uintptr(t.dispositivo), uintptr(unsafe.Pointer(&t.duplicacao)))
	if err := hr(r, "duplicar o monitor"); err != nil {
		return err
	}

	if err := naoNulo(t.duplicacao, "duplicar o monitor"); err != nil {
		return err
	}

	t.duplicacao.chamar(dupPegarDescricao, uintptr(unsafe.Pointer(&t.desc)))
	return nil
}

func (t *Tela) Tamanho() (int, int) {
	if t.janela != nil {
		return t.janela.Tamanho()
	}
	return int(t.desc.Largura), int(t.desc.Altura)
}

func (t *Tela) Hz() int {
	if t.janela != nil {
		return 0
	}
	if t.desc.DenominadorHz == 0 {
		return 0
	}
	return int((t.desc.NumeradorHz + t.desc.DenominadorHz/2) / t.desc.DenominadorHz)
}

type ErroDeAcessoPerdido struct{}

func (ErroDeAcessoPerdido) Error() string { return "acesso à tela perdido" }

func (t *Tela) Remontar(indiceDoMonitor int) error {
	if t.janela != nil {
		return fmt.Errorf("a janela deixou de ser capturável")
	}
	if t.quadroRetido {
		t.duplicacao.chamar(dupSoltarQuadro)
		t.quadroRetido = false
	}
	t.duplicacao.soltar()
	t.duplicacao = 0
	return t.montarDuplicacao(indiceDoMonitor)
}

func (t *Tela) ProximoQuadro(limiteMs uint32) (objeto, error) {
	if t.janela != nil {
		return t.janela.ProximoQuadro(limiteMs)
	}
	return t.pegarQuadro(limiteMs, true)
}

func (t *Tela) QuadroAtual(limiteMs uint32) (objeto, error) {
	return t.pegarQuadro(limiteMs, false)
}

func (t *Tela) pegarQuadro(limiteMs uint32, soSeMudou bool) (objeto, error) {
	if t.quadroRetido {
		return 0, fmt.Errorf("quadro anterior não foi devolvido")
	}

	var info infoDoQuadro
	var recurso objeto
	r := t.duplicacao.chamar(dupPegarProximo,
		uintptr(limiteMs),
		uintptr(unsafe.Pointer(&info)),
		uintptr(unsafe.Pointer(&recurso)),
	)
	switch uint32(r) {
	case esperaEstourou:
		return 0, nil
	case acessoPerdido:
		return 0, ErroDeAcessoPerdido{}
	}
	if err := hr(r, "pegar o próximo quadro"); err != nil {
		return 0, err
	}
	t.quadroRetido = true

	if soSeMudou && info.UltimaApresentacao == 0 {
		recurso.soltar()
		t.SoltarQuadro()
		return 0, nil
	}

	textura, err := recurso.consultar(&iidTextura2D)
	recurso.soltar()
	if err != nil {
		t.SoltarQuadro()
		return 0, fmt.Errorf("o quadro não é uma textura: %w", err)
	}
	return textura, nil
}

func (t *Tela) SoltarQuadro() {
	if t.janela != nil {
		t.janela.SoltarQuadro()
		return
	}
	if !t.quadroRetido {
		return
	}
	t.duplicacao.chamar(dupSoltarQuadro)
	t.quadroRetido = false
}

func (t *Tela) Fechar() {
	t.SoltarQuadro()
	t.janela.Fechar()
	t.janela = nil
	t.duplicacao.soltar()
	t.contexto.soltar()
	t.dispositivo.soltar()
	t.duplicacao, t.contexto, t.dispositivo = 0, 0, 0
}

func MedirTela(monitor int, duracao time.Duration) (quadros int, largura, altura, hz int, err error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err = abrirCOM(); err != nil {
		return
	}
	defer fecharCOM()

	t, err := AbrirTela(monitor)
	if err != nil {
		return
	}
	defer t.Fechar()

	largura, altura = t.Tamanho()
	hz = t.Hz()

	fim := time.Now().Add(duracao)
	for time.Now().Before(fim) {
		textura, e := t.ProximoQuadro(100)
		if e != nil {
			if _, perdeu := e.(ErroDeAcessoPerdido); perdeu {
				if err = t.Remontar(monitor); err != nil {
					return
				}
				continue
			}
			err = e
			return
		}
		if textura == 0 {
			continue
		}
		quadros++
		textura.soltar()
		t.SoltarQuadro()
	}
	return
}
