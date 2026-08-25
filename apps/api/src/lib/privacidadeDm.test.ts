import { describe, it, expect } from 'vitest'
import { nivelDeSussurro } from './privacidadeDm'

describe('nivelDeSussurro', () => {
  it('reconhece os tres niveis validos', () => {
    expect(nivelDeSussurro('all')).toBe('all')
    expect(nivelDeSussurro('shared')).toBe('shared')
    expect(nivelDeSussurro('friends')).toBe('friends')
  })

  it('cai em "all" pra ausencia de valor', () => {
    expect(nivelDeSussurro(null)).toBe('all')
    expect(nivelDeSussurro(undefined)).toBe('all')
    expect(nivelDeSussurro('')).toBe('all')
  })

  it('cai em "all" pra valor desconhecido, e nao no mais restrito', () => {
    expect(nivelDeSussurro('todos')).toBe('all')
    expect(nivelDeSussurro('ALL')).toBe('all')
    expect(nivelDeSussurro('friend')).toBe('all')
    expect(nivelDeSussurro('nobody')).toBe('all')
  })
})
