import { describe, it, expect } from 'vitest'
import { CreateServerSchema } from '@astra/types'

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
