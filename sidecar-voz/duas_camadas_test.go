package main

import (
	"runtime"
	"testing"
	"time"
)

func TestDuasCamadasSobreAMesmaCaptura(t *testing.T) {
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

	const (
		fpsCheio = 60
		cheiaL   = 1280
		cheiaA   = 720
	)
	finaL, finaA := tamanhoDaCamadaFina(cheiaL, cheiaA)

	cheia, err := AbrirCompressor(tela, cheiaL, cheiaA, fpsCheio, 4000)
	if err != nil {
		t.Fatalf("abrir a camada cheia: %v", err)
	}
	defer cheia.Fechar()

	fina, err := AbrirCompressor(tela, finaL, finaA, fpsDaCamadaFina, kbpsDaCamadaFina)
	if err != nil {
		t.Fatalf("a segunda camada nao abriu na mesma captura: %v", err)
	}
	defer fina.Fechar()

	t.Logf("cheia: %s (%dx%d, na placa=%v)", cheia.Nome, cheiaL, cheiaA, !cheia.NaMemoria)
	t.Logf("fina:  %s (%dx%d, na placa=%v)", fina.Nome, finaL, finaA, !fina.NaMemoria)

	if cheia.NaMemoria || fina.NaMemoria {
		t.Log("ao menos uma camada caiu para software; a guarda do emissor recusaria a segunda")
	}

	var bytesCheios, bytesFinos, quadrosCheios, quadrosFinos int
	var tempoCheia, tempoFina time.Duration
	vezDaFina := false

	ritmo := NovoRitmo(fpsCheio)
	comeco := time.Now()
	fim := comeco.Add(8 * time.Second)

	for time.Now().Before(fim) && quadrosCheios < 120 {
		ritmo.Esperar()
		textura, err := tela.ProximoQuadro(100)
		if err != nil {
			t.Fatalf("capturar: %v", err)
		}
		if textura == 0 {
			continue
		}

		vezDaFina = !vezDaFina
		agora := time.Since(comeco)

		marco := time.Now()
		if err := cheia.Comprimir(textura, agora, nil, func(q []byte, _ time.Duration) {
			bytesCheios += len(q)
			quadrosCheios++
		}); err != nil {
			t.Fatalf("comprimir a cheia: %v", err)
		}
		tempoCheia += time.Since(marco)

		if vezDaFina {
			marco = time.Now()
			if err := fina.Comprimir(textura, agora, nil, func(q []byte, _ time.Duration) {
				bytesFinos += len(q)
				quadrosFinos++
			}); err != nil {
				t.Fatalf("comprimir a fina: %v", err)
			}
			tempoFina += time.Since(marco)
		}

		textura.soltar()
		tela.SoltarQuadro()
	}

	if quadrosCheios == 0 {
		t.Fatal("a camada cheia nao produziu nada")
	}
	if quadrosFinos == 0 {
		t.Fatal("a camada fina nao produziu nada: as duas nao convivem nesta maquina")
	}

	decorrido := time.Since(comeco).Seconds()
	t.Logf("cheia: %d quadros, %.0f kbps", quadrosCheios, float64(bytesCheios)*8/decorrido/1000)
	t.Logf("fina:  %d quadros, %.0f kbps", quadrosFinos, float64(bytesFinos)*8/decorrido/1000)
	t.Logf("custo da fina: %.0f%% dos quadros e %.0f%% dos bytes da cheia",
		float64(quadrosFinos)/float64(quadrosCheios)*100,
		float64(bytesFinos)/float64(bytesCheios)*100)

	porQuadroCheia := tempoCheia / time.Duration(quadrosCheios)
	porQuadroFina := tempoFina / time.Duration(quadrosFinos)
	t.Logf("tempo de parede por quadro: cheia %v, fina %v", porQuadroCheia, porQuadroFina)
	t.Logf("a fina soma %v em %v de captura (%.1f%% do relogio)",
		tempoFina, time.Since(comeco), float64(tempoFina)/float64(time.Since(comeco))*100)

	if orcamento := time.Second / fpsCheio; porQuadroCheia+porQuadroFina > orcamento {
		t.Errorf("as duas somam %v por quadro e o orcamento de %d fps e %v: nao cabe",
			porQuadroCheia+porQuadroFina, fpsCheio, orcamento)
	}

	if quadrosFinos > quadrosCheios {
		t.Errorf("a fina rendeu %d quadros contra %d da cheia: ela deveria correr a meio ritmo",
			quadrosFinos, quadrosCheios)
	}
}
