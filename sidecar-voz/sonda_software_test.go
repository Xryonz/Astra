package main

// SONDA DO CAMINHO DE SOFTWARE — a máquina sem compressor de placa transmite?
//
// HOJE ELA NÃO TRANSMITE NADA. Não é "transmite pior": `AbrirCompressor` percorre os
// candidatos, todos recusam, e a transmissão nem começa. Medido nesta máquina:
//
//	NVIDIA H.264 Encoder MFT      nem liga: falha catastrófica
//	Intel Quick Sync (x2)         fala D3D11        <- é por aqui que passa hoje
//	Microsoft AVC DX12            fala D3D11
//	H264 Encoder MFT (software)   NÃO fala D3D11    <- e por isso é recusado
//
// Numa máquina virtual, num notebook velho ou numa área de trabalho remota só existe o
// último — e ele é recusado duas vezes: `definirEntrada` só aceita ARGB32 (ele só
// aceita YUV) e `medirASaida` exige que ele traga a própria amostra (ele não traz).
//
// O PLANO ÓBVIO É CONVERTER, e a peça já está no caminho: o Video Processor MFT
// (`redimensionador.go`) roda na placa e converte formato além de reduzir tamanho. Mas
// entre "ele converte para NV12" e "a transmissão funciona" há uma pergunta que decide
// a arquitetura inteira e que NÃO se responde lendo documentação:
//
//	o compressor de software vive na memória PRINCIPAL. O quadro está na PLACA.
//	quem faz a travessia, e quanto ela custa?
//
// São três perguntas encadeadas, e esta sonda faz as três em ordem. Cada uma só é feita
// se a anterior respondeu sim, e a que falhar aponta o remédio:
//
//	1. o Video Processor aceita ARGB32 na entrada e NV12 na saída, ao mesmo tempo
//	   que reduz o tamanho?
//	2. o quadro NV12 que ele produz pode ser LIDO pela CPU? (se ele alocar em memória
//	   de vídeo, ler exige uma textura de leitura montada por nós — mais código)
//	3. o compressor de software aceita esses bytes e devolve H.264 de verdade?
//
// E depois das três, a pergunta que decide o PRESET: quanto custa por quadro. Um
// compressor de software a 720p pode custar mais que o orçamento de 16,67ms de 60/s, e
// nesse caso prometer 60 é prometer engasgo.
//
//	ASTRA_TESTE_TELA=1 go test -run SondaDoCaminhoDeSoftware -v

