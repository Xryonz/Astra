import { describe, it, expect } from 'vitest'
import { semMarcaDeCodigo } from './bot'

describe('semMarcaDeCodigo', () => {
  it('tira a cerca e MANTÉM o conteúdo', () => {
    expect(semMarcaDeCodigo('olha:\n```\nnpm install\n```')).toBe('olha:\nnpm install')
  })

  it('tira a linguagem declarada junto da cerca', () => {
    expect(semMarcaDeCodigo('```ts\nconst x = 1\n```')).toBe('const x = 1')
  })

  it('fecha cerca órfã (resposta cortada no limite de tokens)', () => {
    expect(semMarcaDeCodigo('tenta assim:\n```bash\nls -la')).toBe('tenta assim:\nls -la')
  })

  it('tira crase simples e dupla', () => {
    expect(semMarcaDeCodigo('roda `ls` ou ``echo `oi` ``')).toBe('roda ls ou echo oi')
  })

  it('não deixa buraco de linhas em branco onde estava a cerca', () => {
    expect(semMarcaDeCodigo('antes\n\n```\nmeio\n```\n\ndepois')).toBe('antes\n\nmeio\n\ndepois')
  })

  it('não mexe em texto sem marca de código', () => {
    const t = 'Bom dia. O **negrito** e as listas continuam valendo:\n- um\n- dois'
    expect(semMarcaDeCodigo(t)).toBe(t)
  })
})
