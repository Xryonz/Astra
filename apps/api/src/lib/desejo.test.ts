import { describe, it, expect } from 'vitest'
import {
  limparDesejo,
  podeDesejar,
  DESEJOS_NA_JANELA,
  JANELA_DO_DESEJO_MS,
} from './desejo'

const NULO = String.fromCharCode(0)
const SINO = String.fromCharCode(7)
const APAGAR = String.fromCharCode(127)

describe('limpeza do desejo', () => {
  it('tira espaco das pontas e junta espaco repetido do meio', () => {
    expect(limparDesejo('  quero    paz  ')).toBe('quero paz')
    expect(limparDesejo('quero\t\tpaz')).toBe('quero paz')
  })

  it('remove caractere de controle sem apagar o resto', () => {
    expect(limparDesejo(`quero${SINO} paz`)).toBe('quero paz')
    expect(limparDesejo(`que${NULO}ro`)).toBe('quero')
    expect(limparDesejo(`quero${APAGAR}paz`)).toBe('queropaz')
  })

  it('preserva a quebra de paragrafo mas corta a escada de linhas', () => {
    expect(limparDesejo('um\n\ndois')).toBe('um\n\ndois')
    expect(limparDesejo('um\n\n\n\n\ndois')).toBe('um\n\ndois')
  })

  it('normaliza em NFKC: acento decomposto e ligadura viram a forma canonica', () => {
    expect(limparDesejo('páscoa')).toBe('páscoa')
    expect(limparDesejo('ﬁm')).toBe('fim')
  })

  it('devolve vazio quando so havia sujeira', () => {
    expect(limparDesejo(`   ${NULO}  \t `)).toBe('')
  })
})

describe('limite de desejos', () => {
  it('deixa passar ate o teto da janela e barra o seguinte', () => {
    const t = 1_000_000
    for (let i = 0; i < DESEJOS_NA_JANELA; i++) {
      expect(podeDesejar('pessoa-a', t + i)).toBe(true)
    }
    expect(podeDesejar('pessoa-a', t + DESEJOS_NA_JANELA)).toBe(false)
  })

  it('libera de novo depois que a janela passa', () => {
    const t = 2_000_000
    for (let i = 0; i < DESEJOS_NA_JANELA; i++) podeDesejar('pessoa-b', t + i)
    expect(podeDesejar('pessoa-b', t + 1)).toBe(false)
    expect(podeDesejar('pessoa-b', t + JANELA_DO_DESEJO_MS + 1)).toBe(true)
  })

  it('conta por pessoa, nao no total', () => {
    const t = 3_000_000
    for (let i = 0; i < DESEJOS_NA_JANELA; i++) podeDesejar('pessoa-c', t + i)
    expect(podeDesejar('pessoa-c', t)).toBe(false)
    expect(podeDesejar('pessoa-d', t)).toBe(true)
  })

  it('tentativa barrada nao estende o castigo', () => {
    const t = 4_000_000
    for (let i = 0; i < DESEJOS_NA_JANELA; i++) podeDesejar('pessoa-e', t + i)
    expect(podeDesejar('pessoa-e', t + 10)).toBe(false)
    expect(podeDesejar('pessoa-e', t + JANELA_DO_DESEJO_MS)).toBe(true)
  })
})
