import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import type { Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { setTimeout as esperar } from 'node:timers/promises'
import sharp from 'sharp'
import rotaDeEnvio from './upload'
import { generateAccessToken } from '../lib/jwt'

const h = vi.hoisted(() => ({ diario: [] as string[], temporariosNaHora: [] as number[] }))

const PASTA_DOS_ENVIOS = path.join(os.tmpdir(), 'astra-envios')
const temporarios = () => (fs.existsSync(PASTA_DOS_ENVIOS) ? fs.readdirSync(PASTA_DOS_ENVIOS).sort() : [])

vi.mock('../lib/storage', () => ({
  storageMode: 's3',
  guardarSeNovo: vi.fn(async (chave: string) => {
    h.temporariosNaHora.push(fs.readdirSync(path.join(os.tmpdir(), 'astra-envios')).length)
    h.diario.push(`começa ${chave}`)
    await esperar(20)
    h.diario.push(`termina ${chave}`)
    return `https://bucket.invalid/${chave}`
  }),
}))

let servidor: Server
let base = ''
let autorizacao = ''
let png = Buffer.alloc(0)

beforeAll(async () => {
  const app = express()
  app.use('/api/upload', rotaDeEnvio)
  await new Promise<void>((pronto) => {
    servidor = app.listen(0, () => pronto())
  })
  base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  autorizacao = `Bearer ${generateAccessToken('u1', 's1').token}`
  png = await sharp({ create: { width: 8, height: 8, channels: 3, background: '#808080' } })
    .png()
    .toBuffer()
})

afterAll(() => {
  servidor.close()
})

beforeEach(() => {
  h.diario.length = 0
  h.temporariosNaHora.length = 0
})

function enviar(arquivos: Array<{ nome: string; tipo: string; bytes: Buffer | string }>) {
  const form = new FormData()
  for (const a of arquivos) form.append('files', new Blob([a.bytes], { type: a.tipo }), a.nome)
  return fetch(`${base}/api/upload`, { method: 'POST', headers: { authorization: autorizacao }, body: form })
}

describe('envio de arquivos pelo disco', () => {
  it('a imagem chega por um arquivo temporário, que some depois da resposta', async () => {
    const antes = temporarios()
    const resposta = await enviar([{ nome: 'a.png', tipo: 'image/png', bytes: png }])
    expect(resposta.status).toBe(200)
    const { data } = (await resposta.json()) as { data: { attachments: Array<Record<string, unknown>> } }
    expect(data.attachments).toHaveLength(1)
    expect(data.attachments[0]).toMatchObject({ type: 'image/png', name: 'a.png', size: png.length, width: 8 })
    expect(h.temporariosNaHora[0]).toBeGreaterThan(0)
    expect(temporarios()).toEqual(antes)
  })

  it('o conteúdo de página disfarçado de imagem é recusado, e o temporário some também', async () => {
    const antes = temporarios()
    const resposta = await enviar([{ nome: 'b.png', tipo: 'image/png', bytes: '<!doctype html><script>x</script>' }])
    expect(resposta.status).toBe(415)
    expect(h.diario).toEqual([])
    expect(temporarios()).toEqual(antes)
  })

  it('vários arquivos são guardados um de cada vez, para só um ficar inteiro na memória', async () => {
    const resposta = await enviar([
      { nome: 'c.txt', tipo: 'text/plain', bytes: 'primeiro' },
      { nome: 'd.txt', tipo: 'text/plain', bytes: 'segundo' },
    ])
    expect(resposta.status).toBe(200)
    const nomes = h.diario.map((linha) => linha.split(' ')[0])
    expect(nomes).toEqual(['começa', 'termina', 'começa', 'termina'])
  })
})
