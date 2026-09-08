package main

import (
	"os"
	"runtime"
	"sort"
	"testing"
	"time"
)

func TestSondaTetoDeQuadros(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_TETO") == "" {
		t.Skip("ASTRA_SONDA_TETO nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	precisaDeTela(t)
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

	tela, err := AbrirTela(0)
	if err != nil {
		t.Fatalf("abrir a tela: %v", err)
	}
	defer tela.Fechar()

	telaL, telaA := tela.Tamanho()
	t.Logf("tela de %dx%d", telaL, telaA)

	const quadrosPreparados = 12
	prontos := make([]*telaSintetica, quadrosPreparados)
	for i := range prontos {
		prontos[i] = abrirTelaSintetica(t, tela)
		prontos[i].n = i * 7
		prontos[i].pintar(true)
		if err := prontos[i].enviar(); err != nil {
			t.Fatalf("preparar o quadro %d: %v", i, err)
		}
	}
	defer func() {
		for _, p := range prontos {
			p.fechar()
		}
	}()

	medir := func(largura, altura, kbps int) {
		c, err := AbrirCompressor(tela, largura, altura, 60, kbps)
		if err != nil {
			t.Fatalf("abrir o compressor em %dx%d: %v", largura, altura, err)
		}
		defer c.Fechar()

		bytes, saidas := 0, 0
		var tamanhos []int
		receber := func(pronto []byte, _ time.Duration) {
			bytes += len(pronto)
			saidas++
			tamanhos = append(tamanhos, len(pronto))
		}

		comeco := time.Now()
		fim := comeco.Add(3 * time.Second)
		entradas := 0
		for i := 0; time.Now().Before(fim); i++ {
			if err := c.Comprimir(prontos[i%quadrosPreparados].textura,
				time.Duration(i)*time.Second/60, nil, receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
			entradas++
		}
		_ = c.Drenar(receber)
		decorrido := time.Since(comeco).Seconds()

		m := c.Custos.Media()
		porQuadro := m.Total()
		t.Logf("%dx%d a %d kbps · %s%s", largura, altura, kbps, c.Nome,
			map[bool]string{true: " (SEM placa)", false: ""}[c.NaMemoria])
		t.Logf("   teto medido: %.0f quadros/s entrando, %.0f saindo · %.1f Mbps",
			float64(entradas)/decorrido, float64(saidas)/decorrido,
			float64(bytes)*8/decorrido/1_000_000)
		t.Logf("   por quadro %.2fms = cópia %.2f + redução %.2f + compressão %.2f + leitura %.2f",
			emMs(porQuadro), emMs(m.Copia), emMs(m.Reducao), emMs(m.Compressao), emMs(m.Leitura))
		t.Logf("   esperas: pedido de entrada %.2fms · saída pronta %.2fms",
			emMs(m.PedidoDeEntrada), emMs(m.SaidaPronta))
		t.Logf("   com a folga de 2x que TaxaQueCabe exige, isso libera %d quadros/s",
			TaxaQueCabe(porQuadro, 60))

		sort.Ints(tamanhos)
		if len(tamanhos) > 0 {
			meio := tamanhos[len(tamanhos)/2]
			maior := tamanhos[len(tamanhos)-1]
			acimaDeDezVezes := 0
			for _, t := range tamanhos {
				if meio > 0 && t > meio*10 {
					acimaDeDezVezes++
				}
			}
			t.Logf("   quadros: mediana %d B · maior %d B (%.0fx a mediana) · %d acima de 10x",
				meio, maior, float64(maior)/float64(max(meio, 1)), acimaDeDezVezes)
		}
	}

	medir(1920, 1080, 8000)
	medir(1280, 720, 2500)
	medir(telaL, telaA, 8000)
}
