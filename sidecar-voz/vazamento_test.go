package main

// A TRANSMISSÃO SEGURA MEMÓRIA COM O TEMPO?
//
// A pergunta vale para os dois caminhos e vale mais para o novo. O de placa roda há
// meses; o de software (`comprimirNaMemoria`) nasceu agora e aloca coisas que o outro
// não alocava — uma amostra de saída nossa, e uma amostra convertida por quadro que
// atravessa `pendente` antes de ser solta. Um `soltar` esquecido em qualquer um dos dois
// não dá erro, não aparece no perfil do Go, e só se manifesta depois de meia hora de
// chamada: a memória sobe até o Windows começar a paginar, e a pessoa relata que "o
// Astra vai ficando lento".
//
// ESTE TESTE É O ÚNICO JEITO DE PEGAR ISSO ANTES DA PESSOA. Ler o código já pegou um
// vazamento nesta sessão (`medirASaida` reservando outro buffer a cada renegociação de
// formato), mas leitura não prova ausência — só a memória do processo, medida ao longo
// de milhares de quadros, prova.
//
// A MEDIDA É `UsoPrivado` E NÃO O CONJUNTO DE TRABALHO. Ver `memoria.go`: o conjunto de
// trabalho sobe e desce por decisão do Windows, sem nada ter sido alocado nem liberado.
//
// Demora meio minuto por caminho, então fica atrás do próprio portão — a suíte inteira
// não deve levar isso a cada volta:
//
//	ASTRA_TESTE_TELA=1 ASTRA_TESTE_VAZAMENTO=1 go test -run Vazamento -v -timeout 300s

import (
	"os"
	"runtime"
	"strconv"
	"testing"
	"time"
)

// AQUECIMENTO E MEDIÇÃO, separados. Os primeiros segundos de qualquer processo Go sobem
// de memória por motivos que não são vazamento: o heap cresce até o tamanho de regime, o
// Media Foundation carrega DLLs, o driver de vídeo reserva o que precisa. Contar isso
// como vazamento reprovaria um caminho perfeito.
const (
	aquecimentoDoVazamento = 6 * time.Second
	medicaoDoVazamento     = 24 * time.Second
)

// janelaDeMedicao permite alongar a medição de fora.
//
// EXISTE POR UMA PERGUNTA QUE 24 SEGUNDOS NÃO RESPONDEM: vazamento e aquecimento de
// pilha se parecem numa janela curta — os dois sobem. O que os separa é a FORMA da
// curva. Vazamento é linear para sempre; pilha de driver enche e para. A única maneira
// de distinguir é medir por muito mais tempo e ver se a subida continua no mesmo ritmo.
//
//	ASTRA_VAZAMENTO_SEGUNDOS=180 go test -run TestTransmissaoNaoVazaMemoria -v -timeout 900s
func janelaDeMedicao() time.Duration {
	if s := os.Getenv("ASTRA_VAZAMENTO_SEGUNDOS"); s != "" {
		if n, err := strconv.Atoi(s); err == nil && n > 0 {
			return time.Duration(n) * time.Second
		}
	}
	return medicaoDoVazamento
}

func TestTransmissaoNaoVazaMemoria(t *testing.T) {
	precisaDeTela(t)
	if os.Getenv("ASTRA_TESTE_VAZAMENTO") == "" {
		t.Skip("defina ASTRA_TESTE_VAZAMENTO=1 (leva um minuto)")
	}

	for _, caso := range []struct {
		nome      string
		naMemoria bool
	}{
		{"na placa", false},
		{"na memoria", true},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			medirVazamento(t, caso.naMemoria)
		})
	}
}

