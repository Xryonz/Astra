import { describe, it, expect } from 'vitest'
import { nivelDeSussurro } from './privacidadeDm'

// Esta funcao e o portao de TODO usuario que existia antes da coluna nascer: pra
// eles o banco devolve o default, e qualquer valor que ela nao reconheca vira
// 'all'. Se ela um dia cair pro lado errado (retornar 'friends' num valor
// estranho), o efeito nao e um erro na tela -- e gente que para de conseguir
// falar com voce sem nunca ter pedido isso.
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

  // O default ABRE em vez de fechar de proposito: um valor corrompido nao pode
  // trancar a conta de ninguem sem que a pessoa tenha escolhido isso.
  it('cai em "all" pra valor desconhecido, e nao no mais restrito', () => {
    expect(nivelDeSussurro('todos')).toBe('all')
    expect(nivelDeSussurro('ALL')).toBe('all')
    expect(nivelDeSussurro('friend')).toBe('all')
    expect(nivelDeSussurro('nobody')).toBe('all')
  })
})
