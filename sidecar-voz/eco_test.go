package main

import (
	"os"
	"testing"
	"time"
	"unsafe"
)

// A VALIDAÇÃO DA VTABLE VEM ANTES DE TUDO.
//
// Índice errado numa tabela de funções não devolve erro: salta para a função
// vizinha, com os argumentos errados, e derruba o processo — num lugar que não tem
// nada a ver com a causa. É o defeito mais caro de caçar que existe nesta camada.
//
// `GetStreamCount` é o teste perfeito da BASE da tabela: não recebe nada além dos
// dois destinos, e a resposta é conhecida. Se o índice 3 estiver certo, todos os
// outros contados a partir dele também estão — vêm da mesma lista de declaração.
//
// A RESPOSTA CERTA É ZERO ENTRADAS E UMA SAÍDA, e essa foi uma lição: a primeira
// versão deste teste exigia uma e uma, e falhou. Não por causa da tabela — por causa
// da expectativa. No modo FONTE o cancelador não tem entrada nenhuma, porque ele
// mesmo puxa o áudio dos aparelhos; quem tem uma entrada é o modo filtro.
//
// Ou seja, o número que parecia errado era a confirmação de que o modo fonte é
// mesmo o padrão do objeto, como a documentação diz.
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

	// O MESMO objeto tem de responder pelas DUAS interfaces: a configuracao entra
	// por IPropertyStore e o audio sai por IMediaObject.
	loja, err := obj.consultar(&iidLojaDePropriedades)
	if err != nil {
		t.Fatalf("o cancelador nao respondeu por IPropertyStore: %v", err)
	}
	loja.soltar()
}

// PERGUNTA AO CANCELADOR QUAIS FORMATOS ELE ACEITA.
//
// `SetOutputType` recusando com "excecao nao esperada" nao diz o que esta errado —
// pode ser taxa, canais, profundidade, ou o struct inteiro. Adivinhar qual seria
// tentar combinacoes as cegas.
//
// `GetOutputType` enumera. O objeto responde exatamente o que aceita, e ai nao ha o
// que adivinhar.
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

	const semMaisItens = 0x80040206 // DMO_E_NO_MORE_ITEMS
	const pegarTipoSaida = 7

	// A LISTA PODE DEPENDER DA CONFIGURACAO. Configurar antes de perguntar e a
	// diferenca entre "o que ele aceita de fabrica" e "o que ele aceita do jeito que
	// vamos usa-lo" — e e a segunda que importa.
	loja, err := obj.consultar(&iidLojaDePropriedades)
	if err != nil {
		t.Fatalf("abrir propriedades: %v", err)
	}
	if err := escreverPropI4(loja, propModoDoSistema, modoSoCancelarEco); err != nil {
		t.Fatalf("modo de sistema: %v", err)
	}
	// O MODO VEM DA VARIAVEL DE AMBIENTE para dar pra comparar os dois sem editar
	// codigo. O limite de taxa pode ser do modo FONTE e nao do cancelador, e a
	// diferenca decide se da pra usar isto sem estragar a voz:
	//
	//	ASTRA_ECO_FILTRO=1  ->  modo filtro (nos alimentamos os dois fluxos)
	//	sem a variavel      ->  modo fonte  (ele puxa dos aparelhos sozinho)
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

// ENUMERAR E UMA DICA; SETOUTPUTTYPE E A VERDADE.
//
// Muitos DMOs listam so o formato PREFERIDO em GetOutputType e aceitam outros
// perfeitamente. Concluir "so aceita 8 kHz" a partir da lista seria decidir a
// arquitetura da voz do Astra em cima de uma inferencia.
//
// O SetOutputType tem uma bandeira de TESTE que pergunta sem comprometer nada. Este
// teste percorre as taxas que interessam e diz, uma por uma, quais passam.
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

	// DMO_SET_TYPEF_TEST_ONLY: "voce aceitaria isto?" sem de fato mudar nada.
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

// Monta o cancelador inteiro, do jeito que o motor monta, e conferе que ele ACEITA a
// configuracao e o formato.
//
// Este e o teste que separa "compila" de "funciona": cada passo aqui e um HRESULT
// que o Windows pode recusar, e a recusa e sempre por um motivo que so aparece
// tentando — modo incompativel com o formato, propriedade na ordem errada, aparelho
// que nao aceita.
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

	// ESTE TESTE NAO MEDE PRODUCAO, de proposito.
	//
	// Producao depende de haver um fluxo de SAIDA ativo — descoberta deste dia, e a
	// razao de existir `TestCanceladorComSaidaAtiva`. Medir producao aqui, sem abrir
	// alto-falante, daria um resultado que depende de qual teste rodou antes: se
	// outro deixou uma saida aberta, este passa; sozinho, falha. Teste que muda de
	// resultado conforme a ordem e pior que teste nenhum, porque ensina a ignorar
	// falha.
	//
	// O que se prova aqui: cada passo do MONTAGEM foi aceito pelo Windows — a
	// configuracao, o formato, a alocacao de recursos. E que pedir audio devolve
	// ErrSemAudio, e nao erro de verdade.
	porQuadro := c.Taxa() * MilissegundosPorQuadro / 1000
	destino := make([]int16, porQuadro)
	n, _, err := c.Ler(destino)
	if err != nil && err != ErrSemAudio {
		t.Fatalf("leitura deu erro de verdade: %v", err)
	}
	t.Logf("primeira leitura: %d amostra(s), err=%v (producao e conferida noutro teste)", n, err)
}

// A porta unica do motor tem de devolver SEMPRE uma fonte utilizavel: com o
// cancelador quando da, sem ele quando nao da. Nunca nada.
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

// DIAGNOSTICO CRU do ProcessOutput: imprime o HRESULT e as bandeiras a cada volta.
//
// "Montou e nao produz" tem varias causas possiveis e nenhuma delas aparece no
// caminho normal, que so distingue "veio audio" de "nao veio". Aqui olhamos o que o
// Windows realmente responde.
//
//	ASTRA_TESTE_AUDIO=1 go test -run DiagnosticoDoCancelador -v
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

// O CANCELADOR PRECISA DE UM ALTO-FALANTE ATIVO?
//
// Hipotese: em modo fonte ele cancela eco comparando o microfone com o que ESTA
// SAINDO na saida. Sem nenhum fluxo de saida aberto, ele pode ficar esperando a
// referencia e nunca produzir — que e exatamente o sintoma (S_FALSE constante).
//
// No app de verdade o laco de saida escreve silencio o tempo todo, entao o fluxo
// existe sempre. Este teste reproduz essa condicao.
func TestCanceladorComSaidaAtiva(t *testing.T) {
	if os.Getenv("ASTRA_TESTE_AUDIO") == "" {
		t.Skip("ASTRA_TESTE_AUDIO nao definida")
	}
	defer PrenderNaThread()()
	if err := abrirCOM(); err != nil {
		t.Fatalf("iniciar COM: %v", err)
	}
	defer fecharCOM()

	// Abre a saida ANTES do cancelador e mantem silencio correndo nela.
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
		// Alimenta a saida com silencio, como o laco real faz.
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