import (
	"runtime"
	"testing"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

func TestSondaDoCaminhoDeSoftware(t *testing.T) {
	precisaDeTela(t)

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

	// ELA PULA NA SUÍTE INTEIRA E PASSA SOZINHA, e isso é conhecido. Depois de uns trinta
	// segundos de testes de placa no MESMO processo, o `D3D11CreateDevice` passa a
	// devolver um dispositivo que não responde `QueryInterface` para DXGI — sucesso sem
	// interface. É esgotamento do processo de teste, não do aplicativo: em produção existe
	// UM dispositivo por transmissão, criado e fechado pelo `Emissor`.
	//
	// Até esta sonda existir, esse mesmo estado DERRUBAVA o processo: o zero devolvido
	// virava chamada de método em endereço nulo. Hoje vira a mensagem abaixo, graças à
	// guarda em `consultar`. Rodar sozinha para ver os números:
	//
	//	ASTRA_TESTE_TELA=1 go test -run SondaDoCaminhoDeSoftware -v
	tela, err := AbrirTela(0)
	if err != nil {
		t.Skipf("sem tela para capturar: %v", err)
	}
	defer tela.Fechar()
	largura, altura := tela.Tamanho()
	const saidaL, saidaA = 1280, 720
	t.Logf("tela %dx%d, alvo %dx%d", largura, altura, saidaL, saidaA)

	// O gerenciador da MESMA placa da captura — sem isso o Video Processor não sabe ler
	// a textura que a duplicação produziu.
	var ficha uint32
	var gerente objeto
	r, _, _ := procMFCriarGerenciador.Call(
		uintptr(unsafe.Pointer(&ficha)), uintptr(unsafe.Pointer(&gerente)))
	if err := hr(r, "criar o gerenciador"); err != nil {
		t.Fatal(err)
	}
	defer gerente.soltar()
	if err := hr(gerente.chamar(gerTrocarDispositivo, uintptr(tela.dispositivo), uintptr(ficha)),
		"entregar a placa"); err != nil {
		t.Fatal(err)
	}

	// ---- PERGUNTA 1: ARGB32 entra, NV12 sai, e menor? -----------------------------

	vp, err := criar(&clsidVideoProcessor, &iidIMFTransform)
	if err != nil {
		t.Fatalf("o Video Processor MFT não existe: %v", err)
	}
	defer vp.soltar()

	if err := hr(vp.chamar(transMandarRecado, recadoDefinirD3D, uintptr(gerente)),
		"dizer a placa ao Video Processor"); err != nil {
		t.Fatalf("PERGUNTA 1 falhou na placa: %v", err)
	}
	if err := ladoDoProcessador(vp, transDefinirEntrada, formatoARGB32, largura, altura); err != nil {
		t.Fatalf("PERGUNTA 1 falhou na entrada: %v", err)
	}
	if err := ladoDoProcessador(vp, transDefinirSaida, formatoNV12, saidaL, saidaA); err != nil {
		t.Fatalf("PERGUNTA 1: ele NÃO converte para NV12 reduzindo: %v", err)
	}
	t.Logf("PERGUNTA 1: SIM — ARGB32 %dx%d entra, NV12 %dx%d sai", largura, altura, saidaL, saidaA)

	var infoVP infoDaSaida
	if err := hr(vp.chamar(transInfoDaSaida, 0, uintptr(unsafe.Pointer(&infoVP))),
		"como o Video Processor entrega"); err != nil {
		t.Fatal(err)
	}
	trazAmostra := infoVP.Bandeiras&compressorTrazAmostra != 0
	t.Logf("  ele traz a própria amostra: %v (bandeiras 0x%X, tamanho %d)",
		trazAmostra, infoVP.Bandeiras, infoVP.Tamanho)

	vp.chamar(transMandarRecado, recadoComecarFluxo, 0)
	vp.chamar(transMandarRecado, recadoAbrirFluxo, 0)

	// ---- PERGUNTA 2: dá para LER esse NV12 na CPU? --------------------------------

	// Uma textura nossa, para onde a duplicação é copiada. Mesmo arranjo do anel do
	// compressor: a textura da duplicação não serve de entrada direta para um MFT.
	desc := descricaoDeTextura{
		Largura: uint32(largura), Altura: uint32(altura),
		Niveis: 1, Camadas: 1, Formato: formatoBGRA, AmostrasConta: 1,
		Uso: usoPadrao, Amarracao: amarrarComoAlvo | amarrarComoTextura,
	}
	nosso, err := (&Compressor{}).embrulharUmQuadro(tela.dispositivo, desc)
	if err != nil {
		t.Fatalf("montar a textura de trabalho: %v", err)
	}
	defer nosso.soltar()

	textura, err := pegarUmQuadroDeVerdade(t, tela)
	if err != nil {
		t.Skipf("a tela não produziu quadro: %v", err)
	}
	tela.contexto.chamar(d3dCopiarTudo, uintptr(nosso.textura), uintptr(textura))
	tela.SoltarQuadro()
	nosso.amostra.chamar(amostraDefinirTempo, 0)
	nosso.amostra.chamar(amostraDefinirDuracao, uintptr(10_000_000/60))

	nv12, passo, err := passarPeloProcessador(vp, nosso.amostra, trazAmostra, infoVP.Tamanho)
	if err != nil {
		t.Fatalf("PERGUNTA 2: não dá para ler o NV12 na CPU: %v", err)
	}
	esperado := saidaL * saidaA * 3 / 2
	t.Logf("PERGUNTA 2: SIM — %d bytes lidos (NV12 %dx%d pede %d), passo %d",
		len(nv12), saidaL, saidaA, esperado, passo)
	if len(nv12) < esperado {
		t.Fatalf("veio quadro curto: %d < %d", len(nv12), esperado)
	}

	// ---- PERGUNTA 3: o compressor de software aceita isso? ------------------------

	sw, nome, err := abrirCompressorDeSoftware(t, saidaL, saidaA, passo)
	if err != nil {
		t.Fatalf("PERGUNTA 3: %v", err)
	}
	defer sw.soltar()
	t.Logf("PERGUNTA 3: o candidato de software é %q", nome)

	var infoSW infoDaSaida
	if err := hr(sw.chamar(transInfoDaSaida, 0, uintptr(unsafe.Pointer(&infoSW))),
		"como o compressor de software entrega"); err != nil {
		t.Fatal(err)
	}
	t.Logf("  ele traz a própria amostra: %v (bandeiras 0x%X, precisa de %d bytes)",
		infoSW.Bandeiras&compressorTrazAmostra != 0, infoSW.Bandeiras, infoSW.Tamanho)

	sw.chamar(transMandarRecado, recadoComecarFluxo, 0)
	sw.chamar(transMandarRecado, recadoAbrirFluxo, 0)

	entrada, bufEntrada, err := amostraDeMemoria(len(nv12))
	if err != nil {
		t.Fatalf("reservar a entrada do compressor: %v", err)
	}
	defer entrada.soltar()
	defer bufEntrada.soltar()

	tamSaida := int(infoSW.Tamanho)
	if tamSaida <= 0 {
		tamSaida = esperado
	}
	saidaAmostra, bufSaida, err := amostraDeMemoria(tamSaida)
	if err != nil {
		t.Fatalf("reservar a saída do compressor: %v", err)
	}
	defer saidaAmostra.soltar()
	defer bufSaida.soltar()

	// ---- E A CONTA QUE DECIDE O PRESET --------------------------------------------
	//
	// Vinte quadros da tela de verdade, do jeito que a transmissão faria: copia,
	// converte na placa, lê para a memória principal, comprime na CPU. Os dois tempos
	// separados porque os remédios são opostos — travessia cara pede quadro menor,
	// compressão cara pede menos quadros por segundo.

	// CONTA AS RODADAS QUE DE FATO ACONTECERAM, e não as pedidas. A área de trabalho
	// parada não produz quadro: dividir pelo número pedido quando metade foi pulada
	// infla o custo por quadro sem nada no relatório dizendo isso. Já custou uma
	// conclusão errada neste projeto — ver o `sair` que juntava dois quadros num.
	const quantos = 30
	var gastoCopia, gastoConversao, gastoLeitura, gastoCompressao time.Duration
	rodadas, bytesH264, quadrosH264 := 0, 0, 0

	for i := 0; i < quantos; i++ {
		tex, err := pegarUmQuadroDeVerdade(t, tela)
		if err != nil {
			continue
		}
		rodadas++
		quando := time.Duration(i) * time.Second / 60

		marco := time.Now()
		tela.contexto.chamar(d3dCopiarTudo, uintptr(nosso.textura), uintptr(tex))
		tela.SoltarQuadro()
		gastoCopia += time.Since(marco)
		nosso.amostra.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))

		bytes, _, conv, leit, err := passarPeloProcessadorMedido(vp, nosso.amostra, trazAmostra, infoVP.Tamanho)
		gastoConversao += conv
		gastoLeitura += leit
		if err != nil {
			t.Fatalf("travessia no quadro %d: %v", i, err)
		}
		if len(bytes) == 0 {
			continue // o Video Processor ainda não fechou quadro; não é erro
		}

		marco = time.Now()
		n, err := comprimirUmaVez(sw, entrada, bufEntrada, saidaAmostra, bufSaida, bytes, quando)
		gastoCompressao += time.Since(marco)
		if err != nil {
			t.Fatalf("comprimir o quadro %d: %v", i, err)
		}
		if n > 0 {
			bytesH264 += n
			quadrosH264++
		}
	}

	if rodadas == 0 {
		t.Skip("a área de trabalho não mudou; sem quadro para medir")
	}
	if quadrosH264 == 0 {
		t.Fatal("PERGUNTA 3: o compressor de software não devolveu H.264 nenhum")
	}
	t.Logf("PERGUNTA 3: SIM — %d rodadas, %d renderam H.264, %d bytes",
		rodadas, quadrosH264, bytesH264)

	n := time.Duration(rodadas)
	copia, conversao, leitura := gastoCopia/n, gastoConversao/n, gastoLeitura/n
	compressao := gastoCompressao / n
	total := copia + conversao + leitura + compressao
	t.Logf("")
	t.Logf("CUSTO POR QUADRO a %dx%d, tudo em software:", saidaL, saidaA)
	t.Logf("  copiar na placa                              %8.2fms", float64(copia.Microseconds())/1000)
	t.Logf("  converter+reduzir (Video Processor)          %8.2fms", float64(conversao.Microseconds())/1000)
	t.Logf("  LER da placa para a memória                  %8.2fms", float64(leitura.Microseconds())/1000)
	t.Logf("  comprimir H.264 na CPU                       %8.2fms", float64(compressao.Microseconds())/1000)
	t.Logf("  TOTAL                                        %8.2fms", float64(total.Microseconds())/1000)
	t.Logf("")
	orcamentos(t, total)

	// ---- E A PERGUNTA QUE A CONTA ACIMA LEVANTA -----------------------------------
	//
	// A leitura é 73% do custo, e ela não é CÓPIA: 1,38 MB de memória para memória
	// custam meio milissegundo, não sete. Os sete são a CPU PARADA esperando a placa
	// terminar de converter — destrancar um buffer que embrulha textura força a
	// sincronização.
	//
	// É a MESMA FORMA do teto que segurava o caminho de hardware em 45 quadros: uma
	// espera que transforma LATÊNCIA em VAZÃO por se recusar a seguir sem o resultado.
	// E o remédio é o mesmo: entregar o quadro de agora, ler o de antes. A placa ganha
	// uma volta inteira para terminar, e quando a leitura chega o dado já está lá.
	//
	// Antes de escrever isso em produção, medir se funciona.

	t.Logf("")
	t.Logf("--- O MESMO CANO, LENDO O QUADRO ANTERIOR ---")

	var pipeCopia, pipeConversao, pipeLeitura, pipeCompressao time.Duration
	pipeRodadas, pipeQuadros := 0, 0
	var pendente objeto // a amostra da volta passada, ainda não lida

	for i := 0; i < quantos; i++ {
		tex, err := pegarUmQuadroDeVerdade(t, tela)
		if err != nil {
			continue
		}
		pipeRodadas++
		quando := time.Duration(i) * time.Second / 60

		marco := time.Now()
		tela.contexto.chamar(d3dCopiarTudo, uintptr(nosso.textura), uintptr(tex))
		tela.SoltarQuadro()
		pipeCopia += time.Since(marco)
		nosso.amostra.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))

		// Entrega o de agora. A placa começa a trabalhar e nós NÃO esperamos.
		marco = time.Now()
		nova, err := converter(vp, nosso.amostra, trazAmostra, infoVP.Tamanho)
		pipeConversao += time.Since(marco)
		if err != nil {
			t.Fatalf("conversão no quadro %d: %v", i, err)
		}

		// Lê o de antes, que teve uma volta inteira para ficar pronto.
		if pendente != 0 {
			marco = time.Now()
			bytes, _, err := lerAmostra(pendente)
			pipeLeitura += time.Since(marco)
			pendente.soltar()
			pendente = 0
			if err != nil {
				t.Fatalf("leitura atrasada no quadro %d: %v", i, err)
			}
			if len(bytes) > 0 {
				marco = time.Now()
				n, err := comprimirUmaVez(sw, entrada, bufEntrada, saidaAmostra, bufSaida, bytes, quando)
				pipeCompressao += time.Since(marco)
				if err != nil {
					t.Fatalf("comprimir o quadro %d: %v", i, err)
				}
				if n > 0 {
					pipeQuadros++
				}
			}
		}
		pendente = nova
	}
	if pendente != 0 {
		pendente.soltar()
	}

	if pipeRodadas < 2 {
		t.Skip("rodadas de menos para comparar o pipeline")
	}
	m := time.Duration(pipeRodadas)
	pTotal := pipeCopia/m + pipeConversao/m + pipeLeitura/m + pipeCompressao/m
	t.Logf("  %d rodadas, %d renderam H.264", pipeRodadas, pipeQuadros)
	t.Logf("  copiar na placa                              %8.2fms", float64((pipeCopia / m).Microseconds())/1000)
	t.Logf("  entregar ao Video Processor (sem esperar)    %8.2fms", float64((pipeConversao / m).Microseconds())/1000)
	t.Logf("  LER o quadro ANTERIOR                        %8.2fms", float64((pipeLeitura / m).Microseconds())/1000)
	t.Logf("  comprimir H.264 na CPU                       %8.2fms", float64((pipeCompressao / m).Microseconds())/1000)
	t.Logf("  TOTAL                                        %8.2fms", float64(pTotal.Microseconds())/1000)
	t.Logf("")
	orcamentos(t, pTotal)
	t.Logf("")
	t.Logf("VEREDITO: %.2fms -> %.2fms por quadro (%.0f%% a menos)",
		float64(total.Microseconds())/1000, float64(pTotal.Microseconds())/1000,
		100*(1-float64(pTotal)/float64(total)))

	// ---- E A PERGUNTA QUE APAGA CÓDIGO --------------------------------------------
	//
	// Acima, os bytes do quadro passam pela memória do Go: destranca o buffer, copia
	// 1,38 MB para um `[]byte`, copia de volta para um buffer do Media Foundation, e só
	// então entrega ao compressor. Duas cópias de um megabyte e meio por quadro, e um
	// buffer de entrada só nosso para manter.
	//
	// Mas o compressor de software recebe um `IMFSample` — e a amostra que o Video
	// Processor devolve JÁ É UMA. Se ele a aceitar direto, some a cópia dupla, some o
	// buffer de entrada, some a leitura explícita. O compressor destranca por dentro,
	// e o pipeline continua valendo porque quem escolhe a VOLTA em que ele destranca
	// continua sendo nós.
	//
	// Se não aceitar, o caminho de cima é o certo — e aí a cópia é o preço.

	t.Logf("")
	t.Logf("--- A AMOSTRA DO VIDEO PROCESSOR, DIRETO NO COMPRESSOR ---")

	sw2, _, err := abrirCompressorDeSoftware(t, saidaL, saidaA, passo)
	if err != nil {
		t.Fatalf("reabrir o compressor de software: %v", err)
	}
	defer sw2.soltar()
	sw2.chamar(transMandarRecado, recadoComecarFluxo, 0)
	sw2.chamar(transMandarRecado, recadoAbrirFluxo, 0)

	saida2, buf2, err := amostraDeMemoria(tamSaida)
	if err != nil {
		t.Fatalf("reservar a saída: %v", err)
	}
	defer saida2.soltar()
	defer buf2.soltar()

	var diretoTotal time.Duration
	diretoRodadas, diretoQuadros := 0, 0
	pendente = 0
	var recusa error

	for i := 0; i < quantos; i++ {
		tex, e := pegarUmQuadroDeVerdade(t, tela)
		if e != nil {
			continue
		}
		diretoRodadas++
		quando := time.Duration(i) * time.Second / 60

		marco := time.Now()
		tela.contexto.chamar(d3dCopiarTudo, uintptr(nosso.textura), uintptr(tex))
		tela.SoltarQuadro()
		nosso.amostra.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))

		nova, e := converter(vp, nosso.amostra, trazAmostra, infoVP.Tamanho)
		if e != nil {
			t.Fatalf("conversão no quadro %d: %v", i, e)
		}

		if pendente != 0 {
			pendente.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))
			pendente.chamar(amostraDefinirDuracao, uintptr(10_000_000/30))
			if e := hr(sw2.chamar(transEntrarQuadro, 0, uintptr(pendente), 0),
				"entregar a amostra do Video Processor direto"); e != nil {
				recusa = e
				pendente.soltar()
				pendente = nova
				break
			}
			n := puxarTudo(sw2, saida2, buf2)
			if n > 0 {
				diretoQuadros++
			}
			pendente.soltar()
		}
		diretoTotal += time.Since(marco)
		pendente = nova
	}
	if pendente != 0 {
		pendente.soltar()
	}

	if recusa != nil {
		t.Logf("  RECUSADO: %v", recusa)
		t.Logf("  => a cópia pela memória do Go é obrigatória; o caminho de cima é o certo")
		return
	}
	if diretoQuadros == 0 {
		t.Logf("  aceitou a amostra mas não devolveu H.264 nenhum")
		t.Logf("  => a cópia pela memória do Go é obrigatória; o caminho de cima é o certo")
		return
	}
	direto := diretoTotal / time.Duration(diretoRodadas)
	t.Logf("  ACEITOU — %d rodadas, %d renderam H.264", diretoRodadas, diretoQuadros)
	t.Logf("  TOTAL                                        %8.2fms", float64(direto.Microseconds())/1000)
	t.Logf("")
	orcamentos(t, direto)
	t.Logf("")
	t.Logf("VEREDITO FINAL: %.2fms (ler na hora) -> %.2fms (pipeline) -> %.2fms (direto)",
		float64(total.Microseconds())/1000,
		float64(pTotal.Microseconds())/1000,
		float64(direto.Microseconds())/1000)
}

