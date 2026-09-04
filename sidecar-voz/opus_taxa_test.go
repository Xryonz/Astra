package main

import (
	"os"
	"testing"
)

func TestQuaisTaxasOOpusAceita(t *testing.T) {
	if os.Getenv("ASTRA_SONDA_ECO") == "" {
		t.Skip("ASTRA_SONDA_ECO nao definida — sonda de investigacao, nao roda no dia a dia")
	}
	abrirParaTeste(t)

	for _, taxa := range []int{8000, 11025, 12000, 16000, 22050, 24000, 44100, 48000} {
		cod, err := NovoCodificador(taxa, CanaisDeVoz)
		if err != nil {
			t.Logf("%6d Hz: recusado pelo opus (%v)", taxa, err)
			continue
		}
		cod.Fechar()
		t.Logf("%6d Hz: aceito", taxa)
	}
}
