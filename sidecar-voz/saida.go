package main

// SAÍDA DE SOM por WASAPI — o outro lado da captura.
//
// A diferença de fundo entre os dois: na captura, quem manda no ritmo é o
// aparelho, e nós corremos atrás. Aqui, quem tem que estar sempre à frente somos
// nós — se o buffer esvaziar antes de escrevermos, o som falha, e falha de saída é
// audível na hora. Por isso a saída trabalha com uma folga proposital e escreve
// silêncio quando não há nada a dizer, em vez de simplesmente parar.

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

// Saida é o alto-falante aberto.
type Saida struct {
	enumerador  objeto
	dispositivo objeto
	cliente     objeto
	tocador     objeto
	evento      windows.Handle
	// Total de quadros que cabem no buffer do aparelho. Precisamos dele para saber
	// quanto espaço sobrou (total menos o que ainda não tocou).
	capacidade uint32
	rodando    bool
}

// AbrirSaida prepara o alto-falante de comunicação padrão do sistema.
//
// Mesma exigência da captura: chamar e usar na MESMA thread, presa com
// PrenderNaThread.
func AbrirSaida() (*Saida, error) {
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

	var dispositivo objeto
	r := enumerador.chamar(mmGetDefaultAudioEndpoint,
		uintptr(sentidoSaida),
		uintptr(papelComunicacao),
		uintptr(unsafe.Pointer(&dispositivo)),
	)
	if err := hr(r, "pegar alto-falante padrão"); err != nil {
		return nil, err
	}
	s.dispositivo = dispositivo

	var cliente objeto
	r = dispositivo.chamar(mmDeviceActivate,
		uintptr(unsafe.Pointer(&iidClienteDeAudio)),
		1, // CLSCTX_INPROC_SERVER
		0,
		uintptr(unsafe.Pointer(&cliente)),
	)
	if err := hr(r, "abrir o alto-falante"); err != nil {
		return nil, err
	}
	s.cliente = cliente

	formato := formatoPCM(TaxaDeAmostragem, CanaisDeVoz)
	// 100ms de buffer. Menor que o da captura de propósito: aqui o buffer é
	// LATÊNCIA que a pessoa ouve, não folga de segurança. Cem milissegundos é o
	// ponto em que a conversa ainda parece imediata e ainda há margem para uma
	// pausa do escalonador.
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

	// O tamanho REAL do buffer não é o que pedimos: o Windows arredonda para o
	// período do aparelho. Usar o valor pedido em vez do concedido é como se
	// escreve estouro de buffer — daí perguntar em vez de assumir.
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

	// SILÊNCIO ANTES DE COMEÇAR. Iniciar com o buffer vazio produz um estalo, ou o
	// resto do que estava ali. Encher de zeros primeiro é o que faz a call começar
	// em silêncio limpo.
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

// EspacoLivre diz quantos quadros cabem agora.
func (s *Saida) EspacoLivre() (uint32, error) {
	var pendente uint32
	if err := hr(s.cliente.chamar(acGetCurrentPadding, uintptr(unsafe.Pointer(&pendente))),
		"consultar o que falta tocar"); err != nil {
		return 0, err
	}
	return s.capacidade - pendente, nil
}

// Escrever entrega amostras ao alto-falante. `pcm` nulo ou curto demais preenche o
// resto com silêncio.
//
// Escreve TUDO que couber, não só um quadro: se o buffer esvaziou porque a máquina
// engasgou, encher de uma vez é o que recupera sem falhar de novo no quadro
// seguinte.
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

	// Ponteiro de verdade, não uintptr — ver o mesmo ponto em `captura.go`.
	var destino unsafe.Pointer
	if err := hr(s.tocador.chamar(renGetBuffer, uintptr(quadros), uintptr(unsafe.Pointer(&destino))),
		"reservar espaço na saída"); err != nil {
		return err
	}

	var bandeiras uintptr
	if pcm == nil {
		// Em vez de escrever zeros à mão, avisa que é silêncio: o Windows preenche,
		// e economiza uma cópia de buffer inteiro toda vez que ninguém fala — o que
		// numa call é a maior parte do tempo.
		bandeiras = blocoSilencioso
	} else {
		n := int(quadros) * CanaisDeVoz
		alvo := unsafe.Slice((*int16)(destino), n)
		copy(alvo, pcm[:n])
	}

	// Devolver com a MESMA contagem reservada, sempre — inclusive em caso de erro
	// no meio. Um GetBuffer sem ReleaseBuffer trava a saída para sempre.
	if err := hr(s.tocador.chamar(renReleaseBuffer, uintptr(quadros), bandeiras),
		"entregar o bloco à saída"); err != nil {
		return err
	}
	return nil
}

// Esperar dorme até haver espaço no buffer de saída.
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

// Fechar solta tudo, na ordem inversa da abertura.
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
