import { describe, it, expect } from 'vitest'
import sharp from 'sharp'

// O AVATAR EM DUAS VERSÕES — a conta que justifica a fatia, feita com pixel de verdade.
//
// O que se quer provar não é que o `sharp` sabe redimensionar (sabe), e sim que a ECONOMIA
// existe na ordem de grandeza que motivou a mudança. O cliente salva avatar com 1024
// pixels de lado; o maior lugar em que ele é desenhado são 96dp, que numa tela de
// densidade dupla dá 192 pixels. Se a versão de 256 não encolher o arquivo de forma
// clara, a coluna nova e o processamento no upload não se pagam.
//
// A imagem de teste é RUIDO, e é a escolha honesta: um degradê liso comprimiria a quase
// nada nos dois tamanhos e faria a economia parecer maior do que é. Ruído é o pior caso
// para um compressor, então o número que sai daqui é um piso.

const LADO_DE_EXIBICAO = 256

/** Uma imagem de ruído do tamanho pedido, em PNG — o pior caso para o compressor. */
async function ruido(lado: number): Promise<Buffer> {
  const px = Buffer.alloc(lado * lado * 3)
  // Gerador simples e determinístico: o teste não pode passar ou falhar por sorte.
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

    // CINCO VEZES é o piso que torna a fatia defensável, e há folga grande sobre ele com
    // imagem de verdade — foto tem áreas lisas, ruído não tem nenhuma.
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
    // `withoutEnlargement` existe para isto: quem manda um avatar de 64px recebe 64px de
    // volta, e não um borrão de 256 com quatro vezes o peso. Sem essa opção o teste falha.
    const saida = await sharp(await ruido(64))
      .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
      .webp()
      .toBuffer()
    const meta = await sharp(saida).metadata()
    expect(meta.width).toBe(64)
    expect(meta.height).toBe(64)
  })
})
