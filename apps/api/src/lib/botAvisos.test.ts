import { describe, it, expect } from 'vitest'
import { chaveDoTurno } from './botAvisos'

// A CHAVE DO TURNO é o que impede a passagem de ser anunciada duas vezes.
//
// O turno da Sparxie tem DOIS dias (sexta e sábado). Se a chave fosse só
// "persona + data de hoje", ela se apresentaria na sexta e de novo no sábado —
// duas chegadas para uma entrada só. Por isso ela recua até o primeiro dia do
// turno: sexta e sábado precisam produzir a MESMA string.
//
// Datas: 2026-08-06 quinta · 07 sexta · 08 sábado · 09 domingo · 10 segunda.

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

  // A borda do fuso vale aqui também: sábado 23h em Brasília já é domingo em UTC,
  // e a chave tem que continuar sendo a do turno da Sparxie.
  it('sábado 23h de Brasília ainda é a chave da Sparxie', () => {
    expect(chaveDoTurno(emUtc('2026-08-09T02:00:00Z'))).toBe('sparxie:2026-08-07')
  })
})
