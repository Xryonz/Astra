package main

import (
	"testing"
	"unsafe"
)

// Prova o IMediaBuffer PELA VTABLE, e não chamando os métodos Go direto.
//
// Chamar `b.definirTamanho(...)` em Go provaria pouco: o que precisa funcionar é o
// caminho que o Windows usa — ler o primeiro campo do objeto, achar a tabela, saltar
// para o índice certo. Se o layout do struct estiver errado, ou o callback estiver
// na posição trocada, só este teste percebe. Chamada Go direta passaria feliz.
func chamarPelaTabela(b *BufferDeMidia, indice int, args ...uintptr) uintptr {
	// O MESMO chamador que o projeto usa para falar com objetos do Windows, agora
	// apontado para um objeto NOSSO. Se ele consegue, o Windows consegue: o
	// caminho é idêntico — primeiro campo, tabela, índice, saltar.
	return objeto(b.Ponteiro()).chamar(indice, args...)
}

func TestBufferDeMidiaPelaVtable(t *testing.T) {
	const capacidade = 1920 // 20ms de PCM 16 bits a 48k
	b := NovoBufferDeMidia(capacidade)
	defer b.Fechar()

	// --- GetMaxLength (índice 4) ---
	var maximo uint32
	if r := chamarPelaTabela(b, 4, uintptr(unsafe.Pointer(&maximo))); r != sOK {
		t.Fatalf("GetMaxLength devolveu 0x%X", r)
	}
	if maximo != capacidade {
		t.Fatalf("capacidade veio %d, esperava %d", maximo, capacidade)
	}

	// --- GetBufferAndLength (índice 5), buffer recém-criado ---
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

	// --- O cancelador ESCREVE no endereço e depois declara o tamanho ---
	// É esta a sequência real: ele pega o endereço, escreve, chama SetLength.
	destino := unsafe.Slice((*byte)(unsafe.Pointer(endereco)), capacidade)
	for i := 0; i < 640; i++ {
		destino[i] = byte(i % 251)
	}
	if r := chamarPelaTabela(b, 3, 640); r != sOK { // SetLength
		t.Fatalf("SetLength devolveu 0x%X", r)
	}
	if b.Usado() != 640 {
		t.Fatalf("Usado() = %d depois de SetLength(640)", b.Usado())
	}
	conteudo := b.Conteudo()
	if len(conteudo) != 640 || conteudo[100] != byte(100%251) {
		t.Fatal("o que o Windows escreveu nao chegou ao lado Go")
	}

	// --- SetLength acima da capacidade tem de ser RECUSADO ---
	// Aceitar seria deixar o resto do programa ler alem do fim do vetor.
	if r := chamarPelaTabela(b, 3, capacidade+1); r == sOK {
		t.Fatal("aceitou tamanho maior que a capacidade")
	}

	// --- Zerar prepara a proxima volta ---
	b.Zerar()
	if b.Usado() != 0 {
		t.Fatal("Zerar nao zerou; o buffer encheria e o audio pararia depois de alguns quadros")
	}
}

func TestBufferDeMidiaConsultaInterface(t *testing.T) {
	b := NovoBufferDeMidia(64)
	defer b.Fechar()

	// Pedir IMediaBuffer devolve o proprio objeto.
	var saida uintptr
	r := chamarPelaTabela(b, 0,
		uintptr(unsafe.Pointer(&iidBufferDeMidia)), uintptr(unsafe.Pointer(&saida)))
	if r != sOK || saida != b.Ponteiro() {
		t.Fatalf("QueryInterface(IMediaBuffer) devolveu 0x%X / %v", r, saida == b.Ponteiro())
	}

	// Pedir IUnknown tambem: todo objeto COM precisa responder a ele.
	saida = 0
	if r := chamarPelaTabela(b, 0,
		uintptr(unsafe.Pointer(&iidDesconhecido)), uintptr(unsafe.Pointer(&saida))); r != sOK {
		t.Fatalf("QueryInterface(IUnknown) devolveu 0x%X", r)
	}

	// Pedir qualquer outra coisa tem de RECUSAR e ZERAR a saida. Deixar lixo ali
	// faz quem perguntou usar um endereco aleatorio como se fosse objeto.
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

// A contagem de referencias tem de subir e descer sem soltar nada: quem manda no
// ciclo de vida somos nos, nao o Windows.
func TestBufferDeMidiaContagem(t *testing.T) {
	b := NovoBufferDeMidia(64)
	defer b.Fechar()

	antes := chamarPelaTabela(b, 1) // AddRef
	depois := chamarPelaTabela(b, 2) // Release
	if depois != antes-1 {
		t.Fatalf("AddRef deu %d e Release deu %d", antes, depois)
	}
	// O objeto tem de continuar utilizavel depois do Release.
	var maximo uint32
	if r := chamarPelaTabela(b, 4, uintptr(unsafe.Pointer(&maximo))); r != sOK || maximo != 64 {
		t.Fatal("o objeto parou de responder depois de um Release")
	}
}