// puxarTudo esvazia o compressor e devolve quantos bytes de H.264 saíram.
func puxarTudo(sw, saidaAmostra, bufSaida objeto) int {
	total := 0
	for {
		bufSaida.chamar(bufDefinirTamanho, 0)
		saida := saidaDoCompressor{Amostra: saidaAmostra}
		var estado uint32
		r := sw.chamar(transSairQuadro, 0, 1,
			uintptr(unsafe.Pointer(&saida)), uintptr(unsafe.Pointer(&estado)))
		if uint32(r)&0x80000000 != 0 {
			return total
		}
		if saida.Eventos != 0 {
			saida.Eventos.soltar()
		}
		var tam uint32
		bufSaida.chamar(bufTamanhoAtual, uintptr(unsafe.Pointer(&tam)))
		if tam == 0 {
			return total
		}
		total += int(tam)
	}
}

// SONDA DO OUTRO LADO — a máquina fraca consegue VER a tela dos outros?
//
// Transmitir e receber falham por motivos independentes, e a pergunta do dono é sobre os
// dois ("entrar em calls E transmitir"). Esta sonda responde o lado de RECEBER, e a
// resposta é boa: o caminho já é livre de placa, por construção.
//
// O QUE ELA MEDIU, e por que o número que ela imprime engana:
//
//	Microsoft H264 Video Decoder MFT     fala D3D11: true
//
// Um só decodificador, e ele diz falar D3D11 — o que parece condenar a máquina sem
// placa. NÃO CONDENA, por duas razões que só o código responde:
//
//  1. `FalaD3D11` lê o `MF_SA_D3D11_AWARE`, que quer dizer "SEI usar placa", e não
//     "EXIJO placa". É o mesmo atributo que este projeto já pegou mentindo antes (ver
//     `abrirGeradorSeAssincrono`), e a lição se repete: o atributo descreve capacidade,
//     nunca requisito.
//  2. `amarrarDescompressor` NUNCA manda `recadoDefinirD3D`. Sem gerenciador entregue, o
//     decodificador roda na memória principal e devolve NV12 num buffer que NÓS
//     alocamos — que é exatamente o caminho que `medirASaida` já trata.
//
// Ou seja: receber tela já funciona em qualquer máquina, e é por isso que este arquivo
// só precisou construir o lado de MANDAR. A sonda fica como registro, para a próxima
// pessoa não refazer a mesma pergunta.
//
//	go test -run SondaDosDecodificadores -v
func TestSondaDosDecodificadores(t *testing.T) {
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

	lista, err := procurarDescompressores()
	if err != nil {
		t.Fatalf("procurar decodificadores: %v", err)
	}
	defer SoltarCompressores(lista)

	if len(lista) == 0 {
		t.Fatal("nenhum decodificador de H.264 — esta máquina não veria tela nenhuma")
	}
	for _, c := range lista {
		fala, _ := c.FalaD3D11()
		t.Logf("  %-45s sabe usar placa: %v", c.Nome, fala)
	}

	// A PROVA DE VERDADE não é a lista: é abrir um sem entregar dispositivo nenhum. É o
	// que `AbrirDescompressor` faz em produção, e o que esta linha confere.
	d, err := AbrirDescompressor(1280, 720)
	if err != nil {
		t.Fatalf("nenhum decodificador abriu SEM placa: %v", err)
	}
	defer d.Fechar()
	t.Logf("abriu %q sem entregar placa nenhuma — receber tela não depende de hardware", d.Nome)
}

