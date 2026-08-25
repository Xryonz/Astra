package main

import (
	"testing"
	"unsafe"
)

func chamarPelaTabela(b *BufferDeMidia, indice int, args ...uintptr) uintptr {

	return objeto(b.Ponteiro()).chamar(indice, args...)
}

func TestBufferDeMidiaPelaVtable(t *testing.T) {
	const capacidade = 1920
	b := NovoBufferDeMidia(capacidade)
	defer b.Fechar()

	var maximo uint32
	if r := chamarPelaTabela(b, 4, uintptr(unsafe.Pointer(&maximo))); r != sOK {
		t.Fatalf("GetMaxLength devolveu 0x%X", r)
	}
	if maximo != capacidade {
		t.Fatalf("capacidade veio %d, esperava %d", maximo, capacidade)
	}

	var endereco uintptr
	var usado uint32
	if r := chamarPelaTabela(b, 5,
		uintptr(unsafe.Pointer(&endereco)), uintptr(unsafe.Pointer(&usado))); r != sOK {
		t.Fatalf("GetBufferAndLength devolveu 0x%X", r)
	}
	if endereco == 0 {
		t.Fatal("devolveu endereco nulo — o cancelador escreveria em lugar nenhum")
	}
	if usado != 0 {
		t.Fatalf("buffer novo ja dizia ter %d bytes", usado)
	}

	destino := unsafe.Slice((*byte)(unsafe.Pointer(endereco)), capacidade)
	for i := 0; i < 640; i++ {
		destino[i] = byte(i % 251)
	}
	if r := chamarPelaTabela(b, 3, 640); r != sOK {
		t.Fatalf("SetLength devolveu 0x%X", r)
	}
	if b.Usado() != 640 {
		t.Fatalf("Usado() = %d depois de SetLength(640)", b.Usado())
	}
	conteudo := b.Conteudo()
	if len(conteudo) != 640 || conteudo[100] != byte(100%251) {
		t.Fatal("o que o Windows escreveu nao chegou ao lado Go")
	}

	if r := chamarPelaTabela(b, 3, capacidade+1); r == sOK {
		t.Fatal("aceitou tamanho maior que a capacidade")
	}

	b.Zerar()
	if b.Usado() != 0 {
		t.Fatal("Zerar nao zerou; o buffer encheria e o audio pararia depois de alguns quadros")
	}
}

func TestBufferDeMidiaConsultaInterface(t *testing.T) {
	b := NovoBufferDeMidia(64)
	defer b.Fechar()

	var saida uintptr
	r := chamarPelaTabela(b, 0,
		uintptr(unsafe.Pointer(&iidBufferDeMidia)), uintptr(unsafe.Pointer(&saida)))
	if r != sOK || saida != b.Ponteiro() {
		t.Fatalf("QueryInterface(IMediaBuffer) devolveu 0x%X / %v", r, saida == b.Ponteiro())
	}

	saida = 0
	if r := chamarPelaTabela(b, 0,
		uintptr(unsafe.Pointer(&iidDesconhecido)), uintptr(unsafe.Pointer(&saida))); r != sOK {
		t.Fatalf("QueryInterface(IUnknown) devolveu 0x%X", r)
	}

	outro := guid(0xDEADBEEF, 0x0000, 0x0000, [8]byte{})
	saida = 0xFFFF
	r = chamarPelaTabela(b, 0,
		uintptr(unsafe.Pointer(&outro)), uintptr(unsafe.Pointer(&saida)))
	if r == sOK {
		t.Fatal("aceitou uma interface que nao implementa")
	}
	if saida != 0 {
		t.Fatal("recusou mas deixou lixo no ponteiro de saida")
	}
}

func TestBufferDeMidiaContagem(t *testing.T) {
	b := NovoBufferDeMidia(64)
	defer b.Fechar()

	antes := chamarPelaTabela(b, 1)
	depois := chamarPelaTabela(b, 2)
	if depois != antes-1 {
		t.Fatalf("AddRef deu %d e Release deu %d", antes, depois)
	}

	var maximo uint32
	if r := chamarPelaTabela(b, 4, uintptr(unsafe.Pointer(&maximo))); r != sOK || maximo != 64 {
		t.Fatal("o objeto parou de responder depois de um Release")
	}
}
