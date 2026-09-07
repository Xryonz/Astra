import { describe, it, expect } from 'vitest'
import { parDaArte, enderecoNoCatalogo, chaveNoBucket, marcaNoRedis } from './arteDeAtividade'

const APLICATIVO = '1402418571715543120'
const IMPRESSAO = 'a0a565c547795c027b82f58c4a391c43'

describe('o par que o cliente pede, antes de virar endereço', () => {
  it('aceita o par real do catálogo, com e sem a extensão', () => {
    expect(parDaArte(APLICATIVO, IMPRESSAO)).toEqual({ aplicativo: APLICATIVO, impressao: IMPRESSAO })
    expect(parDaArte(APLICATIVO, `${IMPRESSAO}.webp`)?.impressao).toBe(IMPRESSAO)
    expect(parDaArte(APLICATIVO, `${IMPRESSAO}.WEBP`)?.impressao).toBe(IMPRESSAO)
  })

  it('recusa qualquer coisa que não seja dígito no aplicativo', () => {
    expect(parDaArte('12345abc', IMPRESSAO)).toBeNull()
    expect(parDaArte('../../etc', IMPRESSAO)).toBeNull()
    expect(parDaArte('1234', IMPRESSAO)).toBeNull()
    expect(parDaArte('1'.repeat(26), IMPRESSAO)).toBeNull()
    expect(parDaArte('', IMPRESSAO)).toBeNull()
  })

  it('recusa impressão fora do formato de 32 hexadecimais', () => {
    expect(parDaArte(APLICATIVO, IMPRESSAO.toUpperCase())).toBeNull()
    expect(parDaArte(APLICATIVO, IMPRESSAO.slice(0, 31))).toBeNull()
    expect(parDaArte(APLICATIVO, `${IMPRESSAO}0`)).toBeNull()
    expect(parDaArte(APLICATIVO, 'z'.repeat(32))).toBeNull()
  })

  it('recusa o que tentaria escapar do caminho ou trocar de servidor', () => {
    expect(parDaArte('1402418571715543120/../..', IMPRESSAO)).toBeNull()
    expect(parDaArte(APLICATIVO, '../../../etc/passwd')).toBeNull()
    expect(parDaArte(APLICATIVO, `${IMPRESSAO}?x=1`)).toBeNull()
    expect(parDaArte(APLICATIVO, `${IMPRESSAO}#a`)).toBeNull()
    expect(parDaArte('@127.0.0.1', IMPRESSAO)).toBeNull()
  })

  it('recusa o que não é texto', () => {
    expect(parDaArte(null, IMPRESSAO)).toBeNull()
    expect(parDaArte(APLICATIVO, undefined)).toBeNull()
    expect(parDaArte(123, IMPRESSAO)).toBeNull()
    expect(parDaArte(APLICATIVO, { toString: () => IMPRESSAO })).toBeNull()
  })
})

describe('o endereço que sai do par', () => {
  const par = parDaArte(APLICATIVO, IMPRESSAO)!

  it('aponta sempre para o CDN do catálogo, e para mais lugar nenhum', () => {
    const { hostname, protocol, pathname } = new URL(enderecoNoCatalogo(par))
    expect(protocol).toBe('https:')
    expect(hostname).toBe('cdn.discordapp.com')
    expect(pathname).toBe(`/app-icons/${APLICATIVO}/${IMPRESSAO}.png`)
  })

  it('deriva a chave do bucket do próprio par, então subir de novo sobrescreve o mesmo objeto', () => {
    expect(chaveNoBucket(par)).toBe(`atividade/${APLICATIVO}-${IMPRESSAO}.webp`)
    expect(chaveNoBucket(par)).toBe(chaveNoBucket(parDaArte(APLICATIVO, `${IMPRESSAO}.webp`)!))
  })

  it('a marca do Redis separa aplicativo de impressão, então ícone novo não reusa a marca antiga', () => {
    const outra = parDaArte(APLICATIVO, 'b'.repeat(32))!
    expect(marcaNoRedis(par)).not.toBe(marcaNoRedis(outra))
    expect(marcaNoRedis(par)).toBe(`arte-atividade:${APLICATIVO}:${IMPRESSAO}`)
  })
})