func orcamentos(t *testing.T, custo time.Duration) {
	for _, fps := range []int{60, 30, 15} {
		orcamento := time.Second / time.Duration(fps)
		folga := 100 * (1 - float64(custo)/float64(orcamento))
		veredito := "CABE"
		if folga < 0 {
			veredito = "NÃO CABE"
		} else if folga < 40 {
			veredito = "cabe apertado"
		}
		t.Logf("  a %d/s (orçamento %.2fms): %5.0f%% de folga — %s",
			fps, float64(orcamento.Microseconds())/1000, folga, veredito)
	}
	t.Logf("  teto desta máquina em software: %.0f quadros por segundo", float64(time.Second)/float64(custo))
}

// converter entrega um quadro ao Video Processor e devolve a amostra de saída SEM LER.
// Quem chama fica dono dela e precisa soltá-la.
func converter(vp, amostra objeto, trazAmostra bool, tamanho uint32) (objeto, error) {
	if err := hr(vp.chamar(transEntrarQuadro, 0, uintptr(amostra), 0),
		"entregar ao Video Processor"); err != nil {
		return 0, err
	}
	var saida saidaDoCompressor
	if !trazAmostra {
		nossaAmostra, nossoBuffer, err := amostraDeMemoria(int(tamanho))
		if err != nil {
			return 0, err
		}
		nossoBuffer.soltar() // a amostra segura a referência
		saida.Amostra = nossaAmostra
	}
	var estado uint32
	r := vp.chamar(transSairQuadro, 0, 1,
		uintptr(unsafe.Pointer(&saida)), uintptr(unsafe.Pointer(&estado)))
	if uint32(r) == querMaisEntrada {
		return 0, nil
	}
	if err := hr(r, "pegar o quadro convertido"); err != nil {
		return 0, err
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}
	return saida.Amostra, nil
}

