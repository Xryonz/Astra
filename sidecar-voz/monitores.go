package main

// O SELETOR DE TELA — quais monitores existem, e o que está em cada um.
//
// Até aqui a transmissão mandava sempre o monitor 0 e não perguntava. Numa máquina de um
// monitor isso está certo por acaso; em duas telas, é metade de chance de compartilhar a
// errada — e quem erra descobre pelo "não é essa" de outra pessoa na chamada.
//
// A MINIATURA NÃO É ENFEITE, e é o motivo de este arquivo ser maior que uma listagem. O
// Windows chama os monitores de `\\.\DISPLAY1` e `\\.\DISPLAY2`, e esses nomes não dizem
// nada: dois monitores do mesmo modelo têm a mesma resolução e nomes que só diferem no
// dígito. A única informação que separa um do outro é O QUE ESTÁ NELE. Escolher por lista
// de texto é escolher por tentativa e erro, com a tentativa acontecendo ao vivo na frente
// de outras pessoas.
//
// LISTAR E AMOSTRAR SÃO PASSOS SEPARADOS, de propósito. A lista sai de `EnumOutputs` e
// nunca falha; a miniatura precisa DUPLICAR o monitor, e duplicação é exclusiva por
// processo. Se a pessoa já estiver transmitindo o monitor 1 e abrir o seletor para
// trocar, a amostra desse monitor falha — e a resposta certa é a lista completa com uma
// miniatura faltando, não uma lista vazia.

import (
	"bytes"
	"encoding/base64"
	"fmt"
	"image"
	"image/png"
	"sync"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	user32                = windows.NewLazySystemDLL("user32.dll")
	procDefinirCienciaDPI = user32.NewProc("SetProcessDpiAwarenessContext")
	procCienciaDPIAntiga  = user32.NewProc("SetProcessDPIAware")
)

// DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 — vem como ponteiro negativo, que é como o
// Windows codifica esses contextos.
const cienciaPorMonitorV2 = ^uintptr(3) // -4

// avisarQueEntendemosDePixel diz ao Windows que este processo fala em pixels de verdade.
//
// SEM ISTO O SELETOR MENTE, e mentia: numa tela 1920x1080 a 125%, o `DXGI_OUTPUT_DESC`
// respondia 1536x864 — as coordenadas da área de trabalho vêm ESCALADAS para processos
// que não se declaram cientes de DPI. A miniatura mostraria a tela certa com o tamanho
// errado escrito embaixo, e o número errado é justamente o que a pessoa usa para
// distinguir dois monitores.
//
// É seguro num processo sem janela nenhuma: a ciência de DPI só muda como o Windows
// reporta coordenadas e escala janelas, e aqui não há janela para escalar.
//
// Uma vez por processo, e antes de qualquer consulta ao DXGI — depois disso o Windows
// ignora a mudança.
var avisarQueEntendemosDePixel = sync.OnceFunc(func() {
	if r, _, _ := procDefinirCienciaDPI.Call(cienciaPorMonitorV2); r != 0 {
		return
	}
	// Windows anterior ao 10 1703. A versão antiga não sabe de monitor por monitor, mas
	// resolve o caso comum de uma tela só com escala.
	procCienciaDPIAntiga.Call()
})

// IDXGIOutput::GetDesc — o primeiro método próprio, logo depois dos sete que todo
// objeto DXGI herda.
const dxgiDescricaoDaSaida = 7

// DXGI_OUTPUT_DESC. O nome vem como 32 caracteres largos com preenchimento de zeros, e
// não como ponteiro — ler como string exige cortar no primeiro zero.
type descricaoDaSaida struct {
	Nome            [32]uint16
	Esquerda        int32
	Topo            int32
	Direita         int32
	Base            int32
	LigadoNaArea    int32
	Rotacao         uint32
	_               uint32 // alinhamento do ponteiro que vem a seguir
	IdentificadorHM uintptr
}

