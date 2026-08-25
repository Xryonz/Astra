import { describe, it, expect } from 'vitest'
import { urlDeAnexoPermitida, primeiroAnexoNaoPermitido } from './storage'

describe('urlDeAnexoPermitida', () => {
  it('aceita o armazenamento local do proprio app', () => {
    expect(urlDeAnexoPermitida('/uploads/abc123.png')).toBe(true)
  })

  it('aceita a CDN de GIF que o app oferece', () => {
    expect(urlDeAnexoPermitida('https://media.giphy.com/media/xyz/giphy.gif')).toBe(true)
    expect(urlDeAnexoPermitida('https://media1.giphy.com/media/xyz/giphy.gif')).toBe(true)
    expect(urlDeAnexoPermitida('https://giphy.com/gifs/xyz')).toBe(true)
  })

  it('recusa host de terceiro', () => {
    expect(urlDeAnexoPermitida('https://rastreio.exemplo/pixel.png')).toBe(false)
  })

  it('recusa http puro, mesmo num host da lista', () => {
    expect(urlDeAnexoPermitida('http://media.giphy.com/x.gif')).toBe(false)
  })

  it('nao cai em host que apenas termina com o nome permitido', () => {
    expect(urlDeAnexoPermitida('https://malgiphy.com/x.gif')).toBe(false)
    expect(urlDeAnexoPermitida('https://giphy.com.invasor.net/x.gif')).toBe(false)
  })

  it('recusa vazio e URL quebrada', () => {
    expect(urlDeAnexoPermitida(null)).toBe(false)
    expect(urlDeAnexoPermitida(undefined)).toBe(false)
    expect(urlDeAnexoPermitida('')).toBe(false)
    expect(urlDeAnexoPermitida('nao e uma url')).toBe(false)
  })

  it('recusa data: e javascript:', () => {
    expect(urlDeAnexoPermitida('data:text/html,<script>alert(1)</script>')).toBe(false)
    expect(urlDeAnexoPermitida('javascript:alert(1)')).toBe(false)
  })
})

describe('primeiroAnexoNaoPermitido', () => {
  it('devolve null quando esta tudo certo', () => {
    expect(primeiroAnexoNaoPermitido([
      { url: '/uploads/a.png' },
      { url: 'https://media.giphy.com/b.gif' },
    ])).toBeNull()
  })

  it('devolve null pra lista vazia ou ausente', () => {
    expect(primeiroAnexoNaoPermitido([])).toBeNull()
    expect(primeiroAnexoNaoPermitido(undefined)).toBeNull()
  })

  it('aponta QUAL url reprovou, pro erro poder dizer', () => {
    expect(primeiroAnexoNaoPermitido([
      { url: '/uploads/a.png' },
      { url: 'https://rastreio.exemplo/pixel.png' },
    ])).toBe('https://rastreio.exemplo/pixel.png')
  })

  it('checa tambem a miniatura', () => {
    expect(primeiroAnexoNaoPermitido([
      { url: '/uploads/a.png', thumbUrl: 'https://rastreio.exemplo/t.png' },
    ])).toBe('https://rastreio.exemplo/t.png')
  })
})
