import { describe, it, expect, beforeAll, vi } from 'vitest'
import { eq } from 'drizzle-orm'

vi.mock('../db', async () => {
  const { abrirBancoEmMemoria } = await import('../__tests__/bancoEmMemoria')
  return { db: await abrirBancoEmMemoria(), schema: await import('../db/schema') }
})

import { contaDoGoogle } from './passport'
import { db } from '../db'
import { users } from '../db/schema'

const CONFIRMADO = new Date('2026-11-01T12:00:00Z')

function perfil(id: string, email: string, verificadoNoGoogle = true) {
  return { id, emails: [{ value: email, verified: verificadoNoGoogle }] }
}

async function googleIdDe(userId: string) {
  const [linha] = await db.select({ googleId: users.googleId }).from(users).where(eq(users.id, userId))
  return linha?.googleId ?? null
}

beforeAll(async () => {
  await db.insert(users).values([
    { id: 'u1', email: 'vitima@teste.invalid', username: 'atacante', coordinate: 'c-u1', displayName: 'Atacante' },
    {
      id: 'u2',
      email: 'confirmada@teste.invalid',
      username: 'confirmada',
      coordinate: 'c-u2',
      displayName: 'Confirmada',
      emailVerifiedAt: CONFIRMADO,
    },
    {
      id: 'u3',
      email: 'ligada@teste.invalid',
      username: 'ligada',
      coordinate: 'c-u3',
      displayName: 'Ligada',
      googleId: 'g-3',
    },
    {
      id: 'u4',
      email: 'outra@teste.invalid',
      username: 'outra',
      coordinate: 'c-u4',
      displayName: 'Outra',
      emailVerifiedAt: CONFIRMADO,
    },
  ])
})

describe('entrar com o Google numa conta que já existe', () => {
  it('não liga o Google a uma conta cujo e-mail nunca foi confirmado, que é o sequestro antecipado', async () => {
    const resultado = await contaDoGoogle(perfil('g-1', 'vitima@teste.invalid'))
    expect(resultado).toEqual({ falha: { code: 'email_not_confirmed', email: 'vitima@teste.invalid' } })
    expect(await googleIdDe('u1')).toBeNull()
  })

  it('liga quando o e-mail da conta já foi confirmado', async () => {
    const resultado = await contaDoGoogle(perfil('g-2', 'confirmada@teste.invalid'))
    expect(resultado).toMatchObject({ conta: { id: 'u2' } })
    expect(await googleIdDe('u2')).toBe('g-2')
  })

  it('não liga quando o próprio Google diz que não verificou o e-mail', async () => {
    const resultado = await contaDoGoogle(perfil('g-4', 'outra@teste.invalid', false))
    expect(resultado).toEqual({ falha: { code: 'google_email_unverified', email: 'outra@teste.invalid' } })
    expect(await googleIdDe('u4')).toBeNull()
  })

  it('a conta já ligada continua entrando pelo id do Google', async () => {
    const resultado = await contaDoGoogle(perfil('g-3', 'ligada@teste.invalid'))
    expect(resultado).toMatchObject({ conta: { id: 'u3' } })
  })

  it('e-mail sem cadastro devolve o pedido de criar conta, como antes', async () => {
    const resultado = await contaDoGoogle(perfil('g-5', 'nova@teste.invalid'))
    expect(resultado).toEqual({ falha: { code: 'email_not_registered', email: 'nova@teste.invalid' } })
  })
})
