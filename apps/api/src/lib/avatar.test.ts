import { describe, it, expect } from 'vitest'
import sharp from 'sharp'

const LADO_DE_EXIBICAO = 256

async function ruido(lado: number): Promise<Buffer> {
  const px = Buffer.alloc(lado * lado * 3)
  let s = 12345
  for (let i = 0; i < px.length; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    px[i] = (s >> 16) & 0xff
  }
  return sharp(px, { raw: { width: lado, height: lado, channels: 3 } }).png().toBuffer()
}

describe('avatar em duas versões', () => {
  it('a versão de exibição é muito menor que a original de 1024', async () => {
    const entrada = await ruido(1024)

    const original = await sharp(entrada)
      .webp({ quality: 92, effort: 6, alphaQuality: 100, smartSubsample: true })
      .toBuffer()

    const exibicao = await sharp(entrada)
      .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
      .webp({ quality: 90, effort: 6, alphaQuality: 100, smartSubsample: true })
      .toBuffer()

    const vezes = original.length / exibicao.length
    console.log(
      `original ${(original.length / 1024).toFixed(0)} KB · ` +
        `exibição ${(exibicao.length / 1024).toFixed(0)} KB · ${vezes.toFixed(1)}x menor`,
    )

    expect(vezes).toBeGreaterThan(5)
  })

  it('a versão de exibição tem 256 no maior lado', async () => {
    const saida = await sharp(await ruido(1024))
      .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
      .webp()
      .toBuffer()
    const meta = await sharp(saida).metadata()
    expect(Math.max(meta.width ?? 0, meta.height ?? 0)).toBe(LADO_DE_EXIBICAO)
  })

  it('imagem que já é pequena não é inflada', async () => {
    const saida = await sharp(await ruido(64))
      .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
      .webp()
      .toBuffer()
    const meta = await sharp(saida).metadata()
    expect(meta.width).toBe(64)
    expect(meta.height).toBe(64)
  })
})
