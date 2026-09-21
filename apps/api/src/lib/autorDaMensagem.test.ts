import { describe, expect, it } from 'vitest'
import { autorComoEra, fotoViva } from './autorDaMensagem'

const autor = { displayName: 'Atual', avatarUrl: 'https://bucket.exemplo/foto-atual.webp' }

describe('autorComoEra', () => {
  it('mantém a foto guardada quando ela ainda existe', () => {
    const r = autorComoEra(autor, 'Sparkle', 'https://bucket.exemplo/sparkle.webp')
    expect(r.avatarUrl).toBe('https://bucket.exemplo/sparkle.webp')
    expect(r.displayName).toBe('Sparkle')
  })

  it('troca a foto guardada no disco apagado pela foto atual', () => {
    const r = autorComoEra(autor, null, '/uploads/abc.webp')
    expect(r.avatarUrl).toBe(autor.avatarUrl)
  })

  it('não inventa foto quando o autor também não tem', () => {
    const r = autorComoEra({ displayName: 'Sem foto', avatarUrl: null }, null, '/uploads/abc.webp')
    expect(r.avatarUrl).toBeNull()
  })
})

describe('fotoViva', () => {
  it('descarta só o disco apagado', () => {
    expect(fotoViva('/uploads/x.png')).toBeNull()
    expect(fotoViva('data:image/png;base64,AAAA')).toBe('data:image/png;base64,AAAA')
    expect(fotoViva(null)).toBeNull()
  })
})
