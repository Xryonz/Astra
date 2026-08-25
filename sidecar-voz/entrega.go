package main

import (
	"encoding/binary"
	"fmt"
	"net"
	"os"
	"sync"
	"time"
)

const (
	marcaDoQuadro     = 0x56545341
	cabecalhoDoQuadro = 24
)

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

func NovaEntrega() *EntregaDeQuadros {
	endereco := os.Getenv("ASTRA_QUADROS")
	if endereco == "" {
		return nil
	}
	e := &EntregaDeQuadros{
		endereco: endereco,
		segredo:  os.Getenv("ASTRA_QUADROS_SEGREDO"),

		fila:  make(chan *quadroPronto, 2),
		parar: make(chan struct{}),
	}
	e.pool.New = func() any { return &quadroPronto{} }
	go e.servir()
	return e
}

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

				c, err := e.ligar()
				if err != nil {
					fmt.Fprintf(os.Stderr, "cano de quadros indisponível: %v\n", err)
					e.pool.Put(p)

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
