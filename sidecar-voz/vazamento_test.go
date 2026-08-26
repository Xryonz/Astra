package main

import (
	"os"
	"runtime"
	"strconv"
	"testing"
	"time"
)

const (
	aquecimentoDoVazamento = 6 * time.Second
	medicaoDoVazamento     = 24 * time.Second
)

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

	var quadros int
	receber := func([]byte, time.Duration) { quadros++ }

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

	janela := janelaDeMedicao()

	runtime.GC()
	m0 := MemoriaDoProcesso()
	if m0 == 0 {
		t.Fatal("não consegui ler a memória do processo")
	}

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

	porSegundo := float64(segunda) / metade
	const limitePorSegundo = 150_000.0
	if porSegundo > limitePorSegundo {
		t.Errorf("a segunda metade cresce %.0f KB/s (limite %.0f KB/s): há algo não sendo solto",
			porSegundo/1000, limitePorSegundo/1000)
	}
}

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