func medirVazamento(t *testing.T, naMemoria bool) {
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
		t.Skipf("sem tela para capturar: %v", err)
	}
	defer tela.Fechar()
	largura, altura := tela.Tamanho()

	c := abrirParaOCaminho(t, tela, largura, altura, naMemoria)
	defer c.Fechar()
	t.Logf("compressor %q, entrada %s", c.Nome, c.Formato)

	// A SAÍDA É JOGADA FORA de propósito: o que se está medindo é o cano, não a rede.
	// Guardar os quadros faria a memória subir por motivo legítimo e esconderia o
	// vazamento no meio do crescimento esperado.
	var quadros int
	receber := func([]byte) { quadros++ }

	ritmo := NovoRitmo(c.fps)
	comeco := time.Now()
	rodar := func(quanto time.Duration) int {
		antes := quadros
		fim := time.Now().Add(quanto)
		for time.Now().Before(fim) {
			ritmo.Esperar()
			textura, err := tela.ProximoQuadro(100)
			if err != nil {
				if _, perdeu := err.(ErroDeAcessoPerdido); perdeu {
					if err := tela.Remontar(0); err != nil {
						t.Fatalf("recuperar a tela: %v", err)
					}
					continue
				}
				t.Fatalf("capturar: %v", err)
			}
			if textura == 0 {
				if err := c.Drenar(receber); err != nil {
					t.Fatalf("colher o que sobrou: %v", err)
				}
				continue
			}
			if err := c.Comprimir(textura, time.Since(comeco), receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
			textura.soltar()
			tela.SoltarQuadro()
		}
		return quadros - antes
	}

	rodar(aquecimentoDoVazamento)

	// DUAS METADES, E É A COMPARAÇÃO ENTRE ELAS QUE RESPONDE — não o crescimento total.
	//
	// ESTA FOI A LIÇÃO CARA DESTE ARQUIVO. A primeira versão dividia o crescimento pelo
	// número de quadros e reprovava acima de mil bytes por quadro. O caminho de placa
	// reprovou três vezes seguidas, com 3.253, 3.355 e 3.673 bytes por quadro — parecia
	// vazamento reproduzível e não era.
	//
	// O que desmascarou foi alongar a janela. Em 24 segundos o crescimento era +1,3 a
	// +1,9 MB; em 240 segundos, DEZ VEZES mais tempo, foi +2,0 MB. Vazamento multiplica
	// com o tempo; aquilo estabilizava. Era a pilha interna do driver de vídeo enchendo
	// UMA VEZ — custo fixo, que dividido por poucos quadros dá um número por quadro
	// enorme e dividido por muitos dá um número pequeno. A métrica é que estava errada,
	// não o código.
	//
	// Partir a medição ao meio separa os dois sem depender do tamanho da janela: custo de
	// aquecimento acontece na PRIMEIRA metade e some; vazamento cresce nas duas igual.
	janela := janelaDeMedicao()

	runtime.GC()
	m0 := MemoriaDoProcesso()
	if m0 == 0 {
		t.Fatal("não consegui ler a memória do processo")
	}
	// A coleta forçada antes de cada leitura tira da conta o lixo do Go ainda por
	// recolher — sem ela a comparação mediria o coletor em vez do cano.
	q1 := rodar(janela / 2)
	runtime.GC()
	m1 := MemoriaDoProcesso()

	q2 := rodar(janela / 2)
	runtime.GC()
	m2 := MemoriaDoProcesso()

	if q1+q2 == 0 {
		t.Skip("a área de trabalho não mudou; sem quadro para medir")
	}

	primeira := int64(m1) - int64(m0)
	segunda := int64(m2) - int64(m1)
	metade := (janela / 2).Seconds()

	t.Logf("%d + %d quadros em %v", q1, q2, janela)
	t.Logf("  primeira metade  %+6.2f MB  (%d quadros)", float64(primeira)/1e6, q1)
	t.Logf("  segunda metade   %+6.2f MB  (%d quadros)", float64(segunda)/1e6, q2)
	t.Logf("  memória          %.1f MB -> %.1f MB", mb(m0), mb(m2))

	// O LIMITE VALE SÓ PARA A SEGUNDA METADE, e é por segundo para não depender da
	// janela escolhida. 150 KB/s é folgado com propósito: medido, a segunda metade fica
	// perto de zero, e o que este teste existe para pegar é grosso — uma amostra NV12
	// não solta são 1,4 MB por quadro, ou 42 MB/s a 30 quadros. Duzentas e oitenta vezes
	// o limite. Apertar mais transformaria o teste em fonte de alarme falso, que é o
	// jeito conhecido de um teste deixar de ser lido.
	porSegundo := float64(segunda) / metade
	const limitePorSegundo = 150_000.0
	if porSegundo > limitePorSegundo {
		t.Errorf("a segunda metade cresce %.0f KB/s (limite %.0f KB/s): há algo não sendo solto",
			porSegundo/1000, limitePorSegundo/1000)
	}
}

// abrirParaOCaminho liga o compressor pelo caminho pedido.
//
// O de software é aberto por `amarrar` direto porque nesta máquina existe compressor de
// placa, e `AbrirCompressor` sempre o escolheria — a segunda passada nunca rodaria. É a
// mesma razão de `semplaca_test.go`.
func abrirParaOCaminho(t *testing.T, tela *Tela, largura, altura int, naMemoria bool) *Compressor {
	t.Helper()
	if !naMemoria {
		c, err := AbrirCompressor(tela, 1280, 720, 30, 2500)
		if err != nil {
			t.Fatalf("abrir o compressor de placa: %v", err)
		}
		if c.NaMemoria {
			c.Fechar()
			t.Skip("esta máquina não tem compressor de placa; o caso de placa não se aplica")
		}
		return c
	}

	lista, err := ProcurarCompressores()
	if err != nil {
		t.Fatalf("procurar compressores: %v", err)
	}
	defer SoltarCompressores(lista)
	for _, cand := range lista {
		if fala, _ := cand.FalaD3D11(); fala {
			continue
		}
		c, err := amarrar(cand, tela, largura, altura, 1280, 720, 30, 2500, true)
		if err == nil {
			return c
		}
	}
	t.Skip("nenhum compressor de software nesta máquina")
	return nil
}

func mb(bytes uint64) float64 { return float64(bytes) / 1e6 }
