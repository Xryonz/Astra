package main

import (
	"runtime"
	"testing"
	"time"
)

// DE QUANTO EM QUANTO TEMPO SAI UM QUADRO-CHAVE?
//
// A pergunta decide se a transmissão FUNCIONA para quem chega depois, e a resposta não
// se adivinha: cada compressor tem um padrão diferente, e alguns tratam tela
// compartilhada como conteúdo estático e espaçam os quadros-chave por MUITO tempo.
//
// POR QUE IMPORTA. Um decodificador de H.264 não abre imagem nenhuma antes de receber um
// quadro-chave: os outros quadros só descrevem a DIFERENÇA em relação ao anterior, e sem
// um ponto de partida não há o que diferenciar. Então quem entra na sala depois de a
// transmissão ter começado — que é o caso normal — fica olhando para o vazio até o
// próximo. Se o próximo demorar trinta segundos, a transmissão está quebrada para essa
// pessoa mesmo com tudo funcionando.
//
// Foi exatamente assim que `TestATransmissaoAtravessaDePontaAPonta` falhou uma vez e
// passou na seguinte: a primeira execução perdeu o quadro-chave inicial (escrito na
// faixa antes de o ICE fechar) e ficou trinta segundos esperando outro.
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

	var chaves []time.Duration
	ritmo := NovoRitmo(fps)
	comeco := time.Now()
	fim := comeco.Add(6 * time.Second)
	quadros := 0

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
			continue
		}
		agora := time.Since(comeco)
		err = c.Comprimir(textura, agora, func(nal []byte) {
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

	t.Logf("%d quadros em 6s, %d quadro(s)-chave", quadros, len(chaves))
	for i, q := range chaves {
		if i == 0 {
			t.Logf("  o primeiro em %v", q.Round(time.Millisecond))
		} else {
			t.Logf("  outro em %v (%v depois do anterior)",
				q.Round(time.Millisecond), (q - chaves[i-1]).Round(time.Millisecond))
		}
	}

	if quadros == 0 {
		t.Skip("a tela não mudou em 6s — mexa numa janela e rode de novo")
	}
	if len(chaves) == 0 {
		t.Fatal("nenhum quadro-chave em 6s: quem entrar depois nunca vê imagem")
	}
	// DOIS EM SEIS SEGUNDOS é o mínimo que torna a espera de quem chega depois
	// suportável. Com um só, o espaçamento é maior que a janela medida e não dá para
	// afirmar qual é — o que já é motivo de reprovação.
	if len(chaves) < 2 {
		t.Errorf("só um quadro-chave em 6s: quem entra depois espera mais que isso para ver algo")
	}
}

// temQuadroChave procura um NAL de fatia IDR (tipo 5) no fluxo.
//
// O tipo 5 é o que ancora a imagem: ele se decodifica sozinho, sem depender de nenhum
// quadro anterior. Os parâmetros (7 e 8) costumam vir na frente dele, mas são descrição,
// não imagem — procurar por eles daria falso positivo em compressor que os repete.
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
