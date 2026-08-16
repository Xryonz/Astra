import { describe, it, expect } from 'vitest'
import { avisoPassa } from './silencioDeCanal'

// Esta função decide se um aviso SAI. Errar pra um lado enche a bandeja de quem
// pediu silêncio; errar pro outro cala quem não pediu nada — e o segundo caso é
// invisível: ninguém percebe que parou de receber aviso, só para de receber.
//
// Ela também é METADE de uma regra escrita duas vezes: a outra vive no cliente
// (ShellUiState.orbitaSilenciada). Se as duas divergirem, o sino fica quieto e a
// bandeja avisa, ou o contrário. Estes testes prendem esta metade.
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

  // "So quando me chamarem" quer dizer MENÇÃO, e nada mais. Resposta à sua
  // mensagem não é ser chamado: quem responde está seguindo a conversa, e quem
  // pediu este modo disse que a conversa pode esperar.
  it('no modo mencao, so mencao passa', () => {
    expect(avisoPassa('mentions', 'mention')).toBe(true)
    expect(avisoPassa('mentions', 'reply')).toBe(false)
    expect(avisoPassa('mentions', 'reaction')).toBe(false)
  })

  // Tipo novo de aviso não pode nascer silencioso por acidente: se alguém criar
  // 'thread_created' amanhã, ele passa no modo padrão sem ninguém lembrar disto.
  it('tipo desconhecido segue a regra do modo, e nao vira excecao', () => {
    expect(avisoPassa('all', 'tipo_que_ainda_nao_existe')).toBe(true)
    expect(avisoPassa('mute', 'tipo_que_ainda_nao_existe')).toBe(false)
    expect(avisoPassa('mentions', 'tipo_que_ainda_nao_existe')).toBe(false)
  })
})