// lerAmostra traz os bytes de uma amostra para a memória do Go.
func lerAmostra(amostra objeto) ([]byte, int, error) {
	var buffer objeto
	if r := amostra.chamar(amostraJuntarBuffers, uintptr(unsafe.Pointer(&buffer))); uint32(r)&0x80000000 != 0 {
		return nil, 0, hr(r, "juntar os pedaços do quadro")
	}
	defer buffer.soltar()

	passo := 0
	if b2d, err := buffer.consultar(&iidBuffer2D); err == nil {
		var p int32
		var linha uintptr
		if b2d.chamar(buf2DTrancar, uintptr(unsafe.Pointer(&linha)), uintptr(unsafe.Pointer(&p)))&0x80000000 == 0 {
			passo = int(p)
			b2d.chamar(buf2DDestrancar)
		}
		b2d.soltar()
	}

	var p uintptr
	var maximo, atual uint32
	if err := hr(buffer.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)), uintptr(unsafe.Pointer(&maximo)), uintptr(unsafe.Pointer(&atual))),
		"abrir o quadro para leitura"); err != nil {
		return nil, passo, err
	}
	dados := make([]byte, atual)
	copy(dados, unsafe.Slice((*byte)(unsafe.Pointer(p)), atual))
	buffer.chamar(bufDestrancar)
	return dados, passo, nil
}

