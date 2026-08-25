import { describe, it, expect } from 'vitest'
import { custoDoNivel, progressoDoXp, brilhoDaTrilha, XP_POR_MENSAGEM, XP_POR_MINUTO_CALL } from './xp'

describe('curva de nivel', () => {
  it('cobra mais a cada nivel, sempre', () => {
    for (let n = 0; n < 60; n++) {
      expect(custoDoNivel(n + 1)).toBeGreaterThan(custoDoNivel(n))
    }
  })

  it('mantem os degraus que eu escolhi', () => {
    expect(custoDoNivel(0)).toBe(100)   
    expect(custoDoNivel(4)).toBe(380)
    expect(custoDoNivel(10)).toBe(1100)
    expect(custoDoNivel(20)).toBe(3100)
  })
})

describe('progressoDoXp', () => {
  it('comeca no nivel 0 com a barra vazia', () => {
    const p = progressoDoXp(0)
    expect(p.nivel).toBe(0)
    expect(p.noNivel).toBe(0)
    expect(p.paraOProximo).toBe(100)
  })

  it('nao sobe de nivel um xp antes da hora', () => {
    expect(progressoDoXp(99).nivel).toBe(0)
    expect(progressoDoXp(99).noNivel).toBe(99)
    expect(progressoDoXp(100).nivel).toBe(1)
    expect(progressoDoXp(100).noNivel).toBe(0)
  })

  it('bate com a soma dos custos, nivel a nivel', () => {
    let acumulado = 0
    for (let n = 0; n < 40; n++) {
      acumulado += custoDoNivel(n)
      const p = progressoDoXp(acumulado)
      expect(p.nivel).toBe(n + 1)
      expect(p.noNivel).toBe(0)
    }
  })

  it('trata xp negativo ou quebrado sem entrar em laco', () => {
    expect(progressoDoXp(-500).nivel).toBe(0)
    expect(progressoDoXp(150.7).nivel).toBe(1)
  })

  it('nao passa do teto de nivel nem com xp absurdo', () => {
    expect(progressoDoXp(Number.MAX_SAFE_INTEGER).nivel).toBeLessThanOrEqual(500)
  })
})

describe('trilha', () => {
  it('paga a lista no comeco e a cauda fixa depois', () => {
    expect(brilhoDaTrilha(1)).toBe(20)
    expect(brilhoDaTrilha(12)).toBe(120)
    expect(brilhoDaTrilha(13)).toBe(60)
    expect(brilhoDaTrilha(500)).toBe(60)
  })

  it('nao paga nada pelo nivel 0 (ninguem "chega" nele)', () => {
    expect(brilhoDaTrilha(0)).toBe(0)
    expect(brilhoDaTrilha(-3)).toBe(0)
  })
})

describe('taxas', () => {
  it('mensagem vale mais que um minuto de call', () => {
    expect(XP_POR_MENSAGEM).toBeGreaterThan(XP_POR_MINUTO_CALL)
  })
})
