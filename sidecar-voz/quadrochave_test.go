package main

import (
	"runtime"
	"testing"
	"time"
)

func TestOCompressorDaQuadroChaveComRegularidade(t *testing.T) {
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

	const fps = 30
	c, err := AbrirCompressor(tela, 1280, 720, fps, 2500)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()
	t.Logf("compressor: %s", c.Nome)

	const quadrosNecessarios = 240
	var chaves []time.Duration
	ritmo := NovoRitmo(fps)
	comeco := time.Now()
	fim := comeco.Add(25 * time.Second)
	quadros := 0

	for time.Now().Before(fim) && quadros < quadrosNecessarios {
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
			continue
		}
		agora := time.Since(comeco)
		err = c.Comprimir(textura, agora, func(nal []byte, _ time.Duration) {
			if temQuadroChave(nal) {
				chaves = append(chaves, agora)
			}
		})
		textura.soltar()
		tela.SoltarQuadro()
		if err != nil {
			t.Fatalf("comprimir: %v", err)
		}
		quadros++
	}

	t.Logf("%d quadros em %v, %d quadro(s)-chave",
		quadros, time.Since(comeco).Round(100*time.Millisecond), len(chaves))
	for i, q := range chaves {
		if i == 0 {
			t.Logf("  o primeiro em %v", q.Round(time.Millisecond))
		} else {
			t.Logf("  outro em %v (%v depois do anterior)",
				q.Round(time.Millisecond), (q - chaves[i-1]).Round(time.Millisecond))
		}
	}

	if quadros < quadrosNecessarios {
		t.Skipf("só %d quadros em %v (precisa de %d): a tela mal mudou — mexa numa janela e rode de novo",
			quadros, time.Since(comeco).Round(time.Second), quadrosNecessarios)
	}
	if len(chaves) == 0 {
		t.Fatalf("nenhum quadro-chave em %d quadros: quem entrar depois nunca vê imagem", quadros)
	}

	if len(chaves) < 2 {
		t.Errorf("só um quadro-chave em %d quadros: quem entra depois espera mais que isso para ver algo",
			quadros)
	}
}

func TestPedirQuadroChaveFuncionaDeVerdade(t *testing.T) {
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

	const fps = 30
	c, err := AbrirCompressor(tela, 1280, 720, fps, 2500)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()

	if c.comandos == 0 {
		t.Skipf("%s não expõe a via de comando — nada a pedir", c.Nome)
	}

	ritmo := NovoRitmo(fps)
	comeco := time.Now()
	rodar := func(ate time.Duration, aoSair func(bool, time.Duration)) {
		for time.Since(comeco) < ate {
			ritmo.Esperar()
			textura, err := tela.ProximoQuadro(100)
			if err != nil || textura == 0 {
				continue
			}
			agora := time.Since(comeco)
			_ = c.Comprimir(textura, agora, func(nal []byte, _ time.Duration) {
				aoSair(temQuadroChave(nal), agora)
			})
			textura.soltar()
			tela.SoltarQuadro()
		}
	}

	var ultimaChaveAntes time.Duration
	rodar(2500*time.Millisecond, func(chave bool, quando time.Duration) {
		if chave {
			ultimaChaveAntes = quando
		}
	})
	t.Logf("antes do pedido, o último quadro-chave foi em %v", ultimaChaveAntes.Round(time.Millisecond))

	if !c.ForcarQuadroChave() {
		t.Fatalf("%s recusou a ordem de quadro-chave, e a sonda dizia que suportava", c.Nome)
	}
	pedidoEm := time.Since(comeco)

	var atendidoEm time.Duration
	rodar(4*time.Second, func(chave bool, quando time.Duration) {
		if chave && atendidoEm == 0 && quando > pedidoEm {
			atendidoEm = quando
		}
	})

	if atendidoEm == 0 {
		t.Fatal("pedi um quadro-chave e nenhum veio em 1,5s: a ordem é aceita e ignorada")
	}
	demora := atendidoEm - pedidoEm
	t.Logf("pedido em %v, atendido em %v (%v depois)",
		pedidoEm.Round(time.Millisecond), atendidoEm.Round(time.Millisecond), demora.Round(time.Millisecond))

	if demora > 1500*time.Millisecond {
		t.Errorf("demorou %v para atender — perto demais do intervalo natural para ser o pedido", demora)
	}
}

func temQuadroChave(fluxo []byte) bool {
	for i := 0; i+4 < len(fluxo); i++ {
		if fluxo[i] != 0 || fluxo[i+1] != 0 {
			continue
		}
		inicio := 0
		if fluxo[i+2] == 1 {
			inicio = i + 3
		} else if fluxo[i+2] == 0 && i+3 < len(fluxo) && fluxo[i+3] == 1 {
			inicio = i + 4
		} else {
			continue
		}
		if inicio < len(fluxo) && fluxo[inicio]&0x1F == 5 {
			return true
		}
	}
	return false
}
