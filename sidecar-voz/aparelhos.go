package main

// ESCOLHER MICROFONE E SAÍDA.
//
// O motor sempre usou o aparelho de COMUNICAÇÃO padrão do Windows, que é o certo
// como ponto de partida: é o que a pessoa já escolheu no sistema para conversar. Mas
// "certo como padrão" não é o mesmo que "certo sempre" — quem tem duas placas, ou um
// headset que o Windows não elegeu, precisa poder dizer qual quer.
//
// Isto existe porque a escolha SUMIU quando a voz mudou de casa: o motor antigo
// listava dispositivos e o novo não listava. Era regressão, não simplificação.
//
// A ENUMERAÇÃO É COM PURO, e o caminho tem quatro paradas: o enumerador devolve uma
// coleção, a coleção devolve dispositivos, cada dispositivo devolve o próprio
// identificador e uma loja de propriedades, e é a loja que carrega o nome legível.

import (
	"fmt"
	"os"
	"unsafe"

	"golang.org/x/sys/windows"
)

// DEVICE_STATE_ACTIVE. Só os aparelhos ligados e prontos — listar os desabilitados
// e os desconectados encheria o menu de coisas que não funcionam se escolhidas.
const apenasAtivos = 0x00000001

// Aparelho é um microfone ou uma saída, do jeito que a tela precisa deles.
type Aparelho struct {
	ID   string `json:"id"`
	Nome string `json:"nome"`
}

// ListarNumaThreadPropria enumera numa thread com apartamento COM só dela.
//
// Existe porque quem pede a lista é o laço que lê a ponte, e esse laço NÃO tem COM
// iniciado — só as duas threads de áudio têm, cada uma no próprio apartamento.
// Chamar a enumeração de lá devolveria erro de "COM não inicializado", que é o tipo
// de falha que só aparece em runtime e confunde.
//
// Uma thread por consulta é barato: isto acontece quando alguém abre as
// configurações da call, não a cada quadro de áudio.
func ListarNumaThreadPropria(sentido int) ([]Aparelho, error) {
	type resposta struct {
		lista []Aparelho
		err   error
	}
	pronto := make(chan resposta, 1)
	go func() {
		defer PrenderNaThread()()
		if err := abrirCOM(); err != nil {
			pronto <- resposta{err: fmt.Errorf("iniciar COM: %w", err)}
			return
		}
		defer fecharCOM()
		lista, err := ListarAparelhos(sentido)
		pronto <- resposta{lista: lista, err: err}
	}()
	r := <-pronto
	return r.lista, r.err
}

// ListarAparelhos devolve os aparelhos de um sentido (entrada ou saída).
//
// Exige COM já iniciado na thread que chama — como todo o resto deste arquivo.
func ListarAparelhos(sentido int) ([]Aparelho, error) {
	enumerador, err := criar(&clsidEnumeradorDeDispositivos, &iidEnumeradorDeDispositivos)
	if err != nil {
		return nil, fmt.Errorf("enumerador de áudio: %w", err)
	}
	defer enumerador.soltar()

	var colecao objeto
	r := enumerador.chamar(mmEnumAudioEndpoints,
		uintptr(sentido),
		uintptr(apenasAtivos),
		uintptr(unsafe.Pointer(&colecao)),
	)
	if err := hr(r, "listar aparelhos"); err != nil {
		return nil, err
	}
	defer colecao.soltar()

	var quantos uint32
	r = colecao.chamar(colContar, uintptr(unsafe.Pointer(&quantos)))
	if err := hr(r, "contar aparelhos"); err != nil {
		return nil, err
	}

	lista := make([]Aparelho, 0, quantos)
	for i := uint32(0); i < quantos; i++ {
		var dispositivo objeto
		r = colecao.chamar(colItem, uintptr(i), uintptr(unsafe.Pointer(&dispositivo)))
		if hr(r, "pegar aparelho") != nil {
			// Um aparelho que falha não invalida os outros: some da lista e o resto
			// continua utilizável. Melhor um menu com quatro dos cinco do que erro.
			continue
		}
		id, nome := descreverAparelho(dispositivo)
		dispositivo.soltar()
		if id != "" {
			lista = append(lista, Aparelho{ID: id, Nome: nome})
		}
	}
	return lista, nil
}

// descreverAparelho tira do dispositivo o identificador e o nome legível.
func descreverAparelho(dispositivo objeto) (id, nome string) {
	var ptrID uintptr
	if hr(dispositivo.chamar(mmDeviceGetId, uintptr(unsafe.Pointer(&ptrID))), "id do aparelho") != nil {
		return "", ""
	}
	// O Windows alocou esta string com o alocador do COM; devolver com o mesmo é
	// obrigação, não boa vontade.
	defer liberarMemoriaDoCOM(ptrID)
	id = windows.UTF16PtrToString((*uint16)(unsafe.Pointer(ptrID)))

	var loja objeto
	const somenteLeitura = 0 // STGM_READ
	if hr(dispositivo.chamar(mmDeviceAbrirLoja,
		uintptr(somenteLeitura),
		uintptr(unsafe.Pointer(&loja)),
	), "abrir propriedades") != nil {
		// Sem nome ainda dá para usar o aparelho — mostra o identificador, que é
		// feio mas verdadeiro, em vez de esconder o aparelho da lista.
		return id, id
	}
	defer loja.soltar()

	var valor propvariant
	if hr(loja.chamar(lojaLer,
		uintptr(unsafe.Pointer(&chaveNomeAmigavel)),
		uintptr(unsafe.Pointer(&valor)),
	), "ler nome do aparelho") != nil {
		return id, id
	}
	if valor.tipo != tipoTextoLargo || valor.ponteiro == 0 {
		return id, id
	}
	defer liberarMemoriaDoCOM(valor.ponteiro)
	return id, windows.UTF16PtrToString((*uint16)(unsafe.Pointer(valor.ponteiro)))
}

// abrirDispositivo devolve o aparelho pedido, ou o padrão de comunicação quando o
// pedido é vazio.
//
// CAIR NO PADRÃO QUANDO O PEDIDO FALHA é deliberado. O identificador guardado nas
// preferências aponta para um aparelho que pode ter sido desconectado desde a última
// vez — headset USB tirado da porta é o caso comum. Sem esta queda, a call abriria
// muda porque a pessoa desplugou um fone semana passada.
func abrirDispositivo(enumerador objeto, sentido int, id string) (objeto, error) {
	if id != "" {
		alvo, err := windows.UTF16PtrFromString(id)
		if err == nil {
			var dispositivo objeto
			r := enumerador.chamar(_mmGetDevice,
				uintptr(unsafe.Pointer(alvo)),
				uintptr(unsafe.Pointer(&dispositivo)),
			)
			if hr(r, "abrir aparelho escolhido") == nil {
				return dispositivo, nil
			}
		}
		fmt.Fprintf(os.Stderr, "aparelho escolhido indisponível (%s); usando o padrão\n", id)
	}

	var dispositivo objeto
	r := enumerador.chamar(mmGetDefaultAudioEndpoint,
		uintptr(sentido),
		uintptr(papelComunicacao),
		uintptr(unsafe.Pointer(&dispositivo)),
	)
	if err := hr(r, "pegar aparelho padrão"); err != nil {
		return 0, err
	}
	return dispositivo, nil
}
