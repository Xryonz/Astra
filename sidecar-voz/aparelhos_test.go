package main

import (
	"os"
	"testing"
)

// Enumeracao de aparelhos contra o Windows de verdade.
//
// Pede variavel de ambiente porque depende da maquina ter placa de som — no CI nao
// tem, e um teste que falha por ausencia de hardware ensina a ignorar teste.
//
//	ASTRA_TESTE_AUDIO=1 go test -run Aparelhos -v
func TestListarAparelhos(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa de placa de som real)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	for _, caso := range []struct {
		nome    string
		sentido int
	}{{"entrada (microfones)", sentidoEntrada}, {"saida (alto-falantes)", sentidoSaida}} {
		lista, err := ListarAparelhos(caso.sentido)
		if err != nil {
			t.Fatalf("%s: %v", caso.nome, err)
		}
		if len(lista) == 0 {
			t.Fatalf("%s: nenhum aparelho ativo — improvavel numa maquina com som", caso.nome)
		}
		t.Logf("%s: %d encontrado(s)", caso.nome, len(lista))
		for _, a := range lista {
			if a.ID == "" {
				t.Error("aparelho sem identificador entrou na lista")
			}
			// Nome igual ao id significa que a leitura do PROPVARIANT falhou e caiu
			// no recurso. Compila e nao quebra nada — so mostra um nome ilegivel no
			// menu, que e exatamente o tipo de defeito que passa despercebido.
			if a.Nome == a.ID {
				t.Errorf("nome nao veio do PROPVARIANT (caiu no id): %s", a.ID)
			}
			t.Logf("   %-45s  id=%.28s...", a.Nome, a.ID)
		}
	}
}

// O ID GUARDADO PODE APONTAR PARA UM APARELHO QUE NAO EXISTE MAIS.
//
// E o caso comum, nao a exceção: headset USB tirado da porta, placa desabilitada,
// perfil levado para outro computador. Sem a queda para o padrao, a call abriria
// muda por causa de um fone desplugado semana passada — e nada na tela diria isso.
//
// Este teste existe porque a queda e um caminho que NUNCA roda no uso normal: so
// aparece no dia em que alguem desplugou algo, que e exatamente o dia em que
// ninguem quer descobrir que ela nao funciona.
func TestAparelhoInvalidoCaiNoPadrao(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa de placa de som real)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	mic, err := AbrirCaptura("{este-aparelho-nao-existe}")
	if err != nil {
		t.Fatalf("devia ter caido no microfone padrao, mas falhou: %v", err)
	}
	mic.Fechar()

	alto, err := AbrirSaida("{este-tambem-nao}")
	if err != nil {
		t.Fatalf("devia ter caido na saida padrao, mas falhou: %v", err)
	}
	alto.Fechar()
}

// Trocar de aparelho tem de FAZER O LACO SAIR — e o unico jeito de ele fechar o
// aparelho velho e abrir o novo. Contador em vez de bandeira porque duas trocas
// rapidas seguidas perderiam a segunda.
func TestTrocaDeAparelhoAvancaAGeracao(t *testing.T) {
	m := &Motor{}

	if m.idEntrada() != "" || m.idSaida() != "" {
		t.Fatal("sem escolha nenhuma, os dois sentidos deviam comecar vazios (= padrao do Windows)")
	}

	antes := m.geracaoEntrada.Load()
	m.DefinirAparelho(sentidoEntrada, "mic-a")
	if m.geracaoEntrada.Load() == antes {
		t.Fatal("trocar o microfone nao avancou a geracao; o laco nunca saberia")
	}
	if m.idEntrada() != "mic-a" {
		t.Fatalf("id de entrada ficou %q", m.idEntrada())
	}
	// A troca de um sentido nao pode mexer no outro: fechar o alto-falante porque a
	// pessoa trocou o microfone seria um corte de som sem motivo.
	if m.geracaoSaida.Load() != 0 || m.idSaida() != "" {
		t.Fatal("trocar a entrada mexeu na saida")
	}

	m.DefinirAparelho(sentidoEntrada, "mic-b")
	if m.geracaoEntrada.Load() != antes+2 {
		t.Fatal("duas trocas seguidas tinham de contar duas; com bandeira booleana a segunda se perderia")
	}
}
