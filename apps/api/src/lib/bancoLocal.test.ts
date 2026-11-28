import { describe, it, expect } from 'vitest'
import { ehBancoLocal } from './bancoLocal'

describe('ehBancoLocal', () => {
  it('aceita o banco desta máquina', () => {
    expect(ehBancoLocal('postgresql://astra:senha@localhost:5432/astra')).toBe(true)
    expect(ehBancoLocal('postgres://astra@127.0.0.1/astra')).toBe(true)
    expect(ehBancoLocal('postgres://astra@[::1]:5432/astra')).toBe(true)
  })

  it('recusa o banco remoto, mesmo com localhost escrito em outra parte do endereço', () => {
    expect(ehBancoLocal('postgresql://astra:senha@banco.invalid:6543/postgres')).toBe(false)
    expect(ehBancoLocal('postgresql://localhost:senha@banco.invalid/astra')).toBe(false)
    expect(ehBancoLocal('postgresql://banco.invalid/localhost')).toBe(false)
  })

  it('recusa o endereço local que troca de servidor pelo parâmetro host', () => {
    expect(ehBancoLocal('postgresql://localhost/astra?host=banco.invalid')).toBe(false)
  })

  it('recusa o endereço vazio ou inválido', () => {
    expect(ehBancoLocal('')).toBe(false)
    expect(ehBancoLocal('nao e um endereco')).toBe(false)
  })
})
