package main

import (
	"runtime"
	"testing"
	"time"
	"unsafe"
)

func TestCopiarDentroDaPlaca(t *testing.T) {
	precisaDeTela(t)
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	tela, err := AbrirTela(0)
	if err != nil {
		t.Fatalf("abrir a tela: %v", err)
	}
	defer tela.Fechar()

	largura, altura := tela.Tamanho()

	leitura := descricaoDeTextura{
		Largura:       uint32(largura),
		Altura:        uint32(altura),
		Niveis:        1,
		Camadas:       1,
		Formato:       formatoBGRA,
		AmostrasConta: 1,
		Uso:           usoDeLeitura,
		AcessoDaCPU:   cpuPodeLer,
	}
	var destino objeto
	r := tela.dispositivo.chamar(d3dCriarTextura2D,
		uintptr(unsafe.Pointer(&leitura)), 0, uintptr(unsafe.Pointer(&destino)))
	if err := hr(r, "criar a textura de leitura"); err != nil {
		t.Fatalf("%v -- indice do CreateTexture2D errado, ou a descricao nao bate", err)
	}
	defer destino.soltar()

	var textura objeto
	prazo := time.Now().Add(3 * time.Second)
	for time.Now().Before(prazo) {
		textura, err = tela.ProximoQuadro(200)
		if err != nil {
			t.Fatalf("pegar quadro: %v", err)
		}
		if textura != 0 {
			break
		}
	}
	if textura == 0 {
		t.Skip("a tela nao mudou em 3s -- mexa o mouse e rode de novo")
	}

	tela.contexto.chamar(d3dCopiarTudo, uintptr(destino), uintptr(textura))
	textura.soltar()
	tela.SoltarQuadro()

	var mapa mapaDaTextura
	r = tela.contexto.chamar(d3dMapear,
		uintptr(destino), 0, mapaDeLeitura, 0, uintptr(unsafe.Pointer(&mapa)))
	if err := hr(r, "abrir a textura copiada"); err != nil {
		t.Fatalf("%v -- indice do Map errado", err)
	}
	if mapa.Dados == 0 {
		t.Fatal("o Map devolveu ponteiro nulo sem erro -- indice errado")
	}

	linha := altura / 2
	inicio := uintptr(linha) * uintptr(mapa.PassoLinha)
	amostra := unsafe.Slice((*byte)(unsafe.Pointer(mapa.Dados+inicio)), int(mapa.PassoLinha))

	naoZero := 0
	for _, b := range amostra {
		if b != 0 {
			naoZero++
		}
	}
	tela.contexto.chamar(d3dDesmapear, uintptr(destino), 0)

	t.Logf("linha %d de %dx%d: %d bytes nao-zero de %d (passo %d)",
		linha, largura, altura, naoZero, len(amostra), mapa.PassoLinha)

	if naoZero == 0 {
		t.Error("a copia nao trouxe pixel nenhum -- indice do CopyResource errado, ou a textura nao foi copiada")
	}
}

func TestComoOCompressorEComandado(t *testing.T) {
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

	lista, err := ProcurarCompressores()
	if err != nil || len(lista) == 0 {
		t.Skipf("nada para perguntar: %v", err)
	}
	defer SoltarCompressores(lista)

	for _, cand := range lista {
		noAtivador := numeroDoAtributo(cand.ativador, &chaveAssincrono)

		t2, err := cand.Montar()
		if err != nil {
			t.Logf("%-46s nao liga: %v", cand.Nome, err)
			continue
		}
		noTransformador := uint32(0)
		var atributos objeto
		if r := t2.chamar(transPegarAtributos, uintptr(unsafe.Pointer(&atributos))); uint32(r)&0x80000000 == 0 {
			noTransformador = numeroDoAtributo(atributos, &chaveAssincrono)
			atributos.soltar()
		}
		temFila := false
		if g, err := t2.consultar(&iidGeradorDeEventos); err == nil {
			temFila = true
			g.soltar()
		}
		t2.soltar()

		t.Logf("%-46s ativador=%d transformador=%d temFilaDeRecados=%v",
			cand.Nome, noAtivador, noTransformador, temFila)
	}
}

