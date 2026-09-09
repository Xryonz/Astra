package main

import (
	"fmt"
	"os"
	"runtime"
	"testing"
	"time"
)

func TestSondaPisoDeSoftware(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_PISO") == "" {
		t.Skip("ASTRA_SONDA_PISO nao definida — sonda de investigacao, nao roda no dia a dia")
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
	largura, altura := tela.Tamanho()

	medirUm := func(c *Compressor, fps int) int {
		defer c.Fechar()

		bytes := 0
		receber := func(pronto []byte, _ time.Duration) { bytes += len(pronto) }

		ritmo := NovoRitmo(fps)
		quadros, vazios := 0, 0
		var falha error
		antesCpu, antes := cpuGasta(t), time.Now()
		prazo := antes.Add(5 * time.Second)

		for time.Now().Before(prazo) {
			ritmo.Esperar()
			textura, err := tela.ProximoQuadro(100)
			if err != nil {
				falha = fmt.Errorf("pegar quadro: %w", err)
				break
			}
			if textura == 0 {
				vazios++
				continue
			}
			err = c.Comprimir(textura, time.Since(antes), tela.SoltarQuadro, receber)
			textura.soltar()
			tela.SoltarQuadro()
			if err != nil {
				falha = fmt.Errorf("no quadro %d: %w", quadros, err)
				break
			}
			quadros++
		}
		_ = c.Drenar(receber)

		gastoCpu, decorrido := cpuGasta(t)-antesCpu, time.Since(antes)
		trabalho := c.Custos.Media().Total()
		orcamento := time.Second / time.Duration(fps)
		cabe := TaxaQueCabe(trabalho, AsTaxasQueOAstraOferece[0])

		t.Logf("%s · %dx%d @%d a %d kbps", c.Nome, c.saidaL, c.saidaA, fps, c.kbps)
		if falha != nil {
			t.Logf("   PAROU %s (%d quadros, %d esperas sem mudança na tela)", falha, quadros, vazios)
			return 0
		}
		if quadros < 20 {
			t.Logf("   só %d quadros em 5s — a tela estava parada demais para medir", quadros)
			return 0
		}
		t.Logf("   entregou %.0f quadros/s · %.1f Mbps · processador %.1f%% de UM núcleo",
			float64(quadros)/decorrido.Seconds(),
			float64(bytes)*8/decorrido.Seconds()/1_000_000,
			100*gastoCpu.Seconds()/decorrido.Seconds())
		t.Logf("   trabalho por quadro %.2fms de um orçamento de %.1fms · a escada escolheria %d/s",
			emMs(trabalho), emMs(orcamento), cabe)
		return cabe
	}

	medir := func(saidaL, saidaA, fps, kbps int) {
		lista, err := ProcurarCompressores()
		if err != nil {
			t.Fatalf("procurar compressores: %v", err)
		}
		defer SoltarCompressores(lista)

		melhor, vencedor := 0, ""
		memL, memA := tetoDeSoftware(saidaL, saidaA)
		for _, cand := range lista {
			c, err := amarrar(cand, tela, largura, altura, memL, memA, fps, kbps, true)
			if err != nil {
				t.Logf("%s · nem abriu pela memória: %v", cand.Nome, err)
				continue
			}
			nome := c.Nome
			if cabe := medirUm(c, fps); cabe > melhor {
				melhor, vencedor = cabe, nome
			}
		}

		if melhor < 30 {
			t.Errorf("PISO FURADO: pedindo %dx%d @%d, nenhum compressor desta máquina sustenta 30/s "+
				"pelo caminho da memória (o melhor chegou a %d/s)", saidaL, saidaA, fps, melhor)
			return
		}
		t.Logf("PISO DE PÉ em %dx%d @%d: %s sustenta %d/s", saidaL, saidaA, fps, vencedor, melhor)
	}

	medir(1280, 720, 30, 2500)
	medir(1920, 1080, 60, 8000)
}
