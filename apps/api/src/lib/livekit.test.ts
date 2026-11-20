import { describe, it, expect, beforeEach, vi } from 'vitest'
import { TwirpError } from 'livekit-server-sdk'

const h = vi.hoisted(() => ({
  env: {} as Record<string, string | undefined>,
  salas: [] as Array<{ id: string }>,
  consultas: 0,
  remover: vi.fn(),
}))

vi.mock('./env', () => ({ env: h.env }))
vi.mock('./logger', () => ({ logger: { error: vi.fn() } }))
vi.mock('livekit-server-sdk', async (importOriginal) => {
  const real = await importOriginal<typeof import('livekit-server-sdk')>()
  return {
    ...real,
    RoomServiceClient: class {
      removeParticipant = h.remover
    },
  }
})
vi.mock('../db', () => ({
  db: {
    select: () => ({
      from: () => ({
        where: async () => {
          h.consultas++
          return h.salas
        },
      }),
    }),
  },
}))

import { tirarDasSalasDeVoz } from './livekit'
import { logger } from './logger'

describe('quem perde o acesso sai da chamada', () => {
  beforeEach(() => {
    Object.assign(h.env, {
      LIVEKIT_URL: 'https://astra-teste.livekit.cloud',
      LIVEKIT_API_KEY: 'chave',
      LIVEKIT_API_SECRET: 'segredo',
    })
    h.salas = [{ id: 'v1' }, { id: 'v2' }]
    h.consultas = 0
    h.remover.mockReset()
    h.remover.mockResolvedValue(undefined)
    vi.mocked(logger.error).mockClear()
  })

  it('sai de cada sala de voz da constelacao, e as credenciais emitidas ate agora deixam de valer', async () => {
    const antes = BigInt(Math.floor(Date.now() / 1000))
    await tirarDasSalasDeVoz('u9', 'srv1')

    expect(h.remover).toHaveBeenCalledTimes(2)
    expect(h.remover.mock.calls.map((chamada) => [chamada[0], chamada[1]])).toEqual([
      ['channel:v1', 'u9'],
      ['channel:v2', 'u9'],
    ])
    const corte = h.remover.mock.calls[0][2]?.revokeTokenTs
    expect(typeof corte).toBe('bigint')
    expect(corte >= antes).toBe(true)
  })

  it('quem nao estava na sala nao vira erro; falha de verdade vira log e nao derruba quem chamou', async () => {
    h.remover.mockImplementation(async (sala: string) => {
      if (sala === 'channel:v1') throw new TwirpError('TwirpError', 'participant not found', 404, 'not_found')
      throw new Error('LiveKit fora do ar')
    })

    await expect(tirarDasSalasDeVoz('u9', 'srv1')).resolves.toBeUndefined()
    expect(logger.error).toHaveBeenCalledTimes(1)
  })

  it('sem LiveKit configurado, nem consulta o banco', async () => {
    h.env.LIVEKIT_URL = undefined
    await tirarDasSalasDeVoz('u9', 'srv1')
    expect(h.consultas).toBe(0)
    expect(h.remover).not.toHaveBeenCalled()
  })
})
