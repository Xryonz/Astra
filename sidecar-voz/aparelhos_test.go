package main

import (
	"os"
	"testing"
)

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

			if a.Nome == a.ID {
				t.Errorf("nome nao veio do PROPVARIANT (caiu no id): %s", a.ID)
			}
			t.Logf("   %-45s  id=%.28s...", a.Nome, a.ID)
		}
	}
}

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

	if m.geracaoSaida.Load() != 0 || m.idSaida() != "" {
		t.Fatal("trocar a entrada mexeu na saida")
	}

	m.DefinirAparelho(sentidoEntrada, "mic-b")
	if m.geracaoEntrada.Load() != antes+2 {
		t.Fatal("duas trocas seguidas tinham de contar duas; com bandeira booleana a segunda se perderia")
	}
}
