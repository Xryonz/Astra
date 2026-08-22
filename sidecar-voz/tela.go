package main

// CAPTURA DE TELA SEM TIRAR O QUADRO DA PLACA DE VÍDEO.
//
// Este é o ponto da transmissão inteira, e não o compressor. A medição que decidiu
// isto está guardada em `GstScreenEncoder.kt`, do lado Kotlin, e vale repetir aqui
// porque é o que justifica todo o COM abaixo:
//
//	quadro fica na GPU do começo ao fim   0,07 núcleos
//	quadro desce pra CPU + encoder de HW  0,68
//	quadro desce pra CPU + software       0,84
//
// Comprimir 720p60 custa um quarto de núcleo. MOVER o pixel custa dois. O caminho
// antigo (ffmpeg num processo à parte) trazia o quadro para a memória principal
// quatro vezes: baixava da placa, convertia na CPU, empurrava por um cano a ~83 MB/s,
// e o Java copiava de novo antes de comprimir. Trocar só o compressor por um de
// hardware rendia 0,16 núcleo; manter o quadro na placa rende 0,77.
//
// Por isso a Desktop Duplication do DXGI: ela entrega uma `ID3D11Texture2D`, ou seja,
// o quadro já está onde o compressor vai buscá-lo. Nada desce.
//
// O QUE ESTE ARQUIVO NÃO FAZ: comprimir. Ele para na textura. Quem transforma isso em
// H.264 é o Media Foundation, em arquivo separado — e a fronteira entre os dois é uma
// textura, não um vetor de bytes, justamente para não desfazer o que está escrito
// acima.

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
	// IID_IDXGIDevice {54EC77FA-1377-44E6-8C32-88FD5F44C84C}
	iidDispositivoDXGI = guid(0x54EC77FA, 0x1377, 0x44E6,
		[8]byte{0x8C, 0x32, 0x88, 0xFD, 0x5F, 0x44, 0xC8, 0x4C})

	// IID_IDXGIAdapter {2411E7E1-12AC-4CCF-BD14-9798E8534DC9}
	iidAdaptador = guid(0x2411E7E1, 0x12AC, 0x4CCF,
		[8]byte{0xBD, 0x14, 0x97, 0x98, 0xE8, 0x53, 0x4D, 0xC9})

	// IID_IDXGIOutput1 {00CDDEA8-939B-4B83-A340-A685226666CC} — a `1` no fim é o que
	// importa: `DuplicateOutput` só existe nesta versão da interface, e é ela a razão
	// de todo o caminho existir.
	iidSaidaDeVideo1 = guid(0x00CDDEA8, 0x939B, 0x4B83,
		[8]byte{0xA3, 0x40, 0xA6, 0x85, 0x22, 0x66, 0x66, 0xCC})

	// IID_ID3D11Texture2D {6F15AAF2-D208-4E89-9AB4-489535D34F9C}
	iidTextura2D = guid(0x6F15AAF2, 0xD208, 0x4E89,
		[8]byte{0x9A, 0xB4, 0x48, 0x95, 0x35, 0xD3, 0x4F, 0x9C})
)

// Índices de vtable, na ORDEM DE DECLARAÇÃO do cabeçalho — que é o que define a
// tabela. Conferidos contra a declaração da Microsoft antes de entrar aqui; o teste
// `tela_test.go` reconfere na máquina, porque índice errado em COM não dá erro: chama
// outra função, com os argumentos errados, e trava.
//
// Todas estas interfaces herdam de IDXGIObject, que já traz sete: os três de IUnknown
// mais SetPrivateData, SetPrivateDataInterface, GetPrivateData e GetParent. Por isso o
// primeiro método próprio de qualquer uma delas é o 7, e não o 3.
const (
	// IDXGIDevice
	dxgiPegarAdaptador = 7 // GetAdapter

	// IDXGIAdapter
	dxgiEnumerarSaidas = 7 // EnumOutputs

	// IDXGIOutput1 — os doze de IDXGIOutput (7..18) e depois os quatro próprios.
	// DuplicateOutput é o último, no 22. Foi por causa desta contagem que a lista
	// inteira do IDXGIOutput precisou ser conferida: pular WaitForVBlank, ou
	// esquecer os três de gama, erra o alvo por uma casa e trava sem mensagem.
	_dxgiListarModos1 = 19
	_dxgiModoProximo1 = 20
	_dxgiSuperficie1  = 21
	dxgiDuplicarSaida = 22

	// IDXGIOutputDuplication
	dupPegarDescricao   = 7  // GetDesc
	dupPegarProximo     = 8  // AcquireNextFrame
	_dupRetangulosSujos = 9  // GetFrameDirtyRects
	_dupRetangulosMove  = 10 // GetFrameMoveRects
	_dupFormaDoPonteiro = 11 // GetFramePointerShape
	_dupMapear          = 12 // MapDesktopSurface
	_dupDesmapear       = 13 // UnMapDesktopSurface
	dupSoltarQuadro     = 14 // ReleaseFrame

	// ID3D11Texture2D — cadeia diferente: ID3D11DeviceChild traz quatro (3..6),
	// ID3D11Resource mais três (7..9), e o GetDesc da textura fica no 10.
	texturaDescricao = 10
)

