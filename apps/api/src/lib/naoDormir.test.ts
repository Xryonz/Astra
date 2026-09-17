import { describe, it, expect } from 'vitest'
import { enderecoParaSeCutucar, horaDeDeixarDormir } from './naoDormir'

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

describe('janela em que o servidor pode dormir', () => {
  it('dorme da meia-noite as 7h de Sao Paulo, como o robo externo', () => {
    expect(horaDeDeixarDormir(new Date('2026-09-18T02:59:59Z'))).toBe(false)
    expect(horaDeDeixarDormir(new Date('2026-09-18T03:00:00Z'))).toBe(true)
    expect(horaDeDeixarDormir(new Date('2026-09-18T09:59:59Z'))).toBe(true)
    expect(horaDeDeixarDormir(new Date('2026-09-18T10:00:00Z'))).toBe(false)
  })

  it('le o relogio de Sao Paulo, nao o do servidor em UTC', () => {
    expect(horaDeDeixarDormir(new Date('2026-09-18T09:30:00Z'))).toBe(true)
    expect(horaDeDeixarDormir(new Date('2026-09-18T02:30:00Z'))).toBe(false)
  })

  it('trata a meia-noite como zero hora, nao como vinte e quatro', () => {
    expect(horaDeDeixarDormir(new Date('2026-09-18T03:00:00Z'))).toBe(true)
  })
})