// ladoDoProcessador amarra um lado do Video Processor num formato e tamanho.
//
// Igual ao `definirLado` de `redimensionador.go`, mas com o formato escolhível — que é
// exatamente a mudança que esta sonda existe para justificar.
func ladoDoProcessador(t objeto, indice int, formato windows.GUID, largura, altura int) error {
	var tipo objeto
	res, _, _ := procMFCriarTipo.Call(uintptr(unsafe.Pointer(&tipo)))
	if err := hr(res, "criar o tipo"); err != nil {
		return err
	}
	defer tipo.soltar()

	definirGUID(tipo, &chaveTipoMaior, tipoMaiorVideo)
	definirGUID(tipo, &chaveSubtipo, formato)
	definirNumero(tipo, &chaveEntrelacamento, progressivo)
	definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)

	return hr(t.chamar(indice, 0, uintptr(tipo), 0), "amarrar o lado")
}

func passarPeloProcessador(vp, amostra objeto, trazAmostra bool, tamanho uint32) ([]byte, int, error) {
	b, p, _, _, err := passarPeloProcessadorMedido(vp, amostra, trazAmostra, tamanho)
	return b, p, err
}

// passarPeloProcessadorMedido entrega um quadro e devolve os bytes que saíram, JÁ NA
// MEMÓRIA PRINCIPAL — que é a pergunta 2 inteira.
//
// SEPARA A CONVERSÃO DA LEITURA, e a separação é o ponto. As duas somam num número só
// que não diz o que otimizar, e os remédios são opostos: conversão cara pede quadro
// menor; leitura cara é a CPU parada esperando a placa terminar, e isso se recupera com
// pipeline — foi exatamente o que destravou os 60fps do caminho de hardware.
func passarPeloProcessadorMedido(vp, amostra objeto, trazAmostra bool, tamanho uint32) (
	dados []byte, passo int, conversao, leitura time.Duration, err error) {

	marco := time.Now()

	if err = hr(vp.chamar(transEntrarQuadro, 0, uintptr(amostra), 0),
		"entregar ao Video Processor"); err != nil {
		return nil, 0, time.Since(marco), 0, err
	}

	var saida saidaDoCompressor
	if !trazAmostra {
		nossaAmostra, nossoBuffer, e := amostraDeMemoria(int(tamanho))
		if e != nil {
			return nil, 0, time.Since(marco), 0, e
		}
		defer nossaAmostra.soltar()
		defer nossoBuffer.soltar()
		saida.Amostra = nossaAmostra
	}

	var estado uint32
	r := vp.chamar(transSairQuadro, 0, 1,
		uintptr(unsafe.Pointer(&saida)), uintptr(unsafe.Pointer(&estado)))
	conversao = time.Since(marco)

	if uint32(r) == querMaisEntrada {
		return nil, 0, conversao, 0, nil
	}
	if err = hr(r, "pegar o quadro convertido"); err != nil {
		return nil, 0, conversao, 0, err
	}
	if saida.Eventos != 0 {
		saida.Eventos.soltar()
	}
	// S_OK SEM AMOSTRA ACONTECE, e não é erro — é a mesma guarda que o `sair` de
	// produção tem em `transmissao.go`. Sem ela a volta seguinte chama método em
	// ponteiro nulo, que em COM não é erro devolvido: é queda do processo.
	if saida.Amostra == 0 {
		return nil, 0, conversao, 0, nil
	}
	if trazAmostra {
		defer saida.Amostra.soltar()
	}

	// A LEITURA COMEÇA AQUI. Tudo acima é ordem dada à placa e volta na hora; é este
	// trecho que espera a placa TERMINAR, porque destrancar um buffer que embrulha
	// textura obriga o Media Foundation a trazer os bytes para a memória principal.
	marco = time.Now()

	var buffer objeto
	if r := saida.Amostra.chamar(amostraJuntarBuffers, uintptr(unsafe.Pointer(&buffer))); uint32(r)&0x80000000 != 0 {
		return nil, 0, conversao, time.Since(marco), hr(r, "juntar os pedaços do quadro convertido")
	}
	defer buffer.soltar()

	// O PASSO SÓ EXISTE SE ELE FOR UM BUFFER 2D. Buffer de memória comum é contíguo e o
	// passo é a largura; buffer que embrulha textura tem o alinhamento da placa dentro.
	if b2d, e := buffer.consultar(&iidBuffer2D); e == nil {
		var p int32
		var linha uintptr
		if b2d.chamar(buf2DTrancar, uintptr(unsafe.Pointer(&linha)), uintptr(unsafe.Pointer(&p)))&0x80000000 == 0 {
			passo = int(p)
			b2d.chamar(buf2DDestrancar)
		}
		b2d.soltar()
	}

	var p uintptr
	var maximo, atual uint32
	r = buffer.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)),
		uintptr(unsafe.Pointer(&maximo)),
		uintptr(unsafe.Pointer(&atual)),
	)
	if err = hr(r, "abrir o quadro convertido para leitura"); err != nil {
		return nil, passo, conversao, time.Since(marco), err
	}
	dados = make([]byte, atual)
	copy(dados, unsafe.Slice((*byte)(unsafe.Pointer(p)), atual))
	buffer.chamar(bufDestrancar)
	return dados, passo, conversao, time.Since(marco), nil
}

