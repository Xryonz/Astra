import { describe, it, expect } from 'vitest'
import { ehCorDeNome } from './corDoNome'

describe('formato da cor do nome', () => {
  it('aceita hex de seis dígitos, em qualquer caixa', () => {
    expect(ehCorDeNome('#c9a96e')).toBe(true)
    expect(ehCorDeNome('#C9A96E')).toBe(true)
  })

  it('recusa hex curto, longo ou sem cerquilha', () => {
    expect(ehCorDeNome('#abc')).toBe(false)
    expect(ehCorDeNome('#c9a96e0')).toBe(false)
    expect(ehCorDeNome('c9a96e')).toBe(false)
    expect(ehCorDeNome('#zzzzzz')).toBe(false)
  })

  it('aceita o degradê que já existia, com qualquer ângulo', () => {
    expect(ehCorDeNome('gradient:0:#c9a96e:#9b7ac4')).toBe(true)
    expect(ehCorDeNome('gradient:360:#c9a96e:#9b7ac4')).toBe(true)
  })

  it('recusa degradê torto', () => {
    expect(ehCorDeNome('gradient:#c9a96e:#9b7ac4')).toBe(false)
    expect(ehCorDeNome('gradient:0:#c9a96e')).toBe(false)
    expect(ehCorDeNome('gradient:x:#c9a96e:#9b7ac4')).toBe(false)
  })

  it('aceita as três animadas, cada uma com a cor parada junto', () => {
    expect(ehCorDeNome('anim:arcoiris:#c9a96e')).toBe(true)
    expect(ehCorDeNome('anim:varredura:#c9a96e:#9b7ac4')).toBe(true)
    expect(ehCorDeNome('anim:pulso:#c9a96e')).toBe(true)
  })

  it('recusa animada sem a cor parada — o nome precisa de cor fora do hover', () => {
    expect(ehCorDeNome('anim:arcoiris')).toBe(false)
    expect(ehCorDeNome('anim:pulso')).toBe(false)
    expect(ehCorDeNome('anim:varredura:#c9a96e')).toBe(false)
  })

  it('recusa animação que não existe', () => {
    expect(ehCorDeNome('anim:disco:#c9a96e')).toBe(false)
    expect(ehCorDeNome('anim:')).toBe(false)
  })

  it('recusa o que não é texto', () => {
    expect(ehCorDeNome(null)).toBe(false)
    expect(ehCorDeNome(undefined)).toBe(false)
    expect(ehCorDeNome(42)).toBe(false)
    expect(ehCorDeNome({})).toBe(false)
  })

  it('não deixa passar sujeira em volta de um valor válido', () => {
    expect(ehCorDeNome(' #c9a96e')).toBe(false)
    expect(ehCorDeNome('#c9a96e ')).toBe(false)
    expect(ehCorDeNome('#c9a96e\n#000000')).toBe(false)
    expect(ehCorDeNome('anim:arcoiris:#c9a96e; drop table')).toBe(false)
  })
})
