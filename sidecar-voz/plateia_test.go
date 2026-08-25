package main

import (
	"bufio"
	"context"
	"encoding/json"
	"io"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
)

func TestAPlateiaSoEscreveParaQuemAssiste(t *testing.T) {
	p := NovaPlateia()

	for _, quem := range []string{"B", "C"} {
		if _, err := p.Entrar(quem); err != nil {
			t.Fatalf("entrar %s: %v", quem, err)
		}
	}

	amostra := media.Sample{Data: []byte{0, 0, 0, 1, 0x65}, Duration: 33 * time.Millisecond}

	escritos, err := p.Escrever(amostra)
	if err != nil {
		t.Fatalf("escrever: %v", err)
	}
	if escritos != 2 {
		t.Errorf("quem entra recebe até dizer o contrário; escreveu para %d de 2", escritos)
	}

	p.Assiste("C", false)
	if escritos, _ = p.Escrever(amostra); escritos != 1 {
		t.Errorf("C avisou que não assiste; esperava escrever para 1, escreveu para %d", escritos)
	}
	if assistindo, total := p.Contar(); assistindo != 1 || total != 2 {
		t.Errorf("C segue na chamada, só não assiste: esperava 1 de 2, deu %d de %d", assistindo, total)
	}

	p.Assiste("naoEstaNaChamada", true)
	if _, total := p.Contar(); total != 2 {
		t.Errorf("aviso de quem não tem assento não pode criar um: a plateia foi para %d", total)
	}

	p.Sair("B")
	if escritos, _ = p.Escrever(amostra); escritos != 0 {
		t.Errorf("com B fora e C sem assistir, ninguém devia receber; escreveu para %d", escritos)
	}

	p.Assiste("C", true)
	if escritos, _ = p.Escrever(amostra); escritos != 1 {
		t.Errorf("C voltou a assistir; esperava 1, escreveu para %d", escritos)
	}
}

func TestOAvisoDeQuemAssisteChegaAQuemTransmite(t *testing.T) {
	ctx, cancelar := context.WithTimeout(context.Background(), 40*time.Second)
	defer cancelar()

	canoA, escreveA := io.Pipe()
	canoB, escreveB := io.Pipe()

	plateiaA := NovaPlateia()
	config := webrtc.Configuration{}

	parA, err := NovoPar("B", config, nil, plateiaA, nil, nil, NewEscritor(escreveA))
	if err != nil {
		t.Fatalf("criar quem transmite: %v", err)
	}
	defer parA.Fechar()

	parB, err := NovoPar("A", config, nil, NovaPlateia(), nil, nil, NewEscritor(escreveB))
	if err != nil {
		t.Fatalf("criar quem assiste: %v", err)
	}
	defer parB.Fechar()

	rotear := func(de io.Reader, para *Par) {
		linhas := bufio.NewScanner(de)
		linhas.Buffer(make([]byte, 0, 64*1024), 1024*1024)
		for linhas.Scan() {
			var ev Evento
			if json.Unmarshal(linhas.Bytes(), &ev) != nil || ev.Ev != EvSinal {
				continue
			}
			_ = para.Receber(ctx, ev.Tipo, ev.Dados)
		}
	}
	go rotear(canoA, parB)
	go rotear(canoB, parA)

	if err := parA.Oferecer(ctx); err != nil {
		t.Fatalf("oferecer: %v", err)
	}

	aguardar := func(quer bool, oque string) {
		t.Helper()
		alvo := 0
		if quer {
			alvo = 1
		}
		limite := time.Now().Add(25 * time.Second)
		for time.Now().Before(limite) {
			if assistindo, total := plateiaA.Contar(); total == 1 && assistindo == alvo {
				return
			}
			parB.AvisarQueAssisto(quer)
			time.Sleep(100 * time.Millisecond)
		}
		assistindo, total := plateiaA.Contar()
		t.Fatalf("%s: quem transmite enxerga %d assistindo de %d na plateia", oque, assistindo, total)
	}

	aguardar(false, "depois de quem assiste sair do palco")
	t.Log("quem transmite parou de enviar para quem não assiste")

	aguardar(true, "depois de quem assiste voltar ao palco")
	t.Log("quem transmite voltou a enviar quando o palco mudou de volta")
}
