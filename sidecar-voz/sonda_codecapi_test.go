package main

import (
	"runtime"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

// SONDA DO ICodecAPI — dá para MANDAR o compressor produzir um quadro-chave agora?
//
// A PERGUNTA POR TRÁS: medido, este compressor espaça os quadros-chave em CINCO
// SEGUNDOS, e não obedece ao atributo `MF_MT_MAX_KEYFRAME_SPACING` (ver
// `TestOCompressorDaQuadroChaveComRegularidade` e a nota em `configurarSaida`). Cinco
// segundos é quanto tempo alguém que entra na sala — ou alguém que perdeu o quadro-chave
// numa oscilação de rede — fica olhando para o vazio.
//
// O caminho padrão para resolver isso em WebRTC é o outro lado PEDIR ("perdi a imagem,
// manda um quadro-chave") e este lado atender. Atender exige uma ordem direta ao
// compressor, que no Windows mora no `ICodecAPI` — outra interface, com outra tabela.
//
// ANTES DE MONTAR NADA, PERGUNTAR. É a mesma lição do cancelador de eco e da busca de
// compressores: GUID copiado de fórum falha em SILÊNCIO, e o que se ganha é uma
// implementação inteira que não faz nada. Aqui os dois GUIDs se autoconferem:
//
//   - se o IID estiver errado, `QueryInterface` devolve E_NOINTERFACE na hora;
//   - se o da propriedade estiver errado, `IsSupported` devolve erro em vez de S_OK.
//
//	go test -run SondaDoCodecAPI -v
func TestSondaDoCodecAPI(t *testing.T) {
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

	lista, err := ProcurarCompressores()
	if err != nil {
		t.Fatalf("procurar compressores: %v", err)
	}
	defer SoltarCompressores(lista)
	if len(lista) == 0 {
		t.Skip("nenhum compressor de H.264 nesta máquina")
	}

	// IID_ICodecAPI {901DB4C7-31CE-41A2-85DC-8FA0BF41B8DA}
	iidCodecAPI := guid(0x901DB4C7, 0x31CE, 0x41A2,
		[8]byte{0x85, 0xDC, 0x8F, 0xA0, 0xBF, 0x41, 0xB8, 0xDA})

	// Os candidatos, com o nome que a documentação da Microsoft lhes dá. O valor padrão
	// e o tipo de cada um estão no comentário para casar com o que a sonda responder.
	candidatos := []struct {
		nome  string
		chave windows.GUID
	}{
		// CODECAPI_AVEncVideoForceKeyFrame {398C1B98-8353-475A-9EF2-8F265D260345} — VT_UI4
		{"ForceKeyFrame", guid(0x398C1B98, 0x8353, 0x475A,
			[8]byte{0x9E, 0xF2, 0x8F, 0x26, 0x5D, 0x26, 0x03, 0x45})},
		// CODECAPI_AVEncMPVGOPSize {95F31BE2-FF9C-4B9A-9F2B-6BFBE8F0D5BA} — VT_UI4
		{"GOPSize", guid(0x95F31BE2, 0xFF9C, 0x4B9A,
			[8]byte{0x9F, 0x2B, 0x6B, 0xFB, 0xE8, 0xF0, 0xD5, 0xBA})},
	}

	const (
		codecSuportado  = 3 // IsSupported
		codecModificavel = 4 // IsModifiable
	)

	for _, cand := range lista {
		t.Run(cand.Nome, func(t *testing.T) {
			transformador, err := cand.Montar()
			if err != nil {
				t.Skipf("não liga: %v", err)
			}
			defer transformador.soltar()

			api, err := transformador.consultar(&iidCodecAPI)
			if err != nil {
				t.Logf("não expõe ICodecAPI: %v", err)
				return
			}
			defer api.soltar()
			t.Log("expõe ICodecAPI")

			for _, c := range candidatos {
				chave := c.chave
				rSup := api.chamar(codecSuportado, uintptr(unsafe.Pointer(&chave)))
				rMod := api.chamar(codecModificavel, uintptr(unsafe.Pointer(&chave)))
				t.Logf("  %-14s suportado=%s  modificavel=%s",
					c.nome, veredito(rSup), veredito(rMod))
			}
		})
	}
}

func veredito(r uintptr) string {
	if uint32(r) == 0 {
		return "SIM"
	}
	return "nao (" + hrTexto(uint32(r)) + ")"
}

func hrTexto(r uint32) string {
	switch r {
	case 0x80004002:
		return "E_NOINTERFACE"
	case 0x80070057:
		return "E_INVALIDARG"
	case 1:
		return "S_FALSE"
	}
	return "0x" + hex32(r)
}

func hex32(v uint32) string {
	const digitos = "0123456789ABCDEF"
	var b [8]byte
	for i := 7; i >= 0; i-- {
		b[i] = digitos[v&0xF]
		v >>= 4
	}
	return string(b[:])
}