// LarguraDaMiniatura é o tamanho em que cada tela é amostrada.
//
// 256 e não 320 por causa do transporte: a resposta viaja como UMA LINHA de JSON pela
// saída padrão, com o PNG em base64 dentro. A 256 de largura cada miniatura fica em uns
// 30 KB codificados; a 320, em 50. Com quatro monitores a diferença é entre 120 KB e
// 200 KB numa linha só, e a linha é lida de uma vez do outro lado.
const LarguraDaMiniatura = 256

// MonitorDaTela é uma tela desta máquina, com uma amostra do que está nela.
type MonitorDaTela struct {
	Indice    int    `json:"indice"`
	Nome      string `json:"nome"`
	Largura   int    `json:"largura"`
	Altura    int    `json:"altura"`
	Principal bool   `json:"principal"`

	// PNG em base64, sem cabeçalho de dados. Vazio quando a tela não pôde ser
	// amostrada — quase sempre porque ela já está sendo transmitida.
	Miniatura string `json:"miniatura,omitempty"`
}

// ListarMonitores devolve as telas desta máquina, cada uma com uma amostra do que está
// nela quando dá para tirá-la.
//
// PRECISA RODAR NUMA THREAD PRESA com COM aberto, como todo o resto deste subsistema.
func ListarMonitores() ([]MonitorDaTela, error) {
	achados, err := enumerarSaidas()
	if err != nil {
		return nil, err
	}
	for i := range achados {
		if png, err := amostrarMonitor(achados[i].Indice); err == nil {
			achados[i].Miniatura = base64.StdEncoding.EncodeToString(png)
		}
		// O erro é engolido de propósito: monitor sem miniatura ainda é monitor
		// escolhível, e a causa mais comum é ele já estar sendo transmitido — que é o
		// caso normal de quem abriu o seletor para TROCAR de tela.
	}
	return achados, nil
}

// enumerarSaidas percorre os monitores ligados ao adaptador que desenha a área de
// trabalho. NÃO duplica nada: esta parte nunca falha por monitor ocupado.
func enumerarSaidas() ([]MonitorDaTela, error) {
	avisarQueEntendemosDePixel()

	var dispositivo, contexto objeto
	var nivel uint32
	r, _, _ := procCriarD3D11.Call(
		0, tipoDeDriverHardware, 0, 0, 0, 0, versaoDoSDKD3D11,
		uintptr(unsafe.Pointer(&dispositivo)),
		uintptr(unsafe.Pointer(&nivel)),
		uintptr(unsafe.Pointer(&contexto)),
	)
	if err := hr(r, "criar dispositivo para listar monitores"); err != nil {
		return nil, err
	}
	if err := naoNulo(dispositivo, "criar dispositivo para listar monitores"); err != nil {
		return nil, err
	}
	defer dispositivo.soltar()
	defer contexto.soltar()

	dispDXGI, err := dispositivo.consultar(&iidDispositivoDXGI)
	if err != nil {
		return nil, fmt.Errorf("o dispositivo não fala DXGI: %w", err)
	}
	defer dispDXGI.soltar()

	var adaptador objeto
	r = dispDXGI.chamar(dxgiPegarAdaptador, uintptr(unsafe.Pointer(&adaptador)))
	if err := hr(r, "achar a placa de vídeo"); err != nil {
		return nil, err
	}
	if err := naoNulo(adaptador, "achar a placa de vídeo"); err != nil {
		return nil, err
	}
	defer adaptador.soltar()

	// O TETO DE OITO É UM FREIO, não uma opinião sobre quantos monitores alguém tem.
	// `EnumOutputs` termina devolvendo DXGI_ERROR_NOT_FOUND, e um laço sem teto depende
	// de o driver respeitar isso — dependência que já custou caro neste projeto.
	var lista []MonitorDaTela
	for i := 0; i < 8; i++ {
		var saida objeto
		r = adaptador.chamar(dxgiEnumerarSaidas, uintptr(i), uintptr(unsafe.Pointer(&saida)))
		if uint32(r)&0x80000000 != 0 || saida == 0 {
			break
		}

		var desc descricaoDaSaida
		erroDesc := saida.chamar(dxgiDescricaoDaSaida, uintptr(unsafe.Pointer(&desc)))
		saida.soltar()
		if uint32(erroDesc)&0x80000000 != 0 {
			continue
		}
		if desc.LigadoNaArea == 0 {
			// Monitor reconhecido mas fora da área de trabalho (desligado, ou
			// espelhando outro). Transmitir um destes entregaria imagem vazia.
			continue
		}

		lista = append(lista, MonitorDaTela{
			Indice:  i,
			Nome:    windows.UTF16ToString(desc.Nome[:]),
			Largura: int(desc.Direita - desc.Esquerda),
			Altura:  int(desc.Base - desc.Topo),
			// A ÁREA DE TRABALHO TEM ORIGEM NO MONITOR PRINCIPAL, por definição do
			// Windows: é o único cujo canto superior esquerdo é (0,0). Os outros ficam
			// à direita, à esquerda (coordenada negativa) ou acima dele.
			Principal: desc.Esquerda == 0 && desc.Topo == 0,
		})
	}
	if len(lista) == 0 {
		return nil, fmt.Errorf("nenhum monitor ligado à área de trabalho")
	}
	return lista, nil
}

