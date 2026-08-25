package main

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

type Saida struct {
	enumerador  objeto
	dispositivo objeto
	cliente     objeto
	tocador     objeto
	evento      windows.Handle

	capacidade uint32
	rodando    bool
}

func AbrirSaida(id string) (*Saida, error) {
	s := &Saida{}
	ok := false
	defer func() {
		if !ok {
			s.Fechar()
		}
	}()

	enumerador, err := criar(&clsidEnumeradorDeDispositivos, &iidEnumeradorDeDispositivos)
	if err != nil {
		return nil, fmt.Errorf("enumerador de áudio: %w", err)
	}
	s.enumerador = enumerador

	dispositivo, err := abrirDispositivo(enumerador, sentidoSaida, id)
	if err != nil {
		return nil, err
	}
	s.dispositivo = dispositivo

	var cliente objeto
	r := dispositivo.chamar(mmDeviceActivate,
		uintptr(unsafe.Pointer(&iidClienteDeAudio)),
		1,
		0,
		uintptr(unsafe.Pointer(&cliente)),
	)
	if err := hr(r, "abrir o alto-falante"); err != nil {
		return nil, err
	}
	s.cliente = cliente

	formato := formatoPCM(TaxaDeAmostragem, CanaisDeVoz)

	duracao := int64(100 * porMilissegundo)
	r = cliente.chamar(acInitialize,
		uintptr(modoCompartilhado),
		uintptr(avisaPorEvento|converteFormato|qualidadePadraoSR),
		uintptr(duracao),
		uintptr(duracao>>32),
		uintptr(unsafe.Pointer(&formato)),
		0,
	)
	if err := hr(r, "configurar a saída"); err != nil {
		return nil, err
	}

	if err := hr(cliente.chamar(acGetBufferSize, uintptr(unsafe.Pointer(&s.capacidade))),
		"consultar o tamanho do buffer"); err != nil {
		return nil, err
	}

	evento, err := windows.CreateEvent(nil, 0, 0, nil)
	if err != nil {
		return nil, fmt.Errorf("criar aviso de saída: %w", err)
	}
	s.evento = evento

	if err := hr(cliente.chamar(acSetEventHandle, uintptr(evento)), "ligar o aviso"); err != nil {
		return nil, err
	}

	var tocador objeto
	r = cliente.chamar(acGetService,
		uintptr(unsafe.Pointer(&iidClienteDeSaida)),
		uintptr(unsafe.Pointer(&tocador)),
	)
	if err := hr(r, "obter o escritor de saída"); err != nil {
		return nil, err
	}
	s.tocador = tocador

	if err := s.Escrever(nil); err != nil {
		return nil, fmt.Errorf("preencher o silêncio inicial: %w", err)
	}

	if err := hr(cliente.chamar(acStart), "iniciar a saída"); err != nil {
		return nil, err
	}
	s.rodando = true

	ok = true
	return s, nil
}

func (s *Saida) EspacoLivre() (uint32, error) {
	var pendente uint32
	if err := hr(s.cliente.chamar(acGetCurrentPadding, uintptr(unsafe.Pointer(&pendente))),
		"consultar o que falta tocar"); err != nil {
		return 0, err
	}
	return s.capacidade - pendente, nil
}

func (s *Saida) Escrever(pcm []int16) error {
	livre, err := s.EspacoLivre()
	if err != nil {
		return err
	}
	if livre == 0 {
		return nil
	}

	quadros := livre
	if pcm != nil {
		disponivel := uint32(len(pcm) / CanaisDeVoz)
		if disponivel < quadros {
			quadros = disponivel
		}
	}
	if quadros == 0 {
		return nil
	}

	var destino unsafe.Pointer
	if err := hr(s.tocador.chamar(renGetBuffer, uintptr(quadros), uintptr(unsafe.Pointer(&destino))),
		"reservar espaço na saída"); err != nil {
		return err
	}

	var bandeiras uintptr
	if pcm == nil {

		bandeiras = blocoSilencioso
	} else {
		n := int(quadros) * CanaisDeVoz
		alvo := unsafe.Slice((*int16)(destino), n)
		copy(alvo, pcm[:n])
	}

	if err := hr(s.tocador.chamar(renReleaseBuffer, uintptr(quadros), bandeiras),
		"entregar o bloco à saída"); err != nil {
		return err
	}
	return nil
}

func (s *Saida) Esperar(limiteMs uint32) error {
	r, err := windows.WaitForSingleObject(s.evento, limiteMs)
	if err != nil {
		return fmt.Errorf("esperar pela saída: %w", err)
	}
	if r == uint32(windows.WAIT_TIMEOUT) {
		return ErrSemAudio
	}
	return nil
}

func (s *Saida) Fechar() {
	if s.rodando {
		s.cliente.chamar(acStop)
		s.rodando = false
	}
	s.tocador.soltar()
	s.tocador = 0
	if s.evento != 0 {
		windows.CloseHandle(s.evento)
		s.evento = 0
	}
	s.cliente.soltar()
	s.cliente = 0
	s.dispositivo.soltar()
	s.dispositivo = 0
	s.enumerador.soltar()
	s.enumerador = 0
}
