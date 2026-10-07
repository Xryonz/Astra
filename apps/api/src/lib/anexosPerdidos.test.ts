import { describe, expect, it } from 'vitest'
import { IMAGEM_INDISPONIVEL, semAnexosPerdidos } from './anexosPerdidos'

describe('semAnexosPerdidos', () => {
  it('troca o anexo do disco apagado pelo cartão de imagem indisponível', () => {
    const [r] = semAnexosPerdidos([{
      url: '/uploads/abc.png', thumbUrl: '/uploads/abc-t.webp', type: 'image/png',
      name: 'foto.png', size: 1234, width: 4000, height: 3000, blurhash: 'LEHV6nWB2yk8',
    }]) as Array<Record<string, unknown>>
    expect(r.url).toBe(IMAGEM_INDISPONIVEL)
    expect(r.type).toBe('image/png')
    expect(r.name).toBe('foto.png')
    expect(r.width).toBe(960)
    expect(r.height).toBe(540)
    expect(r.thumbUrl).toBeUndefined()
    expect(r.blurhash).toBeUndefined()
  })

  it('troca também vídeo e arquivo que moravam no disco apagado', () => {
    const [r] = semAnexosPerdidos([{ url: '/uploads/clip.mp4', type: 'video/mp4', name: 'clip.mp4', size: 9 }]) as Array<Record<string, unknown>>
    expect(r.url).toBe(IMAGEM_INDISPONIVEL)
    expect(r.type).toBe('image/png')
  })

  it('deixa intacto o anexo que está no armazenamento', () => {
    const vivo = { url: 'https://bucket.exemplo/abc.webp', type: 'image/webp', name: 'a.webp', size: 1 }
    expect(semAnexosPerdidos([vivo])[0]).toBe(vivo)
  })

  it('não quebra com item que não é objeto', () => {
    expect(semAnexosPerdidos([null, 'x'])).toEqual([null, 'x'])
  })
})
