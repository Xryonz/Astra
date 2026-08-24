import { describe, it, expect } from 'vitest'
import { CreateServerSchema } from '@astra/types'

// POR QUE A ROTA DE CRIAÇÃO PRECISA DAS MESMAS TRAVAS DO PATCH.
//
// Este arquivo não testa a rota (isso exigiria banco e bucket): testa a PREMISSA que fez o
// buraco existir, e que é justamente a parte contraintuitiva.
//
// A rota de criação confiava no `CreateServerSchema` para barrar coisa estranha em
// `iconUrl`, e o campo é `z.string().url()`. Parece suficiente. Não é — `url()` aceita
// data-URI, porque `new URL('data:image/png;base64,…')` é um endereço válido. Sem limite
// de tamanho no schema, o teto virava o corpo da requisição (16mb), e uma constelação
// podia nascer com megabytes de base64 dentro de uma coluna que é lida com `select()` sem
// projeção — arrastados em toda listagem, para sempre.
//
// Se algum dia o schema passar a barrar isso sozinho, estes testes falham e o comentário
// da rota pode encolher junto. Enquanto falharem, a trava tem de ficar onde está.

describe('CreateServerSchema não é trava suficiente para o ícone', () => {
  it('ACEITA data-URI, ao contrário do que "url()" sugere', () => {
    const r = CreateServerSchema.safeParse({
      name: 'Constelação',
      iconUrl: 'data:image/png;base64,iVBORw0KGgo=',
    })
    expect(r.success).toBe(true)
  })

  it('não impõe teto de tamanho — 4 MB de base64 passam', () => {
    const enorme = 'data:image/png;base64,' + 'A'.repeat(4 * 1024 * 1024)
    const r = CreateServerSchema.safeParse({ name: 'Constelação', iconUrl: enorme })
    expect(r.success).toBe(true)
  })

  it('aceita host de terceiro qualquer — a lista de permitidos vive na rota', () => {
    const r = CreateServerSchema.safeParse({
      name: 'Constelação',
      iconUrl: 'https://exemplo-que-nao-e-nosso.invalid/i.png',
    })
    expect(r.success).toBe(true)
  })
})
