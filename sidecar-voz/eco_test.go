package main

import (
	"os"
	"testing"
	"time"
	"unsafe"
)

func TestVtableDoCancelador(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa do Windows com o DSP)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	obj, err := criar(&clsidCanceladorDeEco, &iidObjetoDeMidia)
	if err != nil {
		t.Fatalf("criar o cancelador como IMediaObject: %v", err)
	}
	defer obj.soltar()

	var entradas, saidas uint32
	r := obj.chamar(moContarFluxos,
		uintptr(unsafe.Pointer(&entradas)),
		uintptr(unsafe.Pointer(&saidas)),
	)
	if err := hr(r, "contar fluxos"); err != nil {
		t.Fatalf("GetStreamCount falhou — o indice 3 nao e GetStreamCount: %v", err)
	}
	t.Logf("o cancelador declara %d entrada(s) e %d saida(s)", entradas, saidas)
	if saidas != 1 {
		t.Fatalf("esperava 1 saida; veio %d. A base da vtable esta deslocada.", saidas)
	}
	if entradas != 0 {
		t.Fatalf("esperava 0 entradas (modo fonte); veio %d — o objeto nao nasceu em modo fonte", entradas)
	}

	loja, err := obj.consultar(&iidLojaDePropriedades)
	if err != nil {
		t.Fatalf("o cancelador nao respondeu por IPropertyStore: %v", err)
	}
	loja.soltar()
}

func TestFormatosQueOCanceladorAceita(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa do Windows com o DSP)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	obj, err := criar(&clsidCanceladorDeEco, &iidObjetoDeMidia)
	if err != nil {
		t.Fatalf("criar o cancelador: %v", err)
	}
	defer obj.soltar()

	const semMaisItens = 0x80040206
	const pegarTipoSaida = 7

	loja, err := obj.consultar(&iidLojaDePropriedades)
	if err != nil {
		t.Fatalf("abrir propriedades: %v", err)
	}
	if err := escreverPropI4(loja, propModoDoSistema, modoSoCancelarEco); err != nil {
		t.Fatalf("modo de sistema: %v", err)
	}

	modoFonte := os.Getenv("ASTRA_ECO_FILTRO") == ""
	if err := escreverPropBool(loja, propModoFonte, modoFonte); err != nil {
		t.Fatalf("modo fonte: %v", err)
	}
	if modoFonte {
		if err := escreverPropI4(loja, propIndices, -1); err != nil {
			t.Fatalf("indices: %v", err)
		}
	}
	loja.soltar()
	t.Logf("configurado: cancelamento simples, modoFonte=%v", modoFonte)

	for i := 0; i < 40; i++ {
		var tipo tipoDeMidia
		r := obj.chamar(pegarTipoSaida, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))
		if uint32(r) == semMaisItens {
			t.Logf("fim da lista em %d", i)
			break
		}
		if hr(r, "pegar tipo de saida") != nil {
			t.Logf("[%d] recusou: 0x%X", i, uint32(r))
			break
		}
		if tipo.formato != 0 && tipo.tamanhoDoFormato >= tamanhoDoFormatoDeOnda {
			onda := (*formatoDeOnda)(unsafe.Pointer(tipo.formato))
			t.Logf("[%2d] %d Hz, %d canal(is), %d bits  (cbFormat=%d)",
				i, onda.Amostras, onda.Canais, onda.BitsPorAmos, tipo.tamanhoDoFormato)
		} else {
			t.Logf("[%2d] sem formato detalhado (cbFormat=%d)", i, tipo.tamanhoDoFormato)
		}
		liberarMemoriaDoCOM(tipo.formato)
	}
}

func TestTaxasQueOCanceladorAceitaDeVerdade(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa do Windows com o DSP)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	obj, err := criar(&clsidCanceladorDeEco, &iidObjetoDeMidia)
	if err != nil {
		t.Fatalf("criar o cancelador: %v", err)
	}
	defer obj.soltar()

	loja, err := obj.consultar(&iidLojaDePropriedades)
	if err != nil {
		t.Fatalf("abrir propriedades: %v", err)
	}
	escreverPropI4(loja, propModoDoSistema, modoSoCancelarEco)
	escreverPropBool(loja, propModoFonte, true)
	escreverPropI4(loja, propIndices, -1)
	loja.soltar()

	const soTestar = 0x00000001

	for _, taxa := range []int{8000, 11025, 16000, 22050, 32000, 44100, 48000} {
		onda := formatoPCM(taxa, 1)
		tipo := tipoDeMidia{
			principal:        tipoAudio,
			subtipo:          subtipoPCM,
			amostraFixa:      1,
			tamanhoAmostra:   2,
			tipoDoFormato:    formatoOnda,
			tamanhoDoFormato: tamanhoDoFormatoDeOnda,
			formato:          uintptr(unsafe.Pointer(&onda)),
		}
		r := obj.chamar(moDefinirTipoSaida, 0, uintptr(unsafe.Pointer(&tipo)), soTestar)
		if r == 0 {
			t.Logf("  %6d Hz  ACEITA", taxa)
		} else {
			t.Logf("  %6d Hz  recusada (0x%X)", taxa, uint32(r))
		}
	}
}

