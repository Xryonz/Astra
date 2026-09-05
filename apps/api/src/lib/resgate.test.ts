import { describe, it, expect, beforeEach, vi } from 'vitest'
import { resgatar } from './missoes'
import { creditarXpDeMissao } from './xp'

const linhasDoUpdate: { missionId: string }[][] = []
const returning = vi.fn(async () => linhasDoUpdate.shift() ?? [])
const where = vi.fn(() => ({ returning }))
const set = vi.fn(() => ({ where }))
const update = vi.fn(() => ({ set }))

vi.mock('../db', () => ({ db: { update: (...a: unknown[]) => update(...a as []) } }))
vi.mock('./realtime', () => ({ missaoConcluida: vi.fn() }))
vi.mock('./redis', () => ({ redis: { sadd: vi.fn(), expire: vi.fn(), incrby: vi.fn(), set: vi.fn() } }))
vi.mock('./logger', () => ({ logger: { error: vi.fn(), info: vi.fn() } }))
vi.mock('./xp', async () => {
  const real = await vi.importActual<typeof import('./xp')>('./xp')
  return { ...real, creditarXpDeMissao: vi.fn(async () => null) }
})

const creditou = vi.mocked(creditarXpDeMissao)

const ALGUEM = 'user_abc123'
const UMA_CONQUISTA = 'c.primeira'
const XP_DA_CONQUISTA = 50

beforeEach(() => {
  linhasDoUpdate.length = 0
  creditou.mockClear()
  update.mockClear()
})

describe('resgate manual', () => {
  it('missao que nao existe no catalogo nao chega no banco', async () => {
    expect(await resgatar(ALGUEM, 'd.inventada')).toBeNull()
    expect(update).not.toHaveBeenCalled()
    expect(creditou).not.toHaveBeenCalled()
  })

  it('primeira vez: marca a linha e credita o xp da missao', async () => {
    linhasDoUpdate.push([{ missionId: UMA_CONQUISTA }])
    const feito = await resgatar(ALGUEM, UMA_CONQUISTA)
    expect(feito).toEqual({
      id: UMA_CONQUISTA,
      titulo: 'A primeira mensagem',
      xp: XP_DA_CONQUISTA,
      tipo: 'conquista',
    })
    expect(creditou).toHaveBeenCalledExactlyOnceWith(ALGUEM, XP_DA_CONQUISTA)
  })

  it('segunda vez NAO paga: o update nao pega linha e o credito nem e chamado', async () => {
    linhasDoUpdate.push([])
    expect(await resgatar(ALGUEM, UMA_CONQUISTA)).toBeNull()
    expect(update).toHaveBeenCalledOnce()
    expect(creditou).not.toHaveBeenCalled()
  })

  it('missao concluida mas ainda nao resgatada paga uma vez so, mesmo com dois pedidos', async () => {
    linhasDoUpdate.push([{ missionId: UMA_CONQUISTA }], [])
    const [a, b] = [await resgatar(ALGUEM, UMA_CONQUISTA), await resgatar(ALGUEM, UMA_CONQUISTA)]
    expect(a).not.toBeNull()
    expect(b).toBeNull()
    expect(creditou).toHaveBeenCalledOnce()
  })

  it('o xp pago e o do catalogo, nunca o que o cliente pedir', async () => {
    linhasDoUpdate.push([{ missionId: 'c.msg1000' }])
    const feito = await resgatar(ALGUEM, 'c.msg1000')
    expect(feito?.xp).toBe(800)
    expect(creditou).toHaveBeenCalledExactlyOnceWith(ALGUEM, 800)
  })
})
