package main

import (
	"os"
	"runtime"
	"testing"
	"time"
)

// SONDA DA CAPTURA DE TELA.
//
// Índice de vtable errado em COM não dá erro: chama outra função, com os argumentos
// errados, e trava ou devolve lixo. Conferir a tabela contra a documentação é o
// primeiro passo, mas não é prova — a documentação lista os métodos em ordem
// alfabética, e a tabela segue a ordem de DECLARAÇÃO. Foi assim que o cancelador de
// eco custou caro, e a lição de lá vale aqui: quem responde é a máquina.
//
// Precisa de tela de verdade, então roda só com ASTRA_TESTE_TELA=1.

func precisaDeTela(t *testing.T) {
	t.Helper()
	if os.Getenv("ASTRA_TESTE_TELA") == "" {
		t.Skip("defina ASTRA_TESTE_TELA=1 (precisa de monitor de verdade)")
	}
}

func abrirTelaParaTeste(t *testing.T) *Tela {
	t.Helper()
	runtime.LockOSThread()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	tela, err := AbrirTela(0)
	if err != nil {
		fecharCOM()
		runtime.UnlockOSThread()
		t.Fatalf("abrir a tela: %v", err)
	}
	t.Cleanup(func() {
		tela.Fechar()
		fecharCOM()
		runtime.UnlockOSThread()
	})
	return tela
}

// A PROVA DE QUE OS ÍNDICES ESTÃO CERTOS, e não um teste de "abriu sem explodir".
//
// O que a torna forte é o FORMATO. A Microsoft documenta que a imagem da área de
// trabalho é SEMPRE DXGI_FORMAT_B8G8R8A8_UNORM, que vale 87. Esse número está num
// campo lá no meio da estrutura: para ele sair 87, o `GetDesc` precisa ser mesmo o
// índice 7 (ou seja, os sete métodos herdados de IDXGIObject estão contados certo) E
// o nosso struct precisa ter o mesmo tamanho e a mesma ordem de campos que o do
// Windows. Um erro em qualquer um dos dois produz outro número.
func TestDescricaoDaDuplicacaoBate(t *testing.T) {
	precisaDeTela(t)
	tela := abrirTelaParaTeste(t)

	const bgra8 = 87 // DXGI_FORMAT_B8G8R8A8_UNORM
	if tela.desc.Formato != bgra8 {
		t.Fatalf("formato %d, esperado %d (BGRA de 8 bits) -- indice de vtable ou layout do struct errado",
			tela.desc.Formato, bgra8)
	}

	l, a := tela.Tamanho()
	if l < 640 || a < 480 || l > 16384 || a > 16384 {
		t.Fatalf("tamanho implausivel: %dx%d", l, a)
	}

	hz := tela.Hz()
	if hz < 24 || hz > 500 {
		t.Fatalf("taxa implausivel: %d Hz", hz)
	}
	t.Logf("monitor: %dx%d @ %d Hz", l, a, hz)
}

// O quadro tem que CHEGAR, e chegar no ritmo do monitor.
//
// Este é o número que decide a transmissão inteira: se a captura não alcança a taxa
// do monitor, nenhum ajuste depois disso alcança. Mede em vez de afirmar — a máquina
// de quem roda é que responde.
//
// A tela precisa estar MUDANDO. Numa área de trabalho parada a duplicação
// legitimamente não entrega quadro nenhum (é o ponto dela), então o teste aceita um
// piso baixo e reporta o que viu; quem quiser o número real mexe o mouse por cima de
// algo animado enquanto ele roda.
func TestQuadrosChegam(t *testing.T) {
	precisaDeTela(t)
	tela := abrirTelaParaTeste(t)

	const janela = 1500 * time.Millisecond
	comeco := time.Now()
	quadros, esperas := 0, 0
	for time.Since(comeco) < janela {
		textura, err := tela.ProximoQuadro(50)
		if err != nil {
			if _, perdeu := err.(ErroDeAcessoPerdido); perdeu {
				t.Skip("acesso perdido (tela cheia ou troca de area de trabalho) -- nao da pra medir")
			}
			t.Fatalf("pegar quadro: %v", err)
		}
		if textura == 0 {
			esperas++
			continue
		}
		quadros++
		textura.soltar()
		tela.SoltarQuadro()
	}

	fps := float64(quadros) / time.Since(comeco).Seconds()
	t.Logf("%d quadros em %.1fs = %.0f fps (mais %d esperas sem mudanca)",
		quadros, time.Since(comeco).Seconds(), fps, esperas)

	// O piso prova que o CAMINHO funciona: `AcquireNextFrame`, `QueryInterface` para
	// textura e `ReleaseFrame` no índice certo. Um índice errado em qualquer um dos
	// três dá zero quadro, não "poucos quadros".
	if quadros == 0 {
		t.Fatal("nenhum quadro em 1,5s -- indice errado, ou nada mudou na tela")
	}
}

// SOLTAR É OBRIGATÓRIO, e o efeito de esquecer é silêncio e não erro.
//
// A API só entrega o próximo quadro depois de devolvido o anterior. Este teste
// existe porque essa é a falha mais fácil de introduzir num laço de captura e a mais
// difícil de diagnosticar depois: a transmissão simplesmente congela na primeira
// imagem, sem nada no registro.
func TestSemSoltarNaoVemOutro(t *testing.T) {
	precisaDeTela(t)
	tela := abrirTelaParaTeste(t)

	// Segura um quadro sem devolver.
	var textura objeto
	prazo := time.Now().Add(2 * time.Second)
	for time.Now().Before(prazo) && textura == 0 {
		tx, err := tela.ProximoQuadro(100)
		if err != nil {
			t.Skipf("nao consegui um primeiro quadro: %v", err)
		}
		textura = tx
	}
	if textura == 0 {
		t.Skip("tela parada demais para o teste")
	}
	defer func() {
		textura.soltar()
		tela.SoltarQuadro()
	}()

	// Com o quadro retido, a guarda tem que recusar em vez de deixar o COM travar.
	if _, err := tela.ProximoQuadro(50); err == nil {
		t.Fatal("pedir outro quadro com um retido devia falhar")
	}
}
