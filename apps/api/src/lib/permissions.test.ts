import { describe, it, expect } from 'vitest'
import {
  PERMS, computeMemberPerms, parsePermissionsJson, computeChannelVisibility,
} from './permissions'

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
        '["MANAGE_ROLES","BAN_MEMBERS"]',
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

    const m = computeMemberPerms('u1', 'u1', { id: 'm1', role: 'MEMBER' }, ['["BAN_MEMBERS"]'])
    expect(m.isOwner).toBe(true)

    expect(m.permissions.has(PERMS.BAN_MEMBERS)).toBe(true)
  })

  it('ownerId null (server fantasma) → isOwner false', () => {
    const m = computeMemberPerms(null, 'u1', { id: 'm1', role: 'MEMBER' }, [])
    expect(m.isOwner).toBe(false)
  })

  it('role enum "OWNER" (sem ownerId match) NÃO ganha admin perms', () => {

    const m = computeMemberPerms('outro-user', 'u1', { id: 'm1', role: 'OWNER' }, [])
    expect(m.isOwner).toBe(false)
    expect(m.isAdmin).toBe(false)
    expect(m.permissions.size).toBe(0)
  })

  it('ownerId vazio string NÃO faz match', () => {

    const m = computeMemberPerms('', 'u1', null, [])
    expect(m.isOwner).toBe(false)
  })

  it('MENTION_EVERYONE só vem de cargo customizado', () => {

    const m = computeMemberPerms('owner', 'u1', { id: 'm1', role: 'ADMIN' }, [])
    expect(m.permissions.has(PERMS.MENTION_EVERYONE)).toBe(false)

    const m2 = computeMemberPerms('owner', 'u1',
      { id: 'm1', role: 'MEMBER' },
      ['["MENTION_EVERYONE"]'],
    )
    expect(m2.permissions.has(PERMS.MENTION_EVERYONE)).toBe(true)
  })
})

describe('parsePermissionsJson — extras', () => {
  it('array misto: só strings válidas passam', () => {
    expect(parsePermissionsJson('["MANAGE_ROLES",123,null,true,"BAN_MEMBERS"]'))
      .toEqual(['MANAGE_ROLES', 'BAN_MEMBERS'])
  })

  it('array só de não-strings → vazio', () => {
    expect(parsePermissionsJson('[1,2,3]')).toEqual([])
    expect(parsePermissionsJson('[null,null]')).toEqual([])
  })

  it('case-sensitive: "manage_roles" lowercase é descartado', () => {
    expect(parsePermissionsJson('["manage_roles"]')).toEqual([])
  })

  it('whitespace dentro do JSON ok', () => {
    expect(parsePermissionsJson('[  "MANAGE_ROLES"  ,  "KICK_MEMBERS"  ]'))
      .toEqual(['MANAGE_ROLES', 'KICK_MEMBERS'])
  })
})

describe('computeChannelVisibility', () => {
  const base = {
    ownerId:      'owner-1',
    userId:       'u1',
    isMember:     true,
    isPrivate:    false,
    userRoleIds:  [] as string[],
    allowedRoles: [] as string[],
  }

  it('owner sempre vê — público ou privado, member ou não', () => {
    expect(computeChannelVisibility({ ...base, userId: 'owner-1' })).toBe(true)
    expect(computeChannelVisibility({
      ...base, userId: 'owner-1', isPrivate: true, isMember: false,
    })).toBe(true)
  })

  it('ownerId null com userId null não dá match (guard)', () => {

    expect(computeChannelVisibility({
      ...base, ownerId: null, userId: '', isMember: false,
    })).toBe(false)
  })

  it('não-membro nunca vê', () => {
    expect(computeChannelVisibility({ ...base, isMember: false })).toBe(false)
    expect(computeChannelVisibility({
      ...base, isMember: false, isPrivate: true,
      userRoleIds: ['r1'], allowedRoles: ['r1'],
    })).toBe(false)
  })

  it('canal público + membro → vê', () => {
    expect(computeChannelVisibility({ ...base, isPrivate: false })).toBe(true)
  })

  it('privado: precisa role intersection', () => {
    expect(computeChannelVisibility({
      ...base, isPrivate: true,
      userRoleIds: ['r1', 'r2'], allowedRoles: ['r3', 'r2'],
    })).toBe(true)
  })

  it('privado sem allowedRoles configurados → ninguém (exceto owner) vê', () => {
    expect(computeChannelVisibility({
      ...base, isPrivate: true,
      userRoleIds: ['r1'], allowedRoles: [],
    })).toBe(false)
  })

  it('privado: user sem roles → não vê', () => {
    expect(computeChannelVisibility({
      ...base, isPrivate: true,
      userRoleIds: [], allowedRoles: ['r1'],
    })).toBe(false)
  })

  it('privado: roles do user disjuntas → não vê', () => {
    expect(computeChannelVisibility({
      ...base, isPrivate: true,
      userRoleIds: ['r-x', 'r-y'], allowedRoles: ['r-a', 'r-b'],
    })).toBe(false)
  })

  it('aceita Set como allowedRoles (uso interno)', () => {
    expect(computeChannelVisibility({
      ...base, isPrivate: true,
      userRoleIds: ['r1'], allowedRoles: new Set(['r1', 'r2']),
    })).toBe(true)
  })
})