// amostrarMonitor tira uma miniatura do que está neste monitor agora.
func amostrarMonitor(indice int) ([]byte, error) {
	tela, err := AbrirTela(indice)
	if err != nil {
		return nil, err
	}
	defer tela.Fechar()

	largura, altura := tela.Tamanho()
	if largura <= 0 || altura <= 0 {
		return nil, fmt.Errorf("o monitor %d não informou tamanho", indice)
	}

	// O PRIMEIRO QUADRO DEPOIS DE DUPLICAR VEM PRETO, e isto custou uma volta: a
	// miniatura saía com 472 bytes de PNG, que é o tamanho de um retângulo de uma cor
	// só. A duplicação precisa de um ciclo para engatar — o primeiro `AcquireNextFrame`
	// devolve uma superfície válida e vazia.
	//
	// Descartar o primeiro e ficar com o segundo resolve. `QuadroAtual` e não
	// `ProximoQuadro` porque quem escolhe qual tela compartilhar costuma estar com a
	// área de trabalho parada, e `ProximoQuadro` responde "nada mudou" justamente aí.
	var textura objeto
	for tentativa := 0; tentativa < 6; tentativa++ {
		t, err := tela.QuadroAtual(120)
		if err != nil {
			return nil, err
		}
		if t != 0 {
			if tentativa == 0 {
				// O de engate. Devolve e pede outro.
				t.soltar()
				tela.SoltarQuadro()
				continue
			}
			textura = t
			break
		}
		tela.SoltarQuadro()
	}
	if textura == 0 {
		return nil, fmt.Errorf("o monitor %d não entregou quadro", indice)
	}
	defer textura.soltar()
	defer tela.SoltarQuadro()

	leitura := descricaoDeTextura{
		Largura: uint32(largura), Altura: uint32(altura),
		Niveis: 1, Camadas: 1, Formato: formatoBGRA, AmostrasConta: 1,
		Uso: usoDeLeitura, AcessoDaCPU: cpuPodeLer,
	}
	var destino objeto
	r := tela.dispositivo.chamar(d3dCriarTextura2D,
		uintptr(unsafe.Pointer(&leitura)), 0, uintptr(unsafe.Pointer(&destino)))
	if err := hr(r, "criar a textura de leitura da miniatura"); err != nil {
		return nil, err
	}
	if err := naoNulo(destino, "criar a textura de leitura da miniatura"); err != nil {
		return nil, err
	}
	defer destino.soltar()

	tela.contexto.chamar(d3dCopiarTudo, uintptr(destino), uintptr(textura))

	var mapa mapaDaTextura
	r = tela.contexto.chamar(d3dMapear,
		uintptr(destino), 0, mapaDeLeitura, 0, uintptr(unsafe.Pointer(&mapa)))
	if err := hr(r, "abrir a cópia para leitura"); err != nil {
		return nil, err
	}
	if mapa.Dados == 0 {
		return nil, fmt.Errorf("o Map devolveu ponteiro nulo sem erro")
	}
	img := encolher(mapa.Dados, int(mapa.PassoLinha), largura, altura, LarguraDaMiniatura)
	tela.contexto.chamar(d3dDesmapear, uintptr(destino), 0)

	var buf bytes.Buffer
	// COMPRESSÃO RÁPIDA e não a melhor. A miniatura vive alguns segundos numa janela de
	// escolha; trocar 30 ms de espera por 3 KB a menos seria o negócio errado.
	cod := png.Encoder{CompressionLevel: png.BestSpeed}
	if err := cod.Encode(&buf, img); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// AmostrasPorLado é quantos pontos de origem entram em cada pixel da miniatura.
//
// Três por lado, ou seja nove por pixel. O caminho barato seria pegar UM ponto por
// bloco, e ele produz aquele serrilhado de miniatura mal feita — o texto da tela vira
// chuvisco e a imagem deixa de ser reconhecível, que é a única coisa que ela precisa
// ser. Ler o bloco INTEIRO seria o certo em teoria e custa caro na prática: a memória
// mapeada de uma textura é lida devagar (foi ela que custou 6,9ms por quadro na
// transmissão), e ler os 8 MB de um 1080p inteiro leva dezenas de milissegundos POR
// MONITOR.
//
// Nove pontos por pixel são 590 mil leituras para uma tela de 2 milhões de pixels —
// bom o bastante para o olho e barato o bastante para não fazer a janela demorar.
const AmostrasPorLado = 3

// encolher reduz o quadro BGRA mapeado a uma imagem de `alvoL` de largura.
func encolher(dados uintptr, passo, largura, altura, alvoL int) *image.RGBA {
	if alvoL > largura {
		alvoL = largura
	}
	alvoA := altura * alvoL / largura
	if alvoA < 1 {
		alvoA = 1
	}
	fonte := unsafe.Slice((*byte)(unsafe.Pointer(dados)), passo*altura)
	img := image.NewRGBA(image.Rect(0, 0, alvoL, alvoA))

	for y := 0; y < alvoA; y++ {
		for x := 0; x < alvoL; x++ {
			var somaB, somaG, somaR, quantos int
			for sy := 0; sy < AmostrasPorLado; sy++ {
				// O ponto é tirado do MEIO de cada fatia do bloco, e não da borda:
				// amostrar a borda faria pixels vizinhos lerem a mesma coluna de
				// origem, desperdiçando um terço das leituras.
				oy := ((y*AmostrasPorLado+sy)*2 + 1) * altura / (2 * alvoA * AmostrasPorLado)
				if oy >= altura {
					oy = altura - 1
				}
				linha := oy * passo
				for sx := 0; sx < AmostrasPorLado; sx++ {
					ox := ((x*AmostrasPorLado+sx)*2 + 1) * largura / (2 * alvoL * AmostrasPorLado)
					if ox >= largura {
						ox = largura - 1
					}
					p := linha + ox*4
					if p+2 >= len(fonte) {
						continue
					}
					// BGRA e não RGBA: é a ordem que a duplicação de tela entrega, e
					// trocá-la sem perceber deixa a miniatura com o azul e o vermelho
					// invertidos — defeito que não dá erro e que se vê na hora.
					somaB += int(fonte[p])
					somaG += int(fonte[p+1])
					somaR += int(fonte[p+2])
					quantos++
				}
			}
			if quantos == 0 {
				continue
			}
			d := img.PixOffset(x, y)
			img.Pix[d] = byte(somaR / quantos)
			img.Pix[d+1] = byte(somaG / quantos)
			img.Pix[d+2] = byte(somaB / quantos)
			img.Pix[d+3] = 255
		}
	}
	return img
}
