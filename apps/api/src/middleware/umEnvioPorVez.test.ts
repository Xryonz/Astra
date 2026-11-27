import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import express from 'express'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { setTimeout as esperar } from 'node:timers/promises'
import { umEnvioPorVez } from './umEnvioPorVez'

const diario: string[] = []
let servidor: Server
let base = ''

beforeAll(async () => {
  const app = express()
  app.post('/envio/:nome', umEnvioPorVez, async (req, res) => {
    const nome = String(req.params.nome)
    diario.push(`começa ${nome}`)
    await esperar(60)
    diario.push(`termina ${nome}`)
    res.status(nome.startsWith('falha') ? 500 : 200).end()
  })
  await new Promise<void>((pronto) => {
    servidor = app.listen(0, () => pronto())
  })
  base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
})

afterAll(() => {
  servidor.close()
})

beforeEach(() => {
  diario.length = 0
})

const enviar = (nome: string, sinal?: AbortSignal) => fetch(`${base}/envio/${nome}`, { method: 'POST', signal: sinal })

describe('um envio de arquivos por vez', () => {
  it('dois envios ao mesmo tempo correm um depois do outro, sem se misturar', async () => {
    const primeiro = enviar('a')
    await esperar(10)
    const segundo = enviar('b')
    await Promise.all([primeiro, segundo])
    expect(diario).toEqual(['começa a', 'termina a', 'começa b', 'termina b'])
  })

  it('um envio que termina em erro passa a vez para o seguinte', async () => {
    const comErro = enviar('falha')
    await esperar(10)
    const seguinte = enviar('c')
    const [r1, r2] = await Promise.all([comErro, seguinte])
    expect([r1.status, r2.status]).toEqual([500, 200])
    expect(diario).toEqual(['começa falha', 'termina falha', 'começa c', 'termina c'])
  })

  it('quem desiste enquanto espera não trava quem vem depois', async () => {
    const segurando = enviar('d')
    await esperar(10)
    const desistencia = new AbortController()
    const desistente = enviar('e', desistencia.signal).catch(() => null)
    await esperar(10)
    desistencia.abort()
    const depois = enviar('f')
    const [, , r3] = await Promise.all([segurando, desistente, depois])
    expect(r3.status).toBe(200)
    expect(diario).toEqual(['começa d', 'termina d', 'começa f', 'termina f'])
  })
})
