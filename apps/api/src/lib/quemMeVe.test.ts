import { describe, it, expect } from 'vitest'
import { montarSalas } from './quemMeVe'

describe('quem enxerga a pessoa', () => {
  it('a propria pessoa sempre entra, mesmo sem constelacao nem amigo', () => {
    expect(montarSalas('eu', [], [])).toEqual(['user:eu'])
  })

  it('cada constelacao vira uma sala, e cada pessoa vira a sala pessoal dela', () => {
    const salas = montarSalas('eu', ['s1', 's2'], ['a1', 'a2'])
    expect(salas).toContain('server:s1')
    expect(salas).toContain('server:s2')
    expect(salas).toContain('user:a1')
    expect(salas).toContain('user:a2')
    expect(salas).toContain('user:eu')
    expect(salas).toHaveLength(5)
  })

  it('nao repete sala quando o mesmo id aparece duas vezes', () => {
    expect(montarSalas('eu', ['s1', 's1'], ['a1', 'a1'])).toHaveLength(3)
  })

  it('amizade consigo mesmo nao vira sala extra', () => {
    expect(montarSalas('eu', [], ['eu'])).toEqual(['user:eu'])
  })

  it('quem so tem sussurro comigo entra igual a um amigo — a lista de conversas mostra foto e nome', () => {
    expect(montarSalas('eu', [], ['so-sussurro'])).toContain('user:so-sussurro')
  })

  it('a mesma pessoa como amiga e como sussurro nao duplica', () => {
    expect(montarSalas('eu', [], ['p1', 'p1'])).toHaveLength(2)
  })

  it('id vazio e descartado em vez de virar sala sem dono', () => {
    const salas = montarSalas('eu', ['', 's1'], ['', 'a1'])
    expect(salas).not.toContain('server:')
    expect(salas).not.toContain('user:')
    expect(salas).toHaveLength(3)
  })

  it('quem nao divide constelacao nem amizade simplesmente nao aparece na lista', () => {
    const salas = montarSalas('eu', ['s1'], ['a1'])
    expect(salas).not.toContain('user:estranho')
    expect(salas).not.toContain('server:outra')
  })
})
