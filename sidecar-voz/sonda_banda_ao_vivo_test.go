package main

// SONDA: DÁ PARA MUDAR A BANDA COM O COMPRESSOR ABERTO?
//
// A resposta é NÃO, nesta máquina, por três caminhos diferentes — e é por isso que a
// adaptação de banda reabre o compressor em vez de fazer a coisa óbvia.
//
// A PERGUNTA IMPORTA porque reabrir custa: um quadro-chave e uns décimos sem imagem,
// justamente quando a rede já está sofrendo, que é quando o ajuste é pedido. Mudar ao
// vivo seria uma chamada e nenhum engasgo.
//
// AS TRÊS ROTAS, e todas foram MEDIDAS, não deduzidas:
//
//	1. ICodecAPI SetValue(AVEncCommonMeanBitRate)
//	   `TestSondaDoCodecAPI` mostra que os quatro compressores desta máquina aceitam a
//	   chamada com S_OK — inclusive os dois que declaram `IsModifiable = não`. Este
//	   teste pesou o fluxo depois: pedido 3000 -> saiu 3015; pedido 600 -> saiu 3014;
//	   pedido 3000 -> saiu 3015. Reto.
//
//	2. SetOutputType com a banda nova, no meio do fluxo
//	   Aceito e ignorado do mesmo jeito. Saiu 3015 nas três fases.
//
//	3. Encerrar o fluxo, repor o tipo, reabrir o fluxo
//	   Derruba o compressor: "puxar o H.264: Falha catastrófica".
//
// A CONCLUSÃO QUE FICA: o compressor honra o `MF_MT_AVG_BITRATE` que recebe na ABERTURA
// — 3000 pedidos viraram 3015 medidos, e a constância do número em tela parada mostra
// que ele está em taxa constante e enche o que falta. Depois disso o controle de banda
// está travado.
//
// ACEITAR NÃO É OBEDECER, e as duas primeiras rotas são a mesma pegadinha do
// `MF_MT_MAX_KEYFRAME_SPACING`, aceito com todas as honras e sem efeito nenhum. É a
// quarta declaração do Media Foundation a mentir neste projeto. Perguntar ao objeto se
// ele aceita não basta: tem de pesar o que sai.
//
// O teste fica como REGISTRO e como rede de segurança: se um dia um compressor passar a
// obedecer de verdade, ele começa a falhar aqui — e aí a adaptação pode largar o
// reabrir. Por isso ele reprova quando o fluxo NÃO muda.
//
//	ASTRA_TESTE_TELA=1 ASTRA_TESTE_BANDA_AO_VIVO=1 go test -run BandaAoVivo -v

import (
	"os"
	"runtime"
	"testing"
	"time"
	"unsafe"
)

func TestMudarBandaAoVivo(t *testing.T) {
	precisaDeTela(t)
	if os.Getenv("ASTRA_TESTE_BANDA_AO_VIVO") == "" {
		t.Skip("defina ASTRA_TESTE_BANDA_AO_VIVO=1 (sonda de registro; sabidamente reprova)")
	}

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

	tela, err := AbrirTela(0)
	if err != nil {
		t.Skipf("sem tela para capturar: %v", err)
	}
	defer tela.Fechar()

	const alta, baixa = 3000, 600
	c, err := AbrirCompressor(tela, 1280, 720, 30, alta)
	if err != nil {
		t.Fatalf("abrir o compressor: %v", err)
	}
	defer c.Fechar()
	t.Logf("compressor %q", c.Nome)

	var bytes int
	receber := func(pronto []byte) { bytes += len(pronto) }

	// Roda por um tempo e devolve quantos kbps DE FATO saíram.
	//
	// Mede pelo relógio e não por quadro: é a banda que está em jogo, e banda é bytes
	// por segundo. Uma tela mais parada rende menos quadros, e contar por quadro
	// esconderia isso dentro da média.
	rodar := func(quanto time.Duration) float64 {
		ritmo := NovoRitmo(c.fps)
		antes := bytes
		comeco := time.Now()
		fim := comeco.Add(quanto)
		for time.Now().Before(fim) {
			ritmo.Esperar()
			textura, err := tela.ProximoQuadro(100)
			if err != nil {
				t.Fatalf("capturar: %v", err)
			}
			if textura == 0 {
				if err := c.Drenar(receber); err != nil {
					t.Fatalf("colher: %v", err)
				}
				continue
			}
			if err := c.Comprimir(textura, time.Since(comeco), receber); err != nil {
				t.Fatalf("comprimir: %v", err)
			}
			textura.soltar()
			tela.SoltarQuadro()
		}
		return float64(bytes-antes) * 8 / time.Since(comeco).Seconds() / 1000
	}

	// O AQUECIMENTO É DESCARTADO. Os primeiros quadros trazem o quadro-chave de
	// abertura, que sozinho é dezenas de vezes maior que um quadro comum — contá-lo
	// inflaria a primeira medida e faria a queda seguinte parecer maior do que é.
	rodar(3 * time.Second)

	const janela = 6 * time.Second
	medidaAlta := rodar(janela)

	// ROTA 1: a via de comando. Aceita com S_OK e sem efeito.
	v := variante{tipo: varInteiroSemSinal, valor: uintptr(baixa) * 1000}
	chave := chaveBandaMediaDoCodec
	rSet := c.comandos.chamar(codecDefinirValor,
		uintptr(unsafe.Pointer(&chave)), uintptr(unsafe.Pointer(&v)))
	t.Logf("ICodecAPI SetValue(%d kbps) devolveu 0x%08X", baixa, uint32(rSet))
	medidaComando := rodar(janela)

	// ROTA 2: repor o tipo de saída no meio do fluxo. Também aceita e também sem efeito.
	if err := configurarSaida(c.t, c.saidaL, c.saidaA, c.fps, baixa); err != nil {
		t.Logf("SetOutputType no meio do fluxo recusou: %v", err)
	} else {
		t.Logf("SetOutputType no meio do fluxo foi aceito")
	}
	medidaTipo := rodar(janela)

	t.Logf("")
	t.Logf("pedido %4d kbps na ABERTURA      -> saiu %7.0f kbps", alta, medidaAlta)
	t.Logf("pedido %4d kbps por ICodecAPI    -> saiu %7.0f kbps", baixa, medidaComando)
	t.Logf("pedido %4d kbps por SetOutputType-> saiu %7.0f kbps", baixa, medidaTipo)

	if medidaAlta <= 0 {
		t.Skip("a área de trabalho não mudou; sem fluxo para medir")
	}

	// REPROVA QUANDO O FLUXO NÃO MUDA, e a inversão é de propósito: hoje ele reprova, e
	// isso é o esperado. No dia em que um compressor passar a obedecer, o teste PASSA e
	// avisa que a adaptação pode largar o reabrir.
	if medidaComando < medidaAlta*0.8 || medidaTipo < medidaAlta*0.8 {
		t.Log("UM DOS CAMINHOS OBEDECEU — a adaptação de banda pode deixar de reabrir o compressor")

		return
	}
	t.Errorf("nenhum caminho mudou o fluxo (%.0f / %.0f / %.0f kbps): "+
		"a banda continua só ajustável na abertura, e a adaptação segue reabrindo",
		medidaAlta, medidaComando, medidaTipo)
}
