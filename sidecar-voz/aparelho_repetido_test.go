package main

import "testing"

func TestTrocarParaOMesmoAparelhoNaoReabreOMicrofone(t *testing.T) {
	m := NovoMotor(nil, NovoMisturador(), nil, "")

	inicio := m.geracaoEntrada.Load()

	m.DefinirAparelho(sentidoEntrada, "microfone-a")
	depoisDaTroca := m.geracaoEntrada.Load()
	if depoisDaTroca == inicio {
		t.Fatal("trocar de aparelho de verdade tem que pedir a reabertura")
	}

	m.DefinirAparelho(sentidoEntrada, "microfone-a")
	m.DefinirAparelho(sentidoEntrada, "microfone-a")
	if m.geracaoEntrada.Load() != depoisDaTroca {
		t.Error("repetir o mesmo aparelho reabriu o microfone a toa;" +
			" a reabertura espera ate 3s pelo alto-falante para rearmar o cancelador de eco")
	}

	m.DefinirAparelho(sentidoEntrada, "microfone-b")
	if m.geracaoEntrada.Load() == depoisDaTroca {
		t.Error("depois de repetir, uma troca real parou de ser notada")
	}
}

func TestOMesmoValeParaASaida(t *testing.T) {
	m := NovoMotor(nil, NovoMisturador(), nil, "")

	m.DefinirAparelho(sentidoSaida, "caixa-a")
	marco := m.geracaoSaida.Load()

	m.DefinirAparelho(sentidoSaida, "caixa-a")
	if m.geracaoSaida.Load() != marco {
		t.Error("repetir a mesma saida reabriu o alto-falante a toa")
	}
}
