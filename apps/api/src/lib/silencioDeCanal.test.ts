import { describe, it, expect } from 'vitest'
import { avisoPassa } from './silencioDeCanal'

describe('avisoPassa', () => {
  it('deixa passar tudo no modo padrao', () => {
    expect(avisoPassa('all', 'mention')).toBe(true)
    expect(avisoPassa('all', 'reply')).toBe(true)
    expect(avisoPassa('all', 'dm')).toBe(true)
  })

  it('nao deixa passar nada quando esta calada', () => {
    expect(avisoPassa('mute', 'mention')).toBe(false)
    expect(avisoPassa('mute', 'reply')).toBe(false)
    expect(avisoPassa('mute', 'dm')).toBe(false)
  })

  it('no modo mencao, so mencao passa', () => {
    expect(avisoPassa('mentions', 'mention')).toBe(true)
    expect(avisoPassa('mentions', 'reply')).toBe(false)
    expect(avisoPassa('mentions', 'reaction')).toBe(false)
  })

  it('tipo desconhecido segue a regra do modo, e nao vira excecao', () => {
    expect(avisoPassa('all', 'tipo_que_ainda_nao_existe')).toBe(true)
    expect(avisoPassa('mute', 'tipo_que_ainda_nao_existe')).toBe(false)
    expect(avisoPassa('mentions', 'tipo_que_ainda_nao_existe')).toBe(false)
  })
})