func TestTransmissaoDaSessenta(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	m, err := MedirTransmissao(0, 2*time.Second, 0, 0, 4000)

	t.Logf("compressor: %s (entrada %s, assincrono=%v)", m.Compressor, m.Formato, m.Assincrono)
	if err != nil {
		t.Fatalf("medir: %v (depois de %d quadros)", err, m.Quadros)
	}

	t.Logf("tela %dx%d, taxa declarada %d", m.Largura, m.Altura, m.Fps)
	t.Logf("%d quadros em %v (a taxa depende do que muda na tela: %.1f/s)",
		m.Quadros, m.Duracao.Round(time.Millisecond), m.PorSegundo())
	t.Logf("CUSTO POR QUADRO: %v de um orcamento de %v -> %.0f%% de folga",
		m.CustoPorQuadro().Round(10*time.Microsecond),
		(time.Second / time.Duration(m.Fps)).Round(10*time.Microsecond),
		m.Folga()*100)
	med := m.Custos.Media()
	t.Logf("  copiar na placa   %8.1fus", float64(med.Copia.Nanoseconds())/1000)
	t.Logf("  reduzir           %8.1fus", float64(med.Reducao.Nanoseconds())/1000)

	t.Logf("  comprimir         %8.1fus  (entregar + colher o que estava pronto)",
		float64(med.Compressao.Nanoseconds())/1000)
	t.Logf("    esperando ele pedir entrada  %8.1fus  (a placa ocupada de verdade)",
		float64(med.PedidoDeEntrada.Nanoseconds())/1000)
	t.Logf("    esperando a saida ficar pronta %6.1fus  (nos parados)",
		float64(med.SaidaPronta.Nanoseconds())/1000)
	t.Logf("  ler os NALs       %8.1fus", float64(med.Leitura.Nanoseconds())/1000)
	t.Logf("PROCESSADOR: %.3f nucleos (o caminho antigo pela memoria principal custava 0,84)",
		m.Nucleos())
	t.Logf("%d pedacos, %d bytes = %.0f kbps", m.Pedacos, m.Bytes, m.Kbps())

	if m.Quadros < 20 {
		t.Skipf("so %d quadros em 2s -- a tela estava parada demais para medir; mexa numa janela e rode de novo", m.Quadros)
	}
	if m.Pedacos == 0 {
		t.Error("nenhum pedaco de H.264 saiu com quadros entrando -- o compressor engoliu tudo")
	}

	if m.Folga() < 0 {
		t.Errorf("cada quadro custa %v e o orcamento e %v -- esta maquina nao sustenta %d/s",
			m.CustoPorQuadro(), time.Second/time.Duration(m.Fps), m.Fps)
	}
}

func TestTransmissaoReduzida(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	m, err := MedirTransmissao(0, 2*time.Second, 1280, 720, 2500)
	if err != nil {
		t.Fatalf("reduzir para 720p: %v", err)
	}

	t.Logf("compressor: %s (entrada %s)", m.Compressor, m.Formato)
	med := m.Custos.Media()
	t.Logf("saida %dx%d: %d quadros, custo %v por quadro (%.0f%% de folga)",
		m.Largura, m.Altura, m.Quadros,
		m.CustoPorQuadro().Round(10*time.Microsecond), m.Folga()*100)
	t.Logf("  reduzir %.1fus | comprimir %.1fus | ler %.1fus | %.3f nucleos",
		float64(med.Reducao.Nanoseconds())/1000,
		float64(med.Compressao.Nanoseconds())/1000,
		float64(med.Leitura.Nanoseconds())/1000,
		m.Nucleos())
	t.Logf("%d pedacos, %.0f kbps", m.Pedacos, m.Kbps())

	if m.Pedacos == 0 {
		t.Error("aceitou os tamanhos mas nao produziu H.264 -- reducao so no papel")
	}
	if m.Folga() < 0 {
		t.Errorf("reduzido ainda custa %v por quadro, acima do orcamento de %v",
			m.CustoPorQuadro(), time.Second/time.Duration(m.Fps))
	}
	if m.Largura != 1280 || m.Altura != 720 {
		t.Errorf("pedi 1280x720 e saiu %dx%d", m.Largura, m.Altura)
	}
}

func TestAlvoDeSaida(t *testing.T) {
	casos := []struct {
		nome                     string
		largura, altura, pessoas int
		querL, querA             int
	}{
		{"1080p a dois fica 1080p", 1920, 1080, 2, 1920, 1080},
		{"1080p com tres cai pra 720", 1920, 1080, 3, 1280, 720},
		{"1440p a dois cai pro teto de 1080", 2560, 1440, 2, 1920, 1080},
		{"4K com sala cheia cai pra 720", 3840, 2160, 5, 1280, 720},
		{"tela pequena nao e esticada", 1280, 720, 2, 1280, 720},

		{"ultralarga mantem proporcao", 3440, 1440, 2, 2580, 1080},
	}
	for _, c := range casos {
		l, a := AlvoDeSaida(c.largura, c.altura, c.pessoas)
		if l != c.querL || a != c.querA {
			t.Errorf("%s: %dx%d com %d pessoas -> %dx%d, queria %dx%d",
				c.nome, c.largura, c.altura, c.pessoas, l, a, c.querL, c.querA)
		}
		if l%2 != 0 || a%2 != 0 {
			t.Errorf("%s: %dx%d tem lado impar -- o H.264 guarda cor em blocos de 2x2", c.nome, l, a)
		}
	}
}
