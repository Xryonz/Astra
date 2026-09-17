import { describe, it, expect } from 'vitest'
import { enderecoParaSeCutucar } from './naoDormir'

describe('endereco que o servidor usa para se cutucar', () => {
  it('aponta para /live, que nao acorda o banco', () => {
    expect(enderecoParaSeCutucar('https://astra-kwzc.onrender.com')).toBe('https://astra-kwzc.onrender.com/live')
    expect(enderecoParaSeCutucar('https://astra-kwzc.onrender.com/api/')).toBe('https://astra-kwzc.onrender.com/live')
  })

  it('recusa endereco ausente, torto ou de outro protocolo', () => {
    expect(enderecoParaSeCutucar(undefined)).toBeNull()
    expect(enderecoParaSeCutucar('')).toBeNull()
    expect(enderecoParaSeCutucar('nao e url')).toBeNull()
    expect(enderecoParaSeCutucar('ftp://astra-kwzc.onrender.com')).toBeNull()
  })

  it('recusa a propria maquina, que nao passa pelo Render', () => {
    expect(enderecoParaSeCutucar('http://localhost:3001')).toBeNull()
    expect(enderecoParaSeCutucar('http://127.0.0.1:3001')).toBeNull()
  })
})
