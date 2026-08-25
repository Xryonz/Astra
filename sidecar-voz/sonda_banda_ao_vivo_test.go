package main

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

	rodar(3 * time.Second)

	const janela = 6 * time.Second
	medidaAlta := rodar(janela)

	v := variante{tipo: varInteiroSemSinal, valor: uintptr(baixa) * 1000}
	chave := chaveBandaMediaDoCodec
	rSet := c.comandos.chamar(codecDefinirValor,
		uintptr(unsafe.Pointer(&chave)), uintptr(unsafe.Pointer(&v)))
	t.Logf("ICodecAPI SetValue(%d kbps) devolveu 0x%08X", baixa, uint32(rSet))
	medidaComando := rodar(janela)

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

	if medidaComando < medidaAlta*0.8 || medidaTipo < medidaAlta*0.8 {
		t.Log("UM DOS CAMINHOS OBEDECEU — a adaptação de banda pode deixar de reabrir o compressor")

		return
	}
	t.Errorf("nenhum caminho mudou o fluxo (%.0f / %.0f / %.0f kbps): "+
		"a banda continua só ajustável na abertura, e a adaptação segue reabrindo",
		medidaAlta, medidaComando, medidaTipo)
}