// abrirCompressorDeSoftware pega o candidato que NÃO fala D3D11 — o de emergência.
func abrirCompressorDeSoftware(t *testing.T, largura, altura, passo int) (objeto, string, error) {
	lista, err := ProcurarCompressores()
	if err != nil {
		return 0, "", err
	}
	defer SoltarCompressores(lista)

	for _, cand := range lista {
		fala, _ := cand.FalaD3D11()
		if fala {
			continue
		}
		mft, err := cand.Montar()
		if err != nil {
			t.Logf("  %s não liga: %v", cand.Nome, err)
			continue
		}
		if err := destrancarSeAssincrono(mft); err != nil {
			mft.soltar()
			continue
		}
		if err := configurarSaida(mft, largura, altura, 30, 2500); err != nil {
			t.Logf("  %s recusou a saída: %v", cand.Nome, err)
			mft.soltar()
			continue
		}
		if err := entradaNV12(mft, largura, altura, passo); err != nil {
			t.Logf("  %s recusou NV12 na entrada: %v", cand.Nome, err)
			mft.soltar()
			continue
		}
		return mft, cand.Nome, nil
	}
	return 0, "", errSemCompressorDeSoftware
}

var errSemCompressorDeSoftware = errorDeSonda("nenhum compressor de software aceitou NV12")

type errorDeSonda string

func (e errorDeSonda) Error() string { return string(e) }

