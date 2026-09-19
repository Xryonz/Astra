import { describe, it, expect } from 'vitest'
import { chaveDoTurno, chaveNaEpoca, quemDisse } from './botAvisos'

const emUtc = (iso: string) => new Date(iso)

describe('chave do turno', () => {
  it('sexta e sábado caem na MESMA chave — uma entrada, um anúncio', () => {
    const sexta  = chaveDoTurno(emUtc('2026-08-07T13:00:00Z'))
    const sabado = chaveDoTurno(emUtc('2026-08-08T13:00:00Z'))
    expect(sexta).toBe(sabado)
    expect(sexta).toBe('sparxie:2026-08-07')
  })

  it('domingo a quinta caem na mesma chave da Sparkle', () => {
    const domingo = chaveDoTurno(emUtc('2026-08-09T13:00:00Z'))
    const segunda = chaveDoTurno(emUtc('2026-08-10T13:00:00Z'))
    const quinta  = chaveDoTurno(emUtc('2026-08-13T13:00:00Z'))
    expect(domingo).toBe(segunda)
    expect(segunda).toBe(quinta)
    expect(domingo).toBe('sparkle:2026-08-09')
  })

  it('a chave VIRA na troca — senão o anúncio nunca sairia de novo', () => {
    const sabado  = chaveDoTurno(emUtc('2026-08-08T13:00:00Z'))
    const domingo = chaveDoTurno(emUtc('2026-08-09T13:00:00Z'))
    expect(sabado).not.toBe(domingo)
  })

  it('a semana seguinte tem chave nova (não reaproveita a de sete dias atrás)', () => {
    const estaSexta   = chaveDoTurno(emUtc('2026-08-07T13:00:00Z'))
    const outraSexta  = chaveDoTurno(emUtc('2026-08-14T13:00:00Z'))
    expect(estaSexta).not.toBe(outraSexta)
    expect(outraSexta).toBe('sparxie:2026-08-14')
  })

  it('sábado 23h de Brasília ainda é a chave da Sparxie', () => {
    expect(chaveDoTurno(emUtc('2026-08-09T02:00:00Z'))).toBe('sparxie:2026-08-07')
  })
})

describe('quem estava de plantão quando a mensagem antiga saiu', () => {
  it('até 09/08 a Sparxie tinha sábado e domingo', () => {
    expect(chaveNaEpoca(emUtc('2026-08-02T15:00:00Z'))).toBe('sparxie')
    expect(chaveNaEpoca(emUtc('2026-08-07T15:00:00Z'))).toBe('sparkle')
    expect(chaveNaEpoca(emUtc('2026-08-09T15:00:00Z'))).toBe('sparxie')
  })

  it('depois da virada de 09/08 a Sparxie passa a ter sexta e sábado', () => {
    expect(chaveNaEpoca(emUtc('2026-08-09T20:00:00Z'))).toBe('sparkle')
    expect(chaveNaEpoca(emUtc('2026-08-14T15:00:00Z'))).toBe('sparxie')
    expect(chaveNaEpoca(emUtc('2026-08-16T15:00:00Z'))).toBe('sparkle')
  })

  it('a meia-noite que conta é a de Brasília', () => {
    expect(chaveNaEpoca(emUtc('2026-09-18T02:59:59Z'))).toBe('sparkle')
    expect(chaveNaEpoca(emUtc('2026-09-18T03:00:00Z'))).toBe('sparxie')
  })

  it('as trocas do print do dono: a despedida é de quem sai, a chegada de quem entra', () => {
    const sextaDe04 = emUtc('2026-09-04T06:26:00Z')
    expect(quemDisse('Fecho por aqui. Boa virada — a Sparxie assume agora.', sextaDe04)).toBe('sparkle')
    expect(quemDisse('Cheguei. O fim de semana é meu: `/sparxie festa` se travar no que fazer.', sextaDe04)).toBe('sparxie')

    const domingoDe30 = emUtc('2026-08-30T03:00:00Z')
    expect(quemDisse('Fim do meu turno. Semana nova é com a Sparkle.', domingoDe30)).toBe('sparxie')
    expect(quemDisse('Voltei. Semana começando — `/sparkle ajuda` se precisar de mim.', domingoDe30)).toBe('sparkle')
  })
})
