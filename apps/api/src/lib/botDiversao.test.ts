import { describe, it, expect } from 'vitest'
import { dado, escolha, moeda } from './botDiversao'

// Trava a parte dos comandos que tem LOGICA de verdade.
//
// Sortear nao da pra testar (e aleatorio de proposito), mas o que cerca o sorteio
// da: interpretar "2d6", nao deixar alguem pedir 9999d9999, e recusar entrada torta
// com uma frase util em vez de um NaN na cara da pessoa.

describe('dado', () => {
  it('entende os formatos que alguem realmente digita', () => {
    expect(dado('2d6')).toMatch(/\(2d6\)/)
    expect(dado('d20')).toMatch(/\(d20\)/)
    expect(dado('')).toMatch(/\(d6\)/)      // sem argumento = d6
    expect(dado('3D10')).toMatch(/\(3d10\)/) // maiusculo tambem
  })

  it('recusa lixo com instrucao, nao com NaN', () => {
    expect(dado('banana')).toContain('Formato')
    expect(dado('2x6')).toContain('Formato')
  })

  // Alguem VAI tentar isso no primeiro dia so pra ver o que acontece.
  it('poe teto no absurdo em vez de gerar uma parede de numeros', () => {
    const r = dado('99d999')
    expect(r).toMatch(/\(20d100\)/)
    expect(r.split('+').length).toBeLessThanOrEqual(20)
  })

  it('resultado fica dentro do intervalo possivel', () => {
    for (let i = 0; i < 200; i++) {
      const total = Number(/\*\*(\d+)\*\*/.exec(dado('3d6'))![1])
      expect(total).toBeGreaterThanOrEqual(3)
      expect(total).toBeLessThanOrEqual(18)
    }
  })
})

describe('escolha', () => {
  it('exige pelo menos duas opcoes', () => {
    expect(escolha('pizza')).toContain('virgula')
    expect(escolha('')).toContain('virgula')
  })

  it('devolve uma das opcoes dadas, sem inventar', () => {
    const opcoes = ['pizza', 'sushi', 'hamburguer']
    for (let i = 0; i < 60; i++) {
      const r = escolha(opcoes.join(', '))
      expect(opcoes.some((o) => r.includes(o))).toBe(true)
    }
  })

  it('ignora espaco sobrando entre as virgulas', () => {
    const r = escolha('  a  ,   b  ')
    expect(r).toMatch(/\*\*(a|b)\*\*/)
  })
})

describe('moeda', () => {
  it('so tem dois resultados possiveis', () => {
    const vistos = new Set(Array.from({ length: 100 }, () => moeda()))
    expect(vistos.size).toBeLessThanOrEqual(2)
    for (const v of vistos) expect(v).toMatch(/Cara|Coroa/)
  })
})
