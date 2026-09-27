import { describe, it, expect, vi, afterEach } from 'vitest'
import path from 'path'
import { promises as fs } from 'fs'
import sharp from 'sharp'

const criados: string[] = []

async function carregarStorageSemBucket() {
  vi.resetModules()
  for (const k of ['S3_ENDPOINT', 'R2_ACCOUNT_ID', 'R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY', 'R2_BUCKET', 'R2_PUBLIC_URL']) {
    vi.stubEnv(k, '')
  }
  vi.stubEnv('NODE_ENV', 'development')
  return import('./storage')
}

async function dataUriDeImagem(largura: number, altura: number, formato: 'png' | 'gif'): Promise<string> {
  const base = sharp({ create: { width: largura, height: altura, channels: 3, background: { r: 40, g: 90, b: 160 } } })
  const bytes = formato === 'png' ? await base.png().toBuffer() : await base.gif().toBuffer()
  return `data:image/${formato};base64,${bytes.toString('base64')}`
}

async function dataUriDeArteChapada(largura: number, altura: number): Promise<string> {
  const pixels = Buffer.alloc(largura * altura * 3)
  for (let y = 0; y < altura; y++) {
    for (let x = 0; x < largura; x++) {
      const i = (y * largura + x) * 3
      const bx = Math.floor(x / 8) * 8
      const by = Math.floor(y / 8) * 8
      pixels[i] = bx % 256
      pixels[i + 1] = by % 256
      pixels[i + 2] = (bx + by) % 256
    }
  }
  const bytes = await sharp(pixels, { raw: { width: largura, height: altura, channels: 3 } })
    .png({ compressionLevel: 9, adaptiveFiltering: true })
    .toBuffer()
  return `data:image/png;base64,${bytes.toString('base64')}`
}

async function medir(url: string) {
  expect(url.startsWith('/uploads/')).toBe(true)
  const arquivo = path.resolve(process.cwd(), 'uploads', url.slice('/uploads/'.length))
  criados.push(arquivo)
  return sharp(await fs.readFile(arquivo)).metadata()
}

afterEach(async () => {
  vi.unstubAllEnvs()
  vi.resetModules()
  await Promise.all(criados.splice(0).map((f) => fs.unlink(f).catch(() => {})))
})

describe('banner em versão média', () => {
  it('guarda a versão de exibição com 1280 de largura e a original inteira à parte', async () => {
    const { persistImagemDeExibicao, LADO_DO_BANNER } = await carregarStorageSemBucket()

    const { url, original } = await persistImagemDeExibicao(await dataUriDeImagem(2560, 853, 'png'), LADO_DO_BANNER)

    expect(url).not.toBeNull()
    expect(original).not.toBeNull()
    const media = await medir(url!)
    const inteira = await medir(original!)
    expect(media.width).toBe(1280)
    expect(media.format).toBe('webp')
    expect(inteira.width).toBe(2560)
  })

  it('arte de cores chapadas, com PNG mais leve que o WebP reduzido, também ganha versão média', async () => {
    const { persistImagemDeExibicao, LADO_DO_BANNER } = await carregarStorageSemBucket()

    const { url, original } = await persistImagemDeExibicao(await dataUriDeArteChapada(2560, 853), LADO_DO_BANNER)

    expect(original).not.toBeNull()
    expect((await medir(url!)).width).toBe(1280)
    expect((await medir(original!)).width).toBe(2560)
  })

  it('banner que já cabe na versão média fica só com a original, sem cópia', async () => {
    const { persistImagemDeExibicao, LADO_DO_BANNER } = await carregarStorageSemBucket()

    const { url, original } = await persistImagemDeExibicao(await dataUriDeImagem(900, 300, 'png'), LADO_DO_BANNER)

    expect(original).toBeNull()
    expect((await medir(url!)).width).toBe(900)
  })

  it('GIF entra inteiro, sem versão média, para não perder a animação', async () => {
    const { persistImagemDeExibicao, LADO_DO_BANNER } = await carregarStorageSemBucket()

    const { url, original } = await persistImagemDeExibicao(await dataUriDeImagem(2400, 800, 'gif'), LADO_DO_BANNER)

    expect(original).toBeNull()
    expect(url!.endsWith('.gif')).toBe(true)
    expect((await medir(url!)).width).toBe(2400)
  })

  it('endereço de fora e remoção passam intactos', async () => {
    const { persistImagemDeExibicao, LADO_DO_BANNER } = await carregarStorageSemBucket()

    expect(await persistImagemDeExibicao('https://media.giphy.com/media/x/giphy.gif', LADO_DO_BANNER))
      .toEqual({ url: 'https://media.giphy.com/media/x/giphy.gif', original: null })
    expect(await persistImagemDeExibicao(null, LADO_DO_BANNER)).toEqual({ url: null, original: null })
    expect(await persistImagemDeExibicao('', LADO_DO_BANNER)).toEqual({ url: '', original: null })
  })
})
