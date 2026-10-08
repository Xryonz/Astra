import { describe, it, expect } from 'vitest'
import path from 'path'
import { promises as fs } from 'fs'
import sharp from 'sharp'
import { recortarFoto, AnimacaoGrandeDemais, LADO_DA_FOTO } from './recorteDeFoto'

async function dataUriDoArquivo(arquivo: string, mime: string): Promise<string> {
  const bytes = await fs.readFile(arquivo)
  return `data:${mime};base64,${bytes.toString('base64')}`
}

function bytesDe(dataUri: string): Buffer {
  return Buffer.from(dataUri.slice(dataUri.indexOf(',') + 1), 'base64')
}

const GIF_DA_SPARKLE = path.join(__dirname, '../../public/bot/sparkle.gif')

describe('recortarFoto', () => {
  it('recorta todos os quadros do GIF e devolve WebP animado no lado da foto', async () => {
    const entrada = await sharp(GIF_DA_SPARKLE, { animated: true }).metadata()
    const saida = await recortarFoto(await dataUriDoArquivo(GIF_DA_SPARKLE, 'image/gif'), { x: 95, y: 0, lado: 307 })

    expect(saida).toMatch(/^data:image\/webp;base64,/)
    const meta = await sharp(bytesDe(saida!), { animated: true }).metadata()
    expect(meta.pages).toBe(entrada.pages)
    expect(meta.width).toBe(LADO_DA_FOTO)
    expect(meta.pageHeight).toBe(LADO_DA_FOTO)
    expect(meta.delay?.slice(0, 3)).toEqual(entrada.delay?.slice(0, 3))
  })

  it('imagem parada vira WebP parado e não é ampliada', async () => {
    const png = await sharp({ create: { width: 300, height: 200, channels: 3, background: { r: 10, g: 20, b: 30 } } }).png().toBuffer()
    const saida = await recortarFoto(`data:image/png;base64,${png.toString('base64')}`, { x: 100, y: 50, lado: 120 })

    const meta = await sharp(bytesDe(saida!)).metadata()
    expect(meta.format).toBe('webp')
    expect(meta.pages ?? 1).toBe(1)
    expect(meta.width).toBe(120)
    expect(meta.height).toBe(120)
  })

  it('recorte que passa da borda é encolhido para caber', async () => {
    const png = await sharp({ create: { width: 300, height: 200, channels: 3, background: { r: 10, g: 20, b: 30 } } }).png().toBuffer()
    const saida = await recortarFoto(`data:image/png;base64,${png.toString('base64')}`, { x: 250, y: 0, lado: 200 })

    const meta = await sharp(bytesDe(saida!)).metadata()
    expect(meta.width).toBe(50)
    expect(meta.height).toBe(50)
  })

  it('recorte totalmente fora da imagem é recusado', async () => {
    const png = await sharp({ create: { width: 100, height: 100, channels: 3, background: { r: 0, g: 0, b: 0 } } }).png().toBuffer()
    expect(await recortarFoto(`data:image/png;base64,${png.toString('base64')}`, { x: 100, y: 0, lado: 10 })).toBeNull()
  })

  it('texto que não é data URI é recusado', async () => {
    expect(await recortarFoto('https://exemplo.com/foto.gif', { x: 0, y: 0, lado: 10 })).toBeNull()
  })

  it('animação acima do teto de pixels é recusada antes de decodificar', async () => {
    const quadros = await Promise.all(Array.from({ length: 16 }, (_, i) =>
      sharp({ create: { width: 2000, height: 2000, channels: 3, background: { r: i * 15, g: 2, b: 3 } } }).png().toBuffer()))
    const longa = await sharp(quadros, { join: { animated: true } }).gif().toBuffer()
    await expect(recortarFoto(`data:image/gif;base64,${longa.toString('base64')}`, { x: 0, y: 0, lado: 100 }))
      .rejects.toBeInstanceOf(AnimacaoGrandeDemais)
  })
})