// entradaNV12 amarra a entrada do compressor em NV12, partindo do tipo que ele oferece.
func entradaNV12(t objeto, largura, altura, passo int) error {
	for i := uint32(0); i < 64; i++ {
		var tipo objeto
		r := t.chamar(transTipoDeEntrada, 0, uintptr(i), uintptr(unsafe.Pointer(&tipo)))
		if uint32(r)&0x80000000 != 0 {
			break
		}
		var sub windows.GUID
		lido := tipo.chamar(atrPegarGUID,
			uintptr(unsafe.Pointer(&chaveSubtipo)), uintptr(unsafe.Pointer(&sub)))
		if uint32(lido)&0x80000000 != 0 || sub != formatoNV12 {
			tipo.soltar()
			continue
		}
		definirNumero(tipo, &chaveEntrelacamento, progressivo)
		definirPar(tipo, &chaveTamanhoDoQuadro, largura, altura)
		definirPar(tipo, &chaveTaxaDeQuadros, 30, 1)
		if passo > 0 {
			definirNumero(tipo, &chavePassoDaLinha, uint32(passo))
		}
		r = t.chamar(transDefinirEntrada, 0, uintptr(tipo), 0)
		tipo.soltar()
		return hr(r, "amarrar a entrada em NV12")
	}
	return errorDeSonda("ele não oferece NV12 na entrada")
}

// amostraDeMemoria cria uma amostra com um buffer na memória PRINCIPAL.
func amostraDeMemoria(tamanho int) (objeto, objeto, error) {
	var buffer objeto
	r, _, _ := procMFCriarBufferDeMemoria.Call(uintptr(tamanho), uintptr(unsafe.Pointer(&buffer)))
	if err := hr(r, "reservar memória"); err != nil {
		return 0, 0, err
	}
	var amostra objeto
	r, _, _ = procMFCriarAmostra.Call(uintptr(unsafe.Pointer(&amostra)))
	if err := hr(r, "criar a amostra"); err != nil {
		buffer.soltar()
		return 0, 0, err
	}
	if err := hr(amostra.chamar(amostraSomarBuffer, uintptr(buffer)), "amarrar o buffer"); err != nil {
		buffer.soltar()
		amostra.soltar()
		return 0, 0, err
	}
	return amostra, buffer, nil
}

// comprimirUmaVez enche a entrada, entrega e puxa o que sair. Devolve quantos bytes de
// H.264 vieram.
func comprimirUmaVez(sw, entrada, bufEntrada, saidaAmostra, bufSaida objeto, nv12 []byte, quando time.Duration) (int, error) {
	var p uintptr
	var maximo, atual uint32
	if err := hr(bufEntrada.chamar(bufTrancar,
		uintptr(unsafe.Pointer(&p)), uintptr(unsafe.Pointer(&maximo)), uintptr(unsafe.Pointer(&atual))),
		"abrir a entrada"); err != nil {
		return 0, err
	}
	copy(unsafe.Slice((*byte)(unsafe.Pointer(p)), maximo), nv12)
	bufEntrada.chamar(bufDestrancar)
	bufEntrada.chamar(bufDefinirTamanho, uintptr(len(nv12)))
	entrada.chamar(amostraDefinirTempo, uintptr(quando.Nanoseconds()/100))
	entrada.chamar(amostraDefinirDuracao, uintptr(10_000_000/30))

	if err := hr(sw.chamar(transEntrarQuadro, 0, uintptr(entrada), 0),
		"entregar ao compressor de software"); err != nil {
		return 0, err
	}

	total := 0
	for {
		bufSaida.chamar(bufDefinirTamanho, 0)
		saida := saidaDoCompressor{Amostra: saidaAmostra}
		var estado uint32
		r := sw.chamar(transSairQuadro, 0, 1,
			uintptr(unsafe.Pointer(&saida)), uintptr(unsafe.Pointer(&estado)))
		if uint32(r) == querMaisEntrada {
			return total, nil
		}
		if uint32(r) == mudouAFormaDaSaida {
			return total, nil
		}
		if err := hr(r, "puxar o H.264 de software"); err != nil {
			return total, err
		}
		if saida.Eventos != 0 {
			saida.Eventos.soltar()
		}
		var tam uint32
		bufSaida.chamar(bufTamanhoAtual, uintptr(unsafe.Pointer(&tam)))
		total += int(tam)
		if tam == 0 {
			return total, nil
		}
	}
}

// pegarUmQuadroDeVerdade insiste até a duplicação entregar. A área de trabalho parada
// não produz quadro — e uma sonda que desiste no primeiro tempo esgotado mede o nada.
func pegarUmQuadroDeVerdade(t *testing.T, tela *Tela) (objeto, error) {
	var ultimo error
	for i := 0; i < 40; i++ {
		tex, err := tela.ProximoQuadro(50)
		if err != nil {
			ultimo = err
			continue
		}
		if tex != 0 {
			return tex, nil
		}
		tela.SoltarQuadro()
	}
	if ultimo == nil {
		ultimo = errorDeSonda("a tela não mudou em dois segundos")
	}
	return 0, ultimo
}