const (
	tipoDeDriverHardware = 1 // D3D_DRIVER_TYPE_HARDWARE
	versaoDoSDKD3D11     = 7 // D3D11_SDK_VERSION

	// DXGI_ERROR_WAIT_TIMEOUT. NÃO É ERRO: significa que a tela não mudou dentro do
	// prazo, o que numa tela parada é o caso mais comum que existe. Tratar como falha
	// derrubaria a transmissão de quem está lendo um texto.
	esperaEstourou = 0x887A0027

	// DXGI_ERROR_ACCESS_LOST. A duplicação morre sozinha em troca de resolução, ao
	// entrar num jogo em tela cheia, ao trocar de área de trabalho e na tela de
	// bloqueio. Não é defeito — é o contrato da API, e o conserto é remontar.
	acessoPerdido = 0x887A0026
)

// descricaoDaDuplicacao é o DXGI_OUTDUPL_DESC.
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

// infoDoQuadro é o DXGI_OUTDUPL_FRAME_INFO.
//
// Só dois campos interessam. `UltimaApresentacao` zerado quer dizer que NADA da
// imagem mudou — o que chegou foi só movimento de cursor, e o cursor não é o assunto
// aqui. Devolver esse quadro ao compressor gastaria banda para transmitir a mesma
// imagem de novo.
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

// Tela é a captura viva. Guarda o dispositivo D3D11 porque o compressor vai precisar
// do MESMO — textura de um dispositivo não serve em outro, e criar dois seria voltar
// a copiar pixel entre eles.
type Tela struct {
	dispositivo objeto // ID3D11Device
	contexto    objeto // ID3D11DeviceContext
	duplicacao  objeto // IDXGIOutputDuplication
	desc        descricaoDaDuplicacao

	// Se um quadro está retido. A API só entrega o próximo depois de devolvido o
	// anterior, e esquecer isto trava a captura para sempre — sem erro, só silêncio.
	quadroRetido bool
}

// AbrirTela monta o dispositivo de vídeo e a duplicação do monitor `indiceDoMonitor`.
//
// PRECISA RODAR NUMA THREAD PRESA. Vale a mesma regra do áudio: COM tem afinidade de
// thread, e o Go troca goroutine de thread quando bem entende. Quem chama isto é
// responsável pelo `runtime.LockOSThread`.
func AbrirTela(indiceDoMonitor int) (*Tela, error) {
	t := &Tela{}

	var nivel uint32
	r, _, _ := procCriarD3D11.Call(
		0,                    // adaptador: nulo = o padrão
		tipoDeDriverHardware, // sem software: sem GPU não há caminho barato
		0,                    // sem driver por software
		0,                    // sem bandeiras
		0, 0,                 // sem lista de níveis: aceita o melhor que a placa der
		versaoDoSDKD3D11,
		uintptr(unsafe.Pointer(&t.dispositivo)),
		uintptr(unsafe.Pointer(&nivel)),
		uintptr(unsafe.Pointer(&t.contexto)),
	)
	if err := hr(r, "criar dispositivo de vídeo"); err != nil {
		return nil, err
	}
	// O `D3D11CreateDevice` pode responder S_FALSE — código de sucesso — e não entregar
	// dispositivo. Sem esta linha o primeiro método vira endereço nulo, e endereço nulo
	// em Go não é erro devolvido: é queda do processo de voz inteiro, com a sala junto.
	if err := naoNulo(t.dispositivo, "criar dispositivo de vídeo"); err != nil {
		t.Fechar()
		return nil, err
	}

	if err := t.montarDuplicacao(indiceDoMonitor); err != nil {
		t.Fechar()
		return nil, err
	}
	return t, nil
}

