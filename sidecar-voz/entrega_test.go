package main

import (
	"bufio"
	"encoding/binary"
	"io"
	"net"
	"testing"
	"time"
)

func TestOCanoDeQuadrosEntregaOQuePrometeu(t *testing.T) {
	ouvinte, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("escutar: %v", err)
	}
	defer ouvinte.Close()

	t.Setenv("ASTRA_QUADROS", ouvinte.Addr().String())
	t.Setenv("ASTRA_QUADROS_SEGREDO", "abre-te-sesamo")

	e := NovaEntrega()
	if e == nil {
		t.Fatal("a entrega não subiu mesmo com endereço no ambiente")
	}
	defer e.Fechar()

	const largura, altura, passo = 4, 2, 8
	dados := make([]byte, passo*altura*3/2)
	for i := range dados {
		dados[i] = byte(i + 1)
	}
	e.Mandar("estrela-9", Quadro{Dados: dados, Largura: largura, Altura: altura, Passo: passo})

	_ = ouvinte.(*net.TCPListener).SetDeadline(time.Now().Add(5 * time.Second))
	con, err := ouvinte.Accept()
	if err != nil {
		t.Fatalf("o processo de voz não ligou: %v", err)
	}
	defer con.Close()
	_ = con.SetReadDeadline(time.Now().Add(5 * time.Second))

	leitor := bufio.NewReader(con)
	segredo, err := leitor.ReadString('\n')
	if err != nil {
		t.Fatalf("ler o segredo: %v", err)
	}
	if segredo != "abre-te-sesamo\n" {
		t.Fatalf("segredo %q, esperava %q", segredo, "abre-te-sesamo\n")
	}

	var cab [cabecalhoDoQuadro]byte
	if _, err := io.ReadFull(leitor, cab[:]); err != nil {
		t.Fatalf("ler o cabeçalho: %v", err)
	}
	if m := binary.LittleEndian.Uint32(cab[0:]); m != marcaDoQuadro {
		t.Fatalf("marca %#x, esperava %#x", m, marcaDoQuadro)
	}
	tamPar := binary.LittleEndian.Uint32(cab[4:])
	if l := binary.LittleEndian.Uint32(cab[8:]); l != largura {
		t.Errorf("largura %d, esperava %d", l, largura)
	}
	if a := binary.LittleEndian.Uint32(cab[12:]); a != altura {
		t.Errorf("altura %d, esperava %d", a, altura)
	}
	if p := binary.LittleEndian.Uint32(cab[16:]); p != passo {
		t.Errorf("passo %d, esperava %d — é o campo que ninguém erra até errar", p, passo)
	}
	tamDados := binary.LittleEndian.Uint32(cab[20:])
	if int(tamDados) != len(dados) {
		t.Fatalf("tamanho %d, esperava %d", tamDados, len(dados))
	}

	par := make([]byte, tamPar)
	if _, err := io.ReadFull(leitor, par); err != nil {
		t.Fatalf("ler o id do par: %v", err)
	}
	if string(par) != "estrela-9" {
		t.Errorf("par %q, esperava %q", par, "estrela-9")
	}

	veio := make([]byte, tamDados)
	if _, err := io.ReadFull(leitor, veio); err != nil {
		t.Fatalf("ler o quadro: %v", err)
	}
	for i := range veio {
		if veio[i] != dados[i] {
			t.Fatalf("o quadro chegou diferente no byte %d: %d contra %d", i, veio[i], dados[i])
		}
	}
}

func TestMandarNuncaEspera(t *testing.T) {
	ouvinte, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("escutar: %v", err)
	}
	defer ouvinte.Close()

	t.Setenv("ASTRA_QUADROS", ouvinte.Addr().String())
	t.Setenv("ASTRA_QUADROS_SEGREDO", "nada")

	e := NovaEntrega()
	if e == nil {
		t.Fatal("a entrega não subiu")
	}
	defer e.Fechar()

	dados := make([]byte, 512*1024)
	pronto := make(chan struct{})
	go func() {
		defer close(pronto)
		for i := 0; i < 100; i++ {
			e.Mandar("alguem", Quadro{Dados: dados, Largura: 640, Altura: 480, Passo: 640})
		}
	}()

	select {
	case <-pronto:
	case <-time.After(3 * time.Second):
		t.Fatal("Mandar bloqueou: o laço da rede pararia de consumir pacotes")
	}
}

func TestSemEnderecoNaoHaCano(t *testing.T) {
	t.Setenv("ASTRA_QUADROS", "")
	if e := NovaEntrega(); e != nil {
		t.Fatal("subiu cano sem endereço")
	}

	var nula *EntregaDeQuadros
	nula.Mandar("alguem", Quadro{Dados: []byte{1, 2, 3}})
	nula.Fechar()
}
