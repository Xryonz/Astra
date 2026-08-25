package main

import (
	"os"
	"runtime"
	"testing"
	"time"
)

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

func TestDescricaoDaDuplicacaoBate(t *testing.T) {
	precisaDeTela(t)
	tela := abrirTelaParaTeste(t)

	const bgra8 = 87
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

	if quadros == 0 {
		t.Fatal("nenhum quadro em 1,5s -- indice errado, ou nada mudou na tela")
	}
}

func TestSemSoltarNaoVemOutro(t *testing.T) {
	precisaDeTela(t)
	tela := abrirTelaParaTeste(t)

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

	if _, err := tela.ProximoQuadro(50); err == nil {
		t.Fatal("pedir outro quadro com um retido devia falhar")
	}
}
