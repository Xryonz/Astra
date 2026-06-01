import { describe, it, expect, vi } from 'vitest'
import { z } from 'zod'
import { validate } from './validate'

function mkReq(body: any, query: any = {}, params: any = {}) {
  return { body, query, params, method: 'POST', path: '/test' } as any
}
function mkRes() {
  const json   = vi.fn()
  const status = vi.fn().mockReturnValue({ json })
  return { status, json } as any
}

const Schema = z.object({
  name: z.string().min(2),
  age:  z.number().int().positive().optional(),
})

describe('validate middleware', () => {
  it('passa next() com payload válido + substitui body com dados parseados', () => {
    const req = mkReq({ name: 'Joao', age: 30, extra: 'discarded' })
    const res = mkRes()
    const next = vi.fn()

    validate(Schema)(req, res, next)

    expect(next).toHaveBeenCalledTimes(1)
    expect(res.status).not.toHaveBeenCalled()
    // Zod descarta o `extra` por default → req.body sanitizado
    expect(req.body).toEqual({ name: 'Joao', age: 30 })
  })

  it('responde 400 com array de erros quando inválido', () => {
    const req = mkReq({ name: 'a', age: -1 })
    const res = mkRes()
    const next = vi.fn()

    validate(Schema)(req, res, next)

    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
    const payload = res.status.mock.results[0].value.json.mock.calls[0][0]
    expect(payload.error).toBe('Dados inválidos')
    expect(Array.isArray(payload.details)).toBe(true)
    expect(payload.details.length).toBeGreaterThanOrEqual(2) // name min2 + age positive
  })

  it('source=query valida req.query', () => {
    const QSchema = z.object({ q: z.string().min(1) })
    const req = mkReq({}, { q: '' })
    const res = mkRes()
    const next = vi.fn()

    validate(QSchema, 'query')(req, res, next)

    expect(next).not.toHaveBeenCalled()
    expect(res.status).toHaveBeenCalledWith(400)
  })

  it('source=params valida req.params', () => {
    const PSchema = z.object({ id: z.string().uuid() })
    const req = mkReq({}, {}, { id: 'not-uuid' })
    const res = mkRes()
    const next = vi.fn()

    validate(PSchema, 'params')(req, res, next)

    expect(res.status).toHaveBeenCalledWith(400)
  })

  it('erro não-Zod (raro) é repassado pro next sem virar 400', () => {
    const Throwing = {
      parse: () => { throw new Error('boom') },
      safeParse: () => ({ success: false, error: new Error('boom') } as any),
    } as any
    const req = mkReq({})
    const res = mkRes()
    const next = vi.fn()

    validate(Throwing)(req, res, next)

    expect(next).toHaveBeenCalledWith(expect.any(Error))
    expect(res.status).not.toHaveBeenCalled()
  })
})
