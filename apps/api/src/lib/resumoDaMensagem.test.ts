import { describe, expect, it } from 'vitest'
import { resumoDaMensagem } from './notifications'

describe('resumoDaMensagem', () => {
  it('devolve a mensagem inteira quando ela cabe', () => {
    expect(resumoDaMensagem('bora hoje?', 0)).toBe('bora hoje?')
  })

  it('tira espaço em volta, que no aviso vira recuo estranho', () => {
    expect(resumoDaMensagem('  bora  ', 0)).toBe('bora')
  })

  it('corta o que passa de 140 e marca o corte', () => {
    const resumo = resumoDaMensagem('a'.repeat(300), 0)
    expect(resumo).toHaveLength(140)
    expect(resumo.endsWith('…')).toBe(true)
  })

  it('deixa passar inteiro exatamente no limite', () => {
    expect(resumoDaMensagem('a'.repeat(140), 0)).toHaveLength(140)
  })

  it('descreve o anexo quando não há texto', () => {
    expect(resumoDaMensagem('', 1)).toBe('enviou um anexo')
    expect(resumoDaMensagem('   ', 3)).toBe('enviou 3 anexos')
  })

  it('prefere o texto ao anexo quando existem os dois', () => {
    expect(resumoDaMensagem('olha isso', 1)).toBe('olha isso')
  })

  it('devolve vazio quando não há nem texto nem anexo', () => {
    expect(resumoDaMensagem('', 0)).toBe('')
  })
})
