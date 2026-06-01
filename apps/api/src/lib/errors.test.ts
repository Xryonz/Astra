import { describe, it, expect } from 'vitest'
import {
  HttpError, badRequest, unauthorized, forbidden, notFound,
  conflict, tooLarge, unprocessable, rateLimited,
} from './errors'

describe('HttpError', () => {
  it('keeps status, message, code, meta', () => {
    const e = new HttpError(418, 'I am a teapot', 'TEAPOT', { brewing: true })
    expect(e.status).toBe(418)
    expect(e.message).toBe('I am a teapot')
    expect(e.code).toBe('TEAPOT')
    expect(e.meta).toEqual({ brewing: true })
    expect(e.name).toBe('HttpError')
  })

  it('is an instance of Error (catchable by generic handlers)', () => {
    const e = new HttpError(400, 'bad')
    expect(e instanceof Error).toBe(true)
    expect(e instanceof HttpError).toBe(true)
  })

  it('code and meta are optional', () => {
    const e = new HttpError(500, 'oops')
    expect(e.code).toBeUndefined()
    expect(e.meta).toBeUndefined()
  })
})

describe('helpers — produz status correto', () => {
  const cases: Array<[() => HttpError, number]> = [
    [() => badRequest('x'),    400],
    [() => unauthorized(),     401],
    [() => forbidden(),        403],
    [() => notFound(),         404],
    [() => conflict('dup'),    409],
    [() => tooLarge('big'),    413],
    [() => unprocessable('x'), 422],
    [() => rateLimited(),      429],
  ]
  for (const [fn, expected] of cases) {
    it(`status ${expected}`, () => {
      expect(fn().status).toBe(expected)
    })
  }
})

describe('helpers — defaults razoáveis', () => {
  it('unauthorized default message', () => {
    expect(unauthorized().message).toBe('Não autenticado')
  })
  it('forbidden default message', () => {
    expect(forbidden().message).toBe('Sem permissão')
  })
  it('notFound default message', () => {
    expect(notFound().message).toBe('Não encontrado')
  })
})
