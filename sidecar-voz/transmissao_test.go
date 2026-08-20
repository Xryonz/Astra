package main

import (
	"runtime"
	"testing"
	"time"
	"unsafe"
)

// SONDA DA TRANSMISSÃO.
//
// Duas perguntas, e as duas só têm resposta honesta na máquina de quem pergunta:
//
//  1. a cópia dentro da placa funciona? (o índice 47 do `ID3D11DeviceContext`)
//  2. o caminho inteiro dá 60 quadros por segundo?
//
// A primeira existe porque índice de vtable errado em COM NÃO DÁ ERRO — chama outra
// função, com os argumentos errados. `CopyResource` é o 47º da tabela, a contagem mais
// funda do projeto, e um erro ali daria transmissão preta ou travamento, nunca uma
// mensagem. Conferir de olho contra o cabeçalho não é conferir.

// A PROVA DE QUE A CÓPIA DENTRO DA PLACA ESTÁ CERTA.
//
// O truque é ter um invariante que só uma cópia de verdade satisfaz: a área de
// trabalho do Windows não é preta. Se `CopyResource` estivesse no índice errado, a
// textura de destino continuaria zerada e a leitura sairia toda zero.
//
// De quebra, prova `CreateTexture2D` (5), `Map` (14) e `Unmap` (15) na mesma volta:
// qualquer um deles errado impede a leitura de chegar ao fim.
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

	// Textura de LEITURA: mora onde a CPU alcança. É o contrário do que a
	// transmissão faz — e é justamente por isso que serve de prova, porque só ela
	// deixa conferir o pixel.
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

	// Espera um quadro DE VERDADE. Numa tela parada a duplicação devolve "nada
	// mudou", que não serve para a prova — daí o laço com prazo.
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

	// Uma linha do meio da tela basta: se a cópia não aconteceu, ela é toda zero.
	// Linha do meio e não a primeira porque barra de título escura no topo daria
	// zeros legítimos e um falso negativo.
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

	// ESTE É O NÚMERO QUE PROVA O ÍNDICE 47. Área de trabalho toda preta na linha do
	// meio é possível em teoria, mas então a transmissão dessa tela também seria
	// preta — e o teste estaria certo em reclamar.
	if naoZero == 0 {
		t.Error("a copia nao trouxe pixel nenhum -- indice do CopyResource errado, ou a textura nao foi copiada")
	}
}

// ONDE MORA "ESTE COMPRESSOR É ASSÍNCRONO?".
//
// A pergunta parece de detalhe e decide o cano inteiro: um compressor assíncrono
// RECUSA quadro que ele não pediu, com "no momento não está aceitando mais entrada" —
// erro que soa como fila cheia e é, na verdade, "você falou fora da vez".
//
// A sonda existe porque o `MF_TRANSFORM_ASYNC` lido do transformador voltou ZERO num
// compressor que se comporta como assíncrono. Já erramos esse tipo de leitura uma vez
// aqui, com o `MF_SA_D3D11_AWARE`, e a lição foi a mesma: "não tenho essa chave" é
// indistinguível de "não" para quem não sabe onde a chave mora. Então pergunta-se nos
// dois lugares e à própria interface.
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

// A PERGUNTA DO DONO, respondida com número: dá 60 quadros por segundo?
//
// Mede o caminho inteiro (duplicar, copiar na placa, comprimir, ler o H.264) e conta.
// Não é estimativa: é o mesmo trabalho que a transmissão de verdade faz, medido por
// dois segundos.
func TestTransmissaoDaSessenta(t *testing.T) {
	precisaDeTela(t)
	precisaDeVideo(t)

	m, err := MedirTransmissao(0, 2*time.Second, 4000)

	// O RETRATO SAI ANTES DO VEREDITO, e de propósito. A medição devolve o que
	// conseguiu mesmo quando falha no meio, e "qual compressor pegou, em que formato,
	// por recado ou por chamada" é justamente a informação que separa um defeito do
	// outro. Falhar sem imprimir isso transforma cada erro numa nova investigação.
	t.Logf("compressor: %s (entrada %s, assincrono=%v)", m.Compressor, m.Formato, m.Assincrono)
	if err != nil {
		t.Fatalf("medir: %v (depois de %d quadros)", err, m.Quadros)
	}

	t.Logf("tela %dx%d, taxa declarada %d", m.Largura, m.Altura, m.Fps)
	t.Logf("%d quadros em %v = %.1f/s", m.Quadros, m.Duracao.Round(time.Millisecond), m.PorSegundo())
	t.Logf("%d pedacos, %d bytes = %.0f kbps", m.Pedacos, m.Bytes, m.Kbps())

	if m.Quadros == 0 {
		t.Fatal("nenhum quadro passou pelo cano")
	}
	// O H.264 pode segurar os primeiros quadros antes de fechar o primeiro pedaço,
	// mas dois segundos são muito mais que isso. Zero aqui significa que a saída não
	// está saindo, e transmissão sem bytes é tela preta do outro lado.
	if m.Pedacos == 0 {
		t.Error("nenhum pedaco de H.264 saiu em 2s -- o compressor engoliu tudo")
	}
	if m.PorSegundo() < 55 {
		t.Errorf("so %.1f quadros/s -- abaixo dos 60 pedidos", m.PorSegundo())
	}
}