func TestMontarOCancelador(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa do Windows com o DSP)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	c, err := AbrirCapturaComEco("", AjustesDaVoz{Eco: true, Ruido: true, Ganho: true})
	if err != nil {
		t.Fatalf("montar o cancelador: %v", err)
	}
	defer c.Fechar()
	t.Logf("cancelador montado: modo fonte, cancelamento simples, PCM %d Hz mono", c.Taxa())

	porQuadro := c.Taxa() * MilissegundosPorQuadro / 1000
	destino := make([]int16, porQuadro)
	n, _, err := c.Ler(destino)
	if err != nil && err != ErrSemAudio {
		t.Fatalf("leitura deu erro de verdade: %v", err)
	}
	t.Logf("primeira leitura: %d amostra(s), err=%v (producao e conferida noutro teste)", n, err)
}

func TestPortaDeEntradaSempreDevolveFonte(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida — pulando (precisa de placa de som real)")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	comEco, err := AbrirEntradaDeVoz("", AjustesDaVoz{Eco: true, Ruido: true, Ganho: true})
	if err != nil {
		t.Fatalf("com eco pedido, nao devolveu fonte nenhuma: %v", err)
	}
	if _, ok := comEco.(*CapturaComEco); !ok {
		t.Log("AVISO: caiu para a captura crua mesmo com eco pedido — ver o motivo na saida de erro")
	}
	comEco.Fechar()

	semEco, err := AbrirEntradaDeVoz("", AjustesDaVoz{})
	if err != nil {
		t.Fatalf("sem eco pedido, nao devolveu fonte: %v", err)
	}
	if _, ok := semEco.(*Captura); !ok {
		t.Fatal("sem eco pedido, mas veio a captura com cancelador")
	}
	semEco.Fechar()
}

func TestDiagnosticoDoCancelador(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	c, err := AbrirCapturaComEco("", AjustesDaVoz{Eco: true, Ruido: true, Ganho: true})
	if err != nil {
		t.Fatalf("montar: %v", err)
	}
	defer c.Fechar()

	for i := 0; i < 12; i++ {
		c.buffer.Zerar()
		pedido := bufferDeSaida{buffer: c.buffer.Ponteiro()}
		var status uint32
		r := c.objeto.chamar(moProcessarSaida,
			0, 1,
			uintptr(unsafe.Pointer(&pedido)),
			uintptr(unsafe.Pointer(&status)),
		)
		t.Logf("[%2d] hr=0x%08X  bytes=%d  statusDoBuffer=0x%08X  statusGeral=0x%08X",
			i, uint32(r), c.buffer.Usado(), pedido.status, status)
		time.Sleep(60 * time.Millisecond)
	}
}

func TestCanceladorComSaidaAtiva(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	alto, err := AbrirSaida("")
	if err != nil {
		t.Fatalf("abrir alto-falante: %v", err)
	}
	defer alto.Fechar()

	c, err := AbrirCapturaComEco("", AjustesDaVoz{Eco: true, Ruido: true, Ganho: true})
	if err != nil {
		t.Fatalf("montar o cancelador: %v", err)
	}
	defer c.Fechar()

	porQuadro := c.Taxa() * MilissegundosPorQuadro / 1000
	destino := make([]int16, porQuadro)
	total := 0
	limite := time.Now().Add(1500 * time.Millisecond)
	for time.Now().Before(limite) {

		alto.Escrever(nil)

		n, _, err := c.Ler(destino)
		if err != nil && err != ErrSemAudio {
			t.Fatalf("leitura deu erro: %v", err)
		}
		total += n
		if err == ErrSemAudio {
			time.Sleep(10 * time.Millisecond)
		}
	}
	t.Logf("com a saida ativa: %d amostras (%.2fs de audio)",
		total, float64(total)/float64(c.Taxa()))
	if total == 0 {
		t.Fatal("continua sem produzir — a hipotese da saida ativa esta descartada")
	}
}
