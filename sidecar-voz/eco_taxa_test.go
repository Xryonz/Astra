package main

import (
	"os"
	"testing"
	"unsafe"
)

func TestQuaisTaxasOCanceladorAceita(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_ECO") == "" {
		t.Skip("ASTRA_SONDA_ECO nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	taxas := []int{8000, 11025, 16000, 22050, 24000, 32000, 44100, 48000}

	for _, taxa := range taxas {
		obj, err := criar(&clsidCanceladorDeEco, &iidObjetoDeMidia)
		if err != nil {
			t.Fatalf("criar o cancelador: %v", err)
		}

		c := &CapturaComEco{objeto: obj}
		if err := c.configurar("", AjustesDaVoz{Eco: true, Ruido: true, Ganho: true}); err != nil {
			t.Logf("%6d Hz: nao deu nem para configurar (%v)", taxa, err)
			c.Fechar()
			continue
		}

		onda := formatoPCM(taxa, CanaisDeVoz)
		tipo := tipoDeMidia{
			principal:        tipoAudio,
			subtipo:          subtipoPCM,
			amostraFixa:      1,
			tamanhoAmostra:   uint32(CanaisDeVoz * 2),
			tipoDoFormato:    formatoOnda,
			tamanhoDoFormato: tamanhoDoFormatoDeOnda,
			formato:          uintptr(unsafe.Pointer(&onda)),
		}

		aceita := hr(c.objeto.chamar(moDefinirTipoSaida, 0, uintptr(unsafe.Pointer(&tipo)), 0),
			"definir formato") == nil

		prontos := false
		if aceita {
			prontos = hr(c.objeto.chamar(moAlocarRecursos), "preparar") == nil
		}

		switch {
		case aceita && prontos:
			t.Logf("%6d Hz: ACEITA e aloca recursos", taxa)
		case aceita:
			t.Logf("%6d Hz: aceita o formato mas NAO aloca recursos", taxa)
		default:
			t.Logf("%6d Hz: recusa", taxa)
		}
		c.Fechar()
	}

	t.Logf("a captura sem eco roda a %d Hz; o cancelador hoje esta fixo em %d Hz",
		TaxaDeAmostragem, taxaDoCancelador)
}
