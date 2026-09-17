import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express from 'express'
import type { Server } from 'http'
import type { AddressInfo } from 'net'

const banco = vi.hoisted(() => ({
  constelacoes: [] as Array<{ id: string; name: string; iconUrl: string | null; bannerUrl: string | null }>,
  membros: 0,
}))

vi.mock('../db', () => ({
  db: {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: async () => banco.constelacoes,
          then: (ok: (v: unknown) => unknown, erro: (e: unknown) => unknown) =>
            Promise.resolve([{ count: banco.membros }]).then(ok, erro),
        }),
      }),
    }),
  },
}))

import invitePreviewRouter from './invitePreview'

let servidor: Server
let base = ''

async function abrir(codigo: string) {
  const resposta = await fetch(`${base}/i/${codigo}`, { redirect: 'manual' })
  return { status: resposta.status, cabecalhos: resposta.headers, corpo: await resposta.text() }
}

describe('pagina do convite', () => {
  beforeAll(async () => {
    const app = express()
    app.use('/i', invitePreviewRouter)
    servidor = app.listen(0)
    await new Promise<void>((pronto) => servidor.once('listening', () => pronto()))
    base = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`
  })

  afterAll(() => new Promise<void>((fechado) => servidor.close(() => fechado())))

  beforeEach(() => {
    banco.constelacoes = [{ id: 'srv_1', name: 'Andromeda', iconUrl: null, bannerUrl: null }]
    banco.membros = 3
  })

  it('mostra a constelacao, o codigo e o download, sem redirecionar para o site arquivado', async () => {
    const { status, cabecalhos, corpo } = await abrir('abc123')

    expect(status).toBe(200)
    expect(cabecalhos.get('content-type')).toContain('text/html')
    expect(corpo).toContain('<h1>Andromeda</h1>')
    expect(corpo).toContain('3 estrelas brilham por aqui')
    expect(corpo).toContain('<code class="codigo">abc123</code>')
    expect(corpo).toContain('https://github.com/Xryonz/Astra/releases/latest')
    expect(corpo).toContain('<meta name="robots" content="noindex">')
    expect(corpo).not.toContain('http-equiv="refresh"')
    expect(corpo).not.toContain(process.env.CLIENT_URL)
  })

  it('escapa o nome da constelacao e o codigo', async () => {
    banco.constelacoes[0].name = '<script>alert(1)</script>'

    const { corpo } = await abrir(encodeURIComponent('"><img src=x onerror=alert(1)>'))

    expect(corpo).not.toContain('<script>alert(1)</script>')
    expect(corpo).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(corpo).not.toContain('<img src=x')
    expect(corpo).toContain('&quot;&gt;&lt;img src=x onerror=alert(1)&gt;')
  })

  it('convite inexistente responde 404, sem cache e ainda oferece o download', async () => {
    banco.constelacoes = []

    const { status, cabecalhos, corpo } = await abrir('sumiu')

    expect(status).toBe(404)
    expect(cabecalhos.get('cache-control')).toBe('no-store')
    expect(corpo).toContain('Este convite não existe mais')
    expect(corpo).toContain('https://github.com/Xryonz/Astra/releases/latest')
    expect(corpo).not.toContain('og:title')
  })

  it('usa o singular com uma estrela', async () => {
    banco.membros = 1

    const { corpo } = await abrir('abc123')

    expect(corpo).toContain('1 estrela brilha por aqui')
  })

  it('imagem em data URI fica fora da previa; caminho do servidor vira endereco absoluto', async () => {
    banco.constelacoes[0].bannerUrl = 'data:image/png;base64,AAAA'
    let { corpo } = await abrir('abc123')
    expect(corpo).not.toContain('og:image')
    expect(corpo).not.toContain('<img')

    banco.constelacoes[0].bannerUrl = '/uploads/capa.webp'
    ;({ corpo } = await abrir('abc123'))
    expect(corpo).toContain(`<meta property="og:image" content="${base}/uploads/capa.webp">`)
    expect(corpo).toContain(`<img class="capa" src="${base}/uploads/capa.webp" alt="">`)
  })
})
