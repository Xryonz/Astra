package main

// A ENTREGA DOS QUADROS — como a imagem atravessa do processo de voz para o Astra.
//
// O PROBLEMA: a ponte que já existe (stdin/stdout, JSON por linha) carrega comandos e
// avisos, coisas de dezenas de bytes. Um quadro de 720p em NV12 são 1,4 MB, e a 30 por
// segundo isso são 40 MB/s. Passar isso por JSON exigiria base64 — mais um terço de
// tamanho — e faria a mesma fila que carrega "mudo" e "quem está falando" competir com
// dezenas de megabytes por segundo. O aviso de fala chegaria atrasado por causa da
// imagem.
//
// A ESCOLHA: um cano à parte, em TCP na volta local.
//
// Foi entre isto e memória compartilhada nomeada, que é o caminho "certo" do livro:
// zero cópia, o Go escreve e a JVM lê o mesmo endereço. Ela perde aqui por três razões
// concretas, e não por preguiça:
//
//  1. A JVM não abre uma seção nomeada do Windows com biblioteca padrão. Precisaria de
//     JNA (que o projeto tem) e de mais uma superfície nativa — no processo cujo motivo
//     de existir é NÃO ter superfície nativa na JVM.
//  2. Escrever e ler o mesmo endereço de dois processos pede um protocolo de leitura
//     consistente (contador antes e depois, para pegar leitura rasgada). É correto e é
//     código sutil, do tipo que falha uma vez a cada mil quadros.
//  3. O ganho é uma cópia de memória. 40 MB/s de `memcpy` custa fração de por cento de
//     um núcleo — abaixo do ruído da medição.
//
// Na volta local o TCP não passa por placa de rede nenhuma; é cópia de memória com
// contabilidade do sistema. Em troca vem enquadramento pronto, fluxo garantido em ordem,
// e desligamento limpo quando qualquer um dos dois lados morre — as três coisas que a
// memória compartilhada obrigaria a escrever à mão.
//
// QUEM ESCUTA É O ASTRA, e este processo é quem liga. Ao contrário do que parece
// natural (o dono do dado abre a porta), e a razão é uma corrida: se este processo
// abrisse a porta, ele teria de ANUNCIAR o número dela, e o Astra teria de estar
// ouvindo o anúncio antes de ele sair. Com o Astra escutando, o endereço vem pronto na
// variável de ambiente, antes de este processo existir. Não há o que perder.
//
// E VEM UM SEGREDO JUNTO. A volta local não é privada: qualquer programa da máquina
// pode se conectar numa porta de escuta. O que passa por aqui é a TELA DA PESSOA. O
// segredo é sorteado pelo Astra a cada execução e viaja pelo ambiente, que só os dois
// enxergam.

import (
	"encoding/binary"
	"fmt"
	"net"
	"os"
	"sync"
	"time"
)

// O cabeçalho de cada quadro. Fixo, e o tamanho do id do par vem dentro dele — assim o
// outro lado sabe quanto ler sem nunca precisar adivinhar.
//
//	0  uint32  marca ('ASTV')
//	4  uint32  bytes do id do par
//	8  uint32  largura
//	12 uint32  altura
//	16 uint32  passo (bytes por linha; pode ser MAIOR que a largura)
//	20 uint32  bytes do quadro
//	24 [..]    id do par, em UTF-8
//	   [..]    o quadro, em NV12
const (
	marcaDoQuadro   = 0x56545341 // 'ASTV' lido como número pequeno-endian
	cabecalhoDoQuadro = 24
)

// EntregaDeQuadros leva quadros decodificados até o Astra.
type EntregaDeQuadros struct {
	endereco string
	segredo  string

	fila chan *quadroPronto
	pool sync.Pool

	umaVez sync.Once
	parar  chan struct{}
}

type quadroPronto struct {
	par   string
	bytes []byte
	l, a  int
	passo int
}

// NovaEntrega prepara o cano. Devolve nil quando o Astra não pediu quadros — que é o
// caso de rodar este processo à mão, e não é erro.
func NovaEntrega() *EntregaDeQuadros {
	endereco := os.Getenv("ASTRA_QUADROS")
	if endereco == "" {
		return nil
	}
	e := &EntregaDeQuadros{
		endereco: endereco,
		segredo:  os.Getenv("ASTRA_QUADROS_SEGREDO"),
		// FILA CURTA DE PROPÓSITO. Vídeo não é áudio: quadro atrasado não tem valor
		// nenhum, porque já existe um mais novo. Uma fila longa só acrescentaria atraso
		// entre o que está na tela de quem transmite e o que aparece na de quem assiste
		// — e depois entregaria os dois de uma vez.
		fila:  make(chan *quadroPronto, 2),
		parar: make(chan struct{}),
	}
	e.pool.New = func() any { return &quadroPronto{} }
	go e.servir()
	return e
}

