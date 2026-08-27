package main

import (
	"fmt"
	"os"
	"unsafe"

	"golang.org/x/sys/windows"
)

const apenasAtivos = 0x00000001

type Aparelho struct {
	ID   string `json:"id"`
	Nome string `json:"nome"`
}

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

func descreverAparelho(dispositivo objeto) (id, nome string) {
	var ptrID uintptr
	if hr(dispositivo.chamar(mmDeviceGetId, uintptr(unsafe.Pointer(&ptrID))), "id do aparelho") != nil {
		return "", ""
	}

	defer liberarMemoriaDoCOM(ptrID)
	id = windows.UTF16PtrToString((*uint16)(unsafe.Pointer(ptrID)))

	var loja objeto
	const somenteLeitura = 0
	if hr(dispositivo.chamar(mmDeviceAbrirLoja,
		uintptr(somenteLeitura),
		uintptr(unsafe.Pointer(&loja)),
	), "abrir propriedades") != nil {

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

func aparelhoEstaVivo(dispositivo objeto) bool {
	var estado uint32
	if hr(dispositivo.chamar(mmDeviceGetSt, uintptr(unsafe.Pointer(&estado))), "estado do aparelho") != nil {
		return false
	}
	return estado&apenasAtivos != 0
}

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
				if aparelhoEstaVivo(dispositivo) {
					return dispositivo, nil
				}
				dispositivo.soltar()
			}
		}
		fmt.Fprintf(os.Stderr, "aparelho escolhido indisponível (%s); usando o padrão\n", id)
	}

	for _, papel := range []int{papelComunicacao, papelConsole} {
		var dispositivo objeto
		r := enumerador.chamar(mmGetDefaultAudioEndpoint,
			uintptr(sentido),
			uintptr(papel),
			uintptr(unsafe.Pointer(&dispositivo)),
		)
		if hr(r, "pegar aparelho padrão") != nil {
			continue
		}
		if aparelhoEstaVivo(dispositivo) {
			return dispositivo, nil
		}
		dispositivo.soltar()
		fmt.Fprintf(os.Stderr, "padrão do papel %d não está ativo; tentando o seguinte\n", papel)
	}
	return 0, fmt.Errorf("nenhum aparelho de áudio ativo para este sentido")
}
