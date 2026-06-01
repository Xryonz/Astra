import { describe, it, expect } from 'vitest'
import { PERMS, computeMemberPerms, parsePermissionsJson } from './permissions'

describe('parsePermissionsJson', () => {
  it('aceita JSON array válido', () => {
    expect(parsePermissionsJson('["MANAGE_ROLES","KICK_MEMBERS"]')).toEqual(['MANAGE_ROLES', 'KICK_MEMBERS'])
  })

  it('filtra strings desconhecidas (defense em profundidade)', () => {
    const r = parsePermissionsJson('["MANAGE_ROLES","DELETE_UNIVERSE","KICK_MEMBERS"]')
    expect(r).toEqual(['MANAGE_ROLES', 'KICK_MEMBERS'])
  })

  it('lixo retorna array vazio (não joga)', () => {
    expect(parsePermissionsJson('not-json')).toEqual([])
    expect(parsePermissionsJson('{"oi":1}')).toEqual([])
    expect(parsePermissionsJson(null)).toEqual([])
    expect(parsePermissionsJson(undefined)).toEqual([])
    expect(parsePermissionsJson(42)).toEqual([])
  })

  it('array vazio passa limpo', () => {
    expect(parsePermissionsJson('[]')).toEqual([])
  })
})

describe('computeMemberPerms — cascata Owner → Admin → Cargos', () => {
  it('owner sempre isOwner=true mesmo sem ser member (caso edge)', () => {
    const m = computeMemberPerms('user-1', 'user-1', null, [])
    expect(m.isOwner).toBe(true)
    expect(m.memberId).toBeNull()
  })

  it('não-membro sem ser owner → tudo vazio', () => {
    const m = computeMemberPerms('owner-x', 'random-user', null, [])
    expect(m.isOwner).toBe(false)
    expect(m.isAdmin).toBe(false)
    expect(m.permissions.size).toBe(0)
    expect(m.memberId).toBeNull()
  })

  it('legacy ADMIN ganha set base (MANAGE_SERVER/CHANNELS/KICK/MESSAGES)', () => {
    const m = computeMemberPerms('owner', 'u1', { id: 'm1', role: 'ADMIN' }, [])
    expect(m.isAdmin).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_SERVER)).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_CHANNELS)).toBe(true)
    expect(m.permissions.has(PERMS.KICK_MEMBERS)).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_MESSAGES)).toBe(true)
    // NÃO ganha BAN/ROLES por default
    expect(m.permissions.has(PERMS.BAN_MEMBERS)).toBe(false)
    expect(m.permissions.has(PERMS.MANAGE_ROLES)).toBe(false)
  })

  it('MEMBER simples sem cargo → permissões vazias', () => {
    const m = computeMemberPerms('owner', 'u1', { id: 'm1', role: 'MEMBER' }, [])
    expect(m.isAdmin).toBe(false)
    expect(m.permissions.size).toBe(0)
  })

  it('cargo customizado UNE perms ao set', () => {
    const m = computeMemberPerms('owner', 'u1',
      { id: 'm1', role: 'MEMBER' },
      ['["BAN_MEMBERS","MANAGE_ROLES"]'],
    )
    expect(m.permissions.has(PERMS.BAN_MEMBERS)).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_ROLES)).toBe(true)
  })

  it('múltiplos cargos: união (dedupe automático)', () => {
    const m = computeMemberPerms('owner', 'u1',
      { id: 'm1', role: 'MEMBER' },
      [
        '["BAN_MEMBERS"]',
        '["MANAGE_ROLES","BAN_MEMBERS"]', // BAN duplicado
        '["MANAGE_CHANNELS"]',
      ],
    )
    expect(m.permissions.size).toBe(3)
    expect(m.permissions.has(PERMS.BAN_MEMBERS)).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_ROLES)).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_CHANNELS)).toBe(true)
  })

  it('ADMIN + cargo: union dos dois sets', () => {
    const m = computeMemberPerms('owner', 'u1',
      { id: 'm1', role: 'ADMIN' },
      ['["BAN_MEMBERS"]'],
    )
    // ADMIN base + BAN_MEMBERS
    expect(m.permissions.size).toBe(5)
    expect(m.permissions.has(PERMS.BAN_MEMBERS)).toBe(true)
    expect(m.permissions.has(PERMS.MANAGE_SERVER)).toBe(true)
  })

  it('cargo com perm inválida não polui o set', () => {
    const m = computeMemberPerms('owner', 'u1',
      { id: 'm1', role: 'MEMBER' },
      ['["MANAGE_ROLES","HACKEAR_TUDO"]'],
    )
    expect(m.permissions.has(PERMS.MANAGE_ROLES)).toBe(true)
    expect(m.permissions.size).toBe(1)
  })

  it('cargo com JSON corrompido é silenciosamente ignorado', () => {
    const m = computeMemberPerms('owner', 'u1',
      { id: 'm1', role: 'MEMBER' },
      ['["MANAGE_ROLES"]', 'GARBAGE', '{"not":"array"}'],
    )
    expect(m.permissions.size).toBe(1)
    expect(m.permissions.has(PERMS.MANAGE_ROLES)).toBe(true)
  })

  it('owner com cargo: perms-set fica vazio (isOwner short-circuit)', () => {
    // Convenção: owner usa isOwner pra short-circuit, então perms-set
    // não precisa estar populado. Mas se for member também, deve refletir.
    const m = computeMemberPerms('u1', 'u1', { id: 'm1', role: 'MEMBER' }, ['["BAN_MEMBERS"]'])
    expect(m.isOwner).toBe(true)
    // Set ainda reflete o que ele tem como member
    expect(m.permissions.has(PERMS.BAN_MEMBERS)).toBe(true)
  })

  it('ownerId null (server fantasma) → isOwner false', () => {
    const m = computeMemberPerms(null, 'u1', { id: 'm1', role: 'MEMBER' }, [])
    expect(m.isOwner).toBe(false)
  })
})