// Mandar entrega um quadro, COPIANDO os bytes.
//
// A cópia é obrigatória: o quadro que o descompressor devolve é o buffer interno dele, e
// vale só até o próximo. Guardar a fatia daria um quadro montado com os bytes do
// seguinte.
//
// NUNCA BLOQUEIA. É chamada de dentro do laço que lê a rede, e travar ali pararia de
// consumir pacotes RTP — que não é "vídeo lento", é conexão entupindo. Com a fila cheia
// o quadro é DESCARTADO, e descartar é a decisão certa: quem assiste quer o quadro de
// agora, não a fila do que já passou.
func (e *EntregaDeQuadros) Mandar(par string, q Quadro) {
	if e == nil || len(q.Dados) == 0 {
		return
	}
	p := e.pool.Get().(*quadroPronto)
	if cap(p.bytes) < len(q.Dados) {
		p.bytes = make([]byte, len(q.Dados))
	}
	p.bytes = p.bytes[:len(q.Dados)]
	copy(p.bytes, q.Dados)
	p.par, p.l, p.a, p.passo = par, q.Largura, q.Altura, q.Passo

	select {
	case e.fila <- p:
	default:
		e.pool.Put(p)
	}
}

func (e *EntregaDeQuadros) Fechar() {
	if e == nil {
		return
	}
	e.umaVez.Do(func() { close(e.parar) })
}

// servir mantém a ligação e escreve o que chega na fila.
//
// UMA GOROUTINE SÓ, e ela é a única que toca a conexão: escrita concorrente em TCP
// intercala bytes no meio de um cabeçalho, e o outro lado passa a ler tamanho de quadro
// onde havia pixel. Como o laço de rede já entrega pela fila, isso sai de graça.
func (e *EntregaDeQuadros) servir() {
	var con net.Conn
	defer func() {
		if con != nil {
			con.Close()
		}
	}()

	for {
		select {
		case <-e.parar:
			return
		case p := <-e.fila:
			if con == nil {
				// LIGA SÓ QUANDO HÁ O QUE ENTREGAR. Ninguém transmitindo é o caso
				// comum de uma call, e abrir conexão para ficar parada seria gastar um
				// descritor e uma porta por nada.
				c, err := e.ligar()
				if err != nil {
					fmt.Fprintf(os.Stderr, "cano de quadros indisponível: %v\n", err)
					e.pool.Put(p)
					// Espera antes de tentar de novo: sem isso, cada quadro
					// descartado viraria uma tentativa de conexão, trinta por segundo.
					select {
					case <-e.parar:
						return
					case <-time.After(2 * time.Second):
					}
					continue
				}
				con = c
			}
			if err := e.escrever(con, p); err != nil {
				fmt.Fprintf(os.Stderr, "cano de quadros caiu: %v\n", err)
				con.Close()
				con = nil
			}
			e.pool.Put(p)
		}
	}
}

func (e *EntregaDeQuadros) ligar() (net.Conn, error) {
	con, err := net.DialTimeout("tcp", e.endereco, 3*time.Second)
	if err != nil {
		return nil, err
	}
	// O SEGREDO PRIMEIRO, antes de qualquer pixel. Quem não o apresenta é desligado do
	// outro lado sem receber nada.
	if _, err := con.Write([]byte(e.segredo + "\n")); err != nil {
		con.Close()
		return nil, fmt.Errorf("apresentar o segredo: %w", err)
	}
	return con, nil
}

func (e *EntregaDeQuadros) escrever(con net.Conn, p *quadroPronto) error {
	par := []byte(p.par)
	var cab [cabecalhoDoQuadro]byte
	binary.LittleEndian.PutUint32(cab[0:], marcaDoQuadro)
	binary.LittleEndian.PutUint32(cab[4:], uint32(len(par)))
	binary.LittleEndian.PutUint32(cab[8:], uint32(p.l))
	binary.LittleEndian.PutUint32(cab[12:], uint32(p.a))
	binary.LittleEndian.PutUint32(cab[16:], uint32(p.passo))
	binary.LittleEndian.PutUint32(cab[20:], uint32(len(p.bytes)))

	// PRAZO EM CADA ESCRITA. Sem ele, um Astra travado (o depurador parado num ponto de
	// parada, a máquina em suspensão) deixaria esta goroutine pendurada para sempre
	// segurando o quadro e a fila. Com prazo, a ligação cai e é refeita.
	if err := con.SetWriteDeadline(time.Now().Add(2 * time.Second)); err != nil {
		return err
	}
	if _, err := con.Write(cab[:]); err != nil {
		return err
	}
	if len(par) > 0 {
		if _, err := con.Write(par); err != nil {
			return err
		}
	}
	_, err := con.Write(p.bytes)
	return err
}
