import { describe, it, expect } from 'vitest'
import { diariasDe, semanaisDe, periodoDiario, periodoSemanal } from './missoes'

// O sorteio e a parte que MENTIRIA em silencio se quebrasse.
//
// Ele nao grava nada: as missoes de hoje sao recalculadas a cada request a partir de
// (userId + dia). Se deixar de ser estavel, a pessoa ve tres missoes, atualiza a tela
// e ve outras tres — com o progresso da anterior preso numa linha que ninguem mais
// consulta. Nao daria erro em lugar nenhum.

const ALGUEM = 'user_abc123'

describe('sorteio das diarias', () => {
  it('e estavel: a mesma pessoa ve as mesmas tres o dia inteiro', () => {
    const a = diariasDe(ALGUEM).map((m) => m.id)
    const b = diariasDe(ALGUEM).map((m) => m.id)
    expect(a).toEqual(b)
  })

  it('da exatamente tres, sem repetir', () => {
    const ids = diariasDe(ALGUEM).map((m) => m.id)
    expect(ids).toHaveLength(3)
    expect(new Set(ids).size).toBe(3)
  })

  it('so sorteia do baralho diario', () => {
    for (const m of diariasDe(ALGUEM)) {
      expect(m.tipo).toBe('diaria')
      expect(m.id.startsWith('d.')).toBe(true)
      expect(m.id).not.toBe('d.bonus') // o bonus nao entra no sorteio
    }
  })

  // Se todo mundo recebesse as mesmas, o app inteiro faria a mesma coisa no mesmo
  // dia — e o movimento ficaria concentrado num canto so.
  it('pessoas diferentes recebem conjuntos diferentes', () => {
    const conjuntos = new Set(
      Array.from({ length: 40 }, (_, i) => diariasDe(`user_${i}`).map((m) => m.id).sort().join(',')),
    )
    expect(conjuntos.size).toBeGreaterThan(3)
  })
})

describe('sorteio das semanais', () => {
  it('da duas, estaveis, do baralho semanal', () => {
    const a = semanaisDe(ALGUEM)
    expect(a).toHaveLength(2)
    expect(a.map((m) => m.id)).toEqual(semanaisDe(ALGUEM).map((m) => m.id))
    for (const m of a) expect(m.tipo).toBe('semanal')
  })
})

describe('periodos', () => {
  it('o diario e uma data AAAA-MM-DD', () => {
    expect(periodoDiario()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('o semanal e um balde estavel', () => {
    expect(periodoSemanal()).toMatch(/^S\d+$/)
    expect(periodoSemanal()).toBe(periodoSemanal())
  })

  // Os dois entram na chave primaria junto com userId+missionId. Se colidissem,
  // o progresso de uma semana cairia em cima da outra.
  it('diario e semanal nunca se confundem', () => {
    expect(periodoDiario()).not.toBe(periodoSemanal())
    expect(periodoDiario()).not.toBe('sempre')
  })
})

describe('catalogo', () => {
  it('nao tem missao impossivel nem de graca', () => {
    for (const m of [...diariasDe(ALGUEM), ...semanaisDe(ALGUEM)]) {
      expect(m.alvo).toBeGreaterThan(0)
      expect(m.xp).toBeGreaterThan(0)
      expect(m.titulo.length).toBeGreaterThan(3)
    }
  })
})
