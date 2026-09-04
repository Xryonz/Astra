package main

import (
	"os"
	"testing"
	"unsafe"
)

var chaveFormaDoAparelho = chaveDePropriedade{
	conjunto: guid(0x1DA5D803, 0xD492, 0x4EDD,
		[8]byte{0x8C, 0x23, 0xE0, 0xC0, 0xFF, 0xEE, 0x7F, 0x0E}),
	id: 0,
}

var nomeDaForma = map[uint32]string{
	0: "aparelho de rede", 1: "caixas de som", 2: "linha", 3: "FONE",
	4: "microfone", 5: "HEADSET", 6: "telefone", 7: "passagem digital",
	8: "SPDIF", 9: "audio da tela", 10: "desconhecido",
}

func TestFormaDosAparelhosDeSaida(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_ECO") == "" {
		t.Skip("ASTRA_SONDA_ECO nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	for _, s := range []struct {
		sentido int
		rotulo  string
	}{{0, "saida"}, {1, "entrada"}} {
		enumerador, err := criar(&clsidEnumeradorDeDispositivos, &iidEnumeradorDeDispositivos)
		if err != nil {
			t.Fatalf("enumerador: %v", err)
		}

		var colecao objeto
		if hr(enumerador.chamar(mmEnumAudioEndpoints, uintptr(s.sentido),
			uintptr(apenasAtivos), uintptr(unsafe.Pointer(&colecao))), "listar") != nil {
			enumerador.soltar()
			continue
		}

		var quantos uint32
		colecao.chamar(colContar, uintptr(unsafe.Pointer(&quantos)))
		t.Logf("--- %s: %d aparelho(s) ---", s.rotulo, quantos)

		for i := uint32(0); i < quantos; i++ {
			var dispositivo objeto
			if hr(colecao.chamar(colItem, uintptr(i), uintptr(unsafe.Pointer(&dispositivo))), "item") != nil {
				continue
			}
			_, nome := descreverAparelho(dispositivo)

			var loja objeto
			if hr(dispositivo.chamar(mmDeviceAbrirLoja, 0, uintptr(unsafe.Pointer(&loja))), "loja") == nil {
				var v propvariant
				if hr(loja.chamar(lojaLer, uintptr(unsafe.Pointer(&chaveFormaDoAparelho)),
					uintptr(unsafe.Pointer(&v))), "forma") == nil {
					forma := uint32(v.ponteiro)
					t.Logf("  %-45s forma=%d (%s)", nome, forma, nomeDaForma[forma])
				} else {
					t.Logf("  %-45s forma: NAO PUBLICADA", nome)
				}
				loja.soltar()
			}
			dispositivo.soltar()
		}
		colecao.soltar()
		enumerador.soltar()
	}
}
