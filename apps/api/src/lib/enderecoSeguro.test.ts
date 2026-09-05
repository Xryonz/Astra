import { describe, it, expect } from 'vitest'
import { ehIpPublico, urlUsavel } from './enderecoSeguro'

describe('endereços que o servidor NÃO pode buscar', () => {
  it('recusa o próprio host', () => {
    expect(ehIpPublico('127.0.0.1')).toBe(false)
    expect(ehIpPublico('127.9.9.9')).toBe(false)
    expect(ehIpPublico('0.0.0.0')).toBe(false)
    expect(ehIpPublico('::1')).toBe(false)
  })

  it('recusa a rede local, que é o alvo de quem tenta usar o servidor como ponte', () => {
    expect(ehIpPublico('10.0.0.5')).toBe(false)
    expect(ehIpPublico('172.16.0.1')).toBe(false)
    expect(ehIpPublico('172.31.255.254')).toBe(false)
    expect(ehIpPublico('192.168.1.1')).toBe(false)
  })

  it('recusa o 169.254.169.254, de onde saem as credenciais da nuvem', () => {
    expect(ehIpPublico('169.254.169.254')).toBe(false)
  })

  it('recusa CGNAT, multicast e reservados', () => {
    expect(ehIpPublico('100.64.0.1')).toBe(false)
    expect(ehIpPublico('224.0.0.1')).toBe(false)
    expect(ehIpPublico('240.0.0.1')).toBe(false)
  })

  it('recusa v6 local e o v4 disfarçado de v6', () => {
    expect(ehIpPublico('fe80::1')).toBe(false)
    expect(ehIpPublico('fd00::1')).toBe(false)
    expect(ehIpPublico('::ffff:127.0.0.1')).toBe(false)
    expect(ehIpPublico('::ffff:192.168.0.1')).toBe(false)
  })

  it('aceita endereço público de verdade', () => {
    expect(ehIpPublico('1.1.1.1')).toBe(true)
    expect(ehIpPublico('8.8.8.8')).toBe(true)
    expect(ehIpPublico('172.32.0.1')).toBe(true)
    expect(ehIpPublico('2606:4700::1')).toBe(true)
  })

  it('não confunde vizinho de faixa com faixa privada', () => {
    expect(ehIpPublico('172.15.255.255')).toBe(true)
    expect(ehIpPublico('11.0.0.1')).toBe(true)
    expect(ehIpPublico('126.255.255.255')).toBe(true)
    expect(ehIpPublico('128.0.0.1')).toBe(true)
  })
})

describe('urlUsavel', () => {
  it('só deixa passar http e https', () => {
    expect(urlUsavel('https://exemplo.com')).not.toBeNull()
    expect(urlUsavel('http://exemplo.com')).not.toBeNull()
    expect(urlUsavel('file:///etc/passwd')).toBeNull()
    expect(urlUsavel('ftp://exemplo.com')).toBeNull()
    expect(urlUsavel('gopher://exemplo.com')).toBeNull()
    expect(urlUsavel('data:text/html,oi')).toBeNull()
  })

  it('recusa credencial embutida, que serve para enganar quem lê a URL', () => {
    expect(urlUsavel('https://usuario:senha@exemplo.com')).toBeNull()
  })

  it('recusa IP interno escrito direto, sem passar pelo DNS', () => {
    expect(urlUsavel('http://127.0.0.1:8080/admin')).toBeNull()
    expect(urlUsavel('http://169.254.169.254/latest/meta-data/')).toBeNull()
    expect(urlUsavel('http://[::1]/')).toBeNull()
  })

  it('recusa lixo', () => {
    expect(urlUsavel('')).toBeNull()
    expect(urlUsavel('nao e url')).toBeNull()
  })
})
