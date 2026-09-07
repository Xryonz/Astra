import { describe, it, expect, beforeEach, vi } from 'vitest'
import { redis } from './redis'
import { fichaDoCanal, esquecerFichaDoCanal } from './fichaDoCanal'

const banco = vi.hoisted(() => ({
  idas: 0,
  linhas: [] as Array<{ serverId: string | null; isPrivate: boolean | null }>,
}))

vi.mock('../db', () => ({
  db: {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: async () => {
            banco.idas++
            return banco.linhas
          },
        }),
      }),
    }),
  },
}))

const CANAL = 'ch_publico'
const CONSTELACAO = 'srv_1'

describe('ficha do canal', () => {
  beforeEach(async () => {
    await redis.flushall()
    banco.idas = 0
    banco.linhas = [{ serverId: CONSTELACAO, isPrivate: false }]
  })

  it('a primeira leitura vai ao banco e a segunda nao', async () => {
    expect(await fichaDoCanal(CANAL)).toEqual({ serverId: CONSTELACAO, isPrivate: false })
    expect(banco.idas).toBe(1)

    expect(await fichaDoCanal(CANAL)).toEqual({ serverId: CONSTELACAO, isPrivate: false })
    expect(banco.idas).toBe(1)
  })

  it('esquecer manda a proxima leitura de volta ao banco', async () => {
    await fichaDoCanal(CANAL)
    expect(banco.idas).toBe(1)

    await esquecerFichaDoCanal(CANAL)

    await fichaDoCanal(CANAL)
    expect(banco.idas).toBe(2)
  })

  it('canal que virou fechado passa a responder fechado depois de esquecido', async () => {
    expect((await fichaDoCanal(CANAL))?.isPrivate).toBe(false)

    banco.linhas = [{ serverId: CONSTELACAO, isPrivate: true }]
    expect((await fichaDoCanal(CANAL))?.isPrivate).toBe(false)

    await esquecerFichaDoCanal(CANAL)
    expect((await fichaDoCanal(CANAL))?.isPrivate).toBe(true)
  })

  it('canal fechado atravessa o cache como fechado', async () => {
    banco.linhas = [{ serverId: CONSTELACAO, isPrivate: true }]
    expect(await fichaDoCanal(CANAL)).toEqual({ serverId: CONSTELACAO, isPrivate: true })
    expect(await fichaDoCanal(CANAL)).toEqual({ serverId: CONSTELACAO, isPrivate: true })
    expect(banco.idas).toBe(1)
  })

  it('canal que nao existe responde nulo e nao repete a consulta', async () => {
    banco.linhas = []
    expect(await fichaDoCanal('ch_inventado')).toBeNull()
    expect(await fichaDoCanal('ch_inventado')).toBeNull()
    expect(banco.idas).toBe(1)
  })

  it('linha sem constelacao conta como canal inexistente', async () => {
    banco.linhas = [{ serverId: null, isPrivate: false }]
    expect(await fichaDoCanal(CANAL)).toBeNull()
  })

  it('identificador vazio nao chega ao banco', async () => {
    expect(await fichaDoCanal('')).toBeNull()
    expect(banco.idas).toBe(0)
  })

  it('lixo guardado no cache cai de volta no banco em vez de explodir', async () => {
    await redis.set(`canal:ficha:${CANAL}`, 'nao sou json')
    expect(await fichaDoCanal(CANAL)).toEqual({ serverId: CONSTELACAO, isPrivate: false })
    expect(banco.idas).toBe(1)
  })

  it('ficha guardada sem constelacao nao e aceita', async () => {
    await redis.set(`canal:ficha:${CANAL}`, JSON.stringify({ isPrivate: true }))
    expect(await fichaDoCanal(CANAL)).toEqual({ serverId: CONSTELACAO, isPrivate: false })
    expect(banco.idas).toBe(1)
  })
})
