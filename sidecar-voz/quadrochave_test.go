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

// PEDIR UM QUADRO-CHAVE FUNCIONA MESMO?
//
// A sonda (`TestSondaDoCodecAPI`) diz que o compressor SUPORTA a ordem. Suportar não é
// obedecer: `SetValue` pode devolver sucesso e o compressor seguir o próprio compasso.
// Este teste espera o intervalo natural passar de longe, PEDE, e confere que o
// quadro-chave veio muito antes do que viria sozinho.
//
// Sem ele, o caminho inteiro de recuperação de imagem (o outro lado pede, este atende)
// seria construído sobre uma promessa não conferida.
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

	// PRIMEIRO O SILÊNCIO. Dois segundos e meio deixam o quadro-chave inicial para trás
	// e ficam BEM antes do próximo natural (medido em 5s), então qualquer quadro-chave
	// depois do pedido só pode ter vindo do pedido.
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
			_ = c.Comprimir(textura, agora, func(nal []byte) {
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

	// UM SEGUNDO E MEIO, e o número tem folga de propósito.
	//
	// Meio segundo parecia razoável e reprovou por 0,3 milissegundo numa execução em que
	// o pedido tinha funcionado perfeitamente. Duas coisas legítimas entram nessa conta e
	// nenhuma delas é o compressor ignorando a ordem: ele é assíncrono e segura alguns
	// quadros, e a captura só entrega quadro quando a tela MUDA — tela parada por um
	// instante empurra tudo para a frente.
	//
	// O que decide é a comparação com o intervalo natural, que é de CINCO segundos.
	// Qualquer coisa abaixo de dois só pode ser o pedido; um limite justo demais só
	// produz reprovação que não significa nada.
	if demora > 1500*time.Millisecond {
		t.Errorf("demorou %v para atender — perto demais do intervalo natural para ser o pedido", demora)
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