// montarDuplicacao percorre dispositivo → adaptador → monitor → duplicação.
//
// Está separado de `AbrirTela` porque é exatamente isto que precisa ser refeito quando
// o acesso se perde (jogo em tela cheia, troca de resolução, tela de bloqueio). O
// dispositivo D3D11 sobrevive a esses eventos; só a duplicação morre.
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
	// A duplicação é EXCLUSIVA por processo e por monitor: um segundo pedido para o
	// mesmo monitor é o caminho normal para chegar aqui sem objeto.
	if err := naoNulo(t.duplicacao, "duplicar o monitor"); err != nil {
		return err
	}

	t.duplicacao.chamar(dupPegarDescricao, uintptr(unsafe.Pointer(&t.desc)))
	return nil
}

// Tamanho devolve a resolução da área capturada.
func (t *Tela) Tamanho() (int, int) { return int(t.desc.Largura), int(t.desc.Altura) }

// Hz devolve a taxa de atualização do monitor, arredondada.
//
// É o TETO REAL da captura, e não uma curiosidade: a duplicação entrega no ritmo em
// que a tela desenha. Pedir 60 quadros por segundo a um monitor de 60 Hz é pedir tudo
// que existe; num de 144 Hz sobra folga; num de 30 Hz não há 60 para dar, e nenhum
// ajuste de código inventa quadro que a tela não desenhou.
func (t *Tela) Hz() int {
	if t.desc.DenominadorHz == 0 {
		return 0
	}
	return int((t.desc.NumeradorHz + t.desc.DenominadorHz/2) / t.desc.DenominadorHz)
}

// ErroDeAcessoPerdido diz se a duplicação morreu por causa do sistema (e não por
// defeito nosso), caso em que o conserto é remontar.
type ErroDeAcessoPerdido struct{}

func (ErroDeAcessoPerdido) Error() string { return "acesso à tela perdido" }

// Remontar refaz a duplicação depois de um acesso perdido, reaproveitando o
// dispositivo de vídeo.
func (t *Tela) Remontar(indiceDoMonitor int) error {
	if t.quadroRetido {
		t.duplicacao.chamar(dupSoltarQuadro)
		t.quadroRetido = false
	}
	t.duplicacao.soltar()
	t.duplicacao = 0
	return t.montarDuplicacao(indiceDoMonitor)
}

// ProximoQuadro espera o próximo quadro e devolve a textura, que continua na placa.
//
// `nil, nil` significa "nada mudou dentro do prazo" e é o caso normal numa tela
// parada — quem chama repete a espera em vez de tratar como falha.
//
// A textura devolvida é EMPRESTADA: vale até `SoltarQuadro`, e quem a recebe não pode
// guardá-la. É assim que a API funciona, e é o que permite a captura não alocar nada
// por quadro.
func (t *Tela) ProximoQuadro(limiteMs uint32) (objeto, error) {
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

	// SÓ MEXEU O CURSOR. A imagem é a mesma de antes, e comprimi-la de novo gastaria
	// banda para transmitir o que o outro lado já tem. Devolve o quadro na hora e
	// responde "nada novo".
	if info.UltimaApresentacao == 0 {
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

// SoltarQuadro devolve o quadro à API. Sem isto o próximo nunca chega.
func (t *Tela) SoltarQuadro() {
	if !t.quadroRetido {
		return
	}
	t.duplicacao.chamar(dupSoltarQuadro)
	t.quadroRetido = false
}

func (t *Tela) Fechar() {
	t.SoltarQuadro()
	t.duplicacao.soltar()
	t.contexto.soltar()
	t.dispositivo.soltar()
	t.duplicacao, t.contexto, t.dispositivo = 0, 0, 0
}

// MedirTela roda a captura por um tempo e conta quadros. Existe para a pessoa do
// computador lento conseguir provar o número na máquina DELA — que foi a lição da
// migração anterior, em que a medição veio antes da reescrita.
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
