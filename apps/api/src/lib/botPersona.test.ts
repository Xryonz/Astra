import { describe, it, expect } from 'vitest'
import { ehFimDeSemana, personaDoDia, prefixoUsado, semPrefixo, comandosDeHoje } from './bot'

// Trava a regra de QUEM esta de plantao.
//
// O fuso e a parte perigosa: o servidor roda em UTC (Render) e o publico e do
// Brasil. Com `getDay()` cru, a Sparxie entraria as 21h de SEXTA e a Sparkle
// voltaria as 21h de DOMINGO — errado nas duas pontas, e do tipo que so aparece
// no fim de semana, quando ninguem esta olhando o log.

const emUtc = (iso: string) => new Date(iso)

describe('de quem e o plantao', () => {
  it('sexta 23h de Brasilia ainda e Sparkle (mesmo ja sendo sabado em UTC)', () => {
    // 2026-08-07 e uma sexta. 23h BRT = 02h UTC de sabado.
    expect(ehFimDeSemana(emUtc('2026-08-08T02:00:00Z'))).toBe(false)
    expect(personaDoDia(emUtc('2026-08-08T02:00:00Z')).nome).toBe('Sparkle')
  })

  it('sabado de manha e Sparxie', () => {
    expect(personaDoDia(emUtc('2026-08-08T13:00:00Z')).nome).toBe('Sparxie')
  })

  it('domingo 23h de Brasilia ainda e Sparxie (ja e segunda em UTC)', () => {
    // 2026-08-09 e domingo. 23h BRT = 02h UTC de segunda.
    expect(ehFimDeSemana(emUtc('2026-08-10T02:00:00Z'))).toBe(true)
    expect(personaDoDia(emUtc('2026-08-10T02:00:00Z')).nome).toBe('Sparxie')
  })

  it('segunda de manha volta a Sparkle', () => {
    expect(personaDoDia(emUtc('2026-08-10T13:00:00Z')).nome).toBe('Sparkle')
  })
})

describe('prefixos', () => {
  it('aceita os dois nomes e o /astra antigo (o mobile ainda manda esse)', () => {
    expect(prefixoUsado('/sparkle ping')).toBe('/sparkle')
    expect(prefixoUsado('/sparxie festa')).toBe('/sparxie')
    expect(prefixoUsado('/astra ajuda')).toBe('/astra')
  })

  it('nao confunde com palavra que so COMECA igual', () => {
    // Sem o espaco obrigatorio, "/sparklezinho" viraria comando.
    expect(prefixoUsado('/sparklezinho')).toBeNull()
    expect(prefixoUsado('sparkle ping')).toBeNull()
    expect(prefixoUsado('/outro comando')).toBeNull()
  })

  it('prefixo sozinho conta (e a conversa aberta)', () => {
    expect(prefixoUsado('/sparkle')).toBe('/sparkle')
    expect(semPrefixo('/sparkle')).toBe('')
  })

  it('tira o prefixo e devolve so o pedido', () => {
    expect(semPrefixo('/sparxie desejo quero paz')).toBe('desejo quero paz')
    expect(semPrefixo('/ASTRA  ping ')).toBe('ping')
  })
})

describe('catalogo do dia', () => {
  it('dia util: prefixo da Sparkle e SEM os comandos de fim de semana', () => {
    const nomes = comandosDeHoje(emUtc('2026-08-10T13:00:00Z')).map((c) => c.name)
    expect(nomes).toContain('/sparkle ping')
    expect(nomes.some((n) => n.includes('festa'))).toBe(false)
    expect(nomes.some((n) => n.startsWith('/sparxie'))).toBe(false)
  })

  it('fim de semana: prefixo da Sparxie e COM os extras', () => {
    const nomes = comandosDeHoje(emUtc('2026-08-08T13:00:00Z')).map((c) => c.name)
    expect(nomes).toContain('/sparxie festa')
    // Com argumento, o nome carrega a FORMA — e por isso o `toContain` exato de
    // "/sparxie desejo" nao vale mais.
    expect(nomes).toContain('/sparxie desejo <seu desejo>')
    expect(nomes.some((n) => n.startsWith('/sparkle'))).toBe(false)
  })

  // O que a caixinha do "/" mostra tem que ENSINAR a escrever. Antes ela dizia
  // "/sparxie desejo — joga um desejo na estrela": quem lia mandava exatamente
  // isso, sem desejo nenhum, e o comando reclamava. O formato e justamente a
  // parte que nao da pra adivinhar.
  it('comando com argumento mostra a forma e um exemplo', () => {
    const fds = comandosDeHoje(emUtc('2026-08-08T13:00:00Z'))
    const desejo = fds.find((c) => c.name.startsWith('/sparxie desejo'))!
    expect(desejo.name).toBe('/sparxie desejo <seu desejo>')
    expect(desejo.description).toContain('ex.: /sparxie desejo passar de ano')

    // O exemplo usa o prefixo de QUEM ESTA DE PLANTAO: ensinar "/sparxie ..."
    // numa terca seria ensinar errado.
    const util = comandosDeHoje(emUtc('2026-08-10T13:00:00Z'))
    const conversa = util.find((c) => c.name.startsWith('/sparkle <'))!
    expect(conversa.name).toBe('/sparkle <sua pergunta>')
    expect(conversa.description).toContain('ex.: /sparkle ')
    expect(conversa.description).not.toContain('/sparxie')
  })

  // Comando sem argumento continua limpo: acrescentar "<...>" onde nao ha o que
  // escrever so poluiria a lista.
  it('comando sem argumento nao ganha rotulo', () => {
    const nomes = comandosDeHoje(emUtc('2026-08-10T13:00:00Z')).map((c) => c.name)
    expect(nomes).toContain('/sparkle ping')
    expect(nomes).toContain('/sparkle ajuda')
  })
})
