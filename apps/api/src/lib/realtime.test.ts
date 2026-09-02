import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { Server as SocketServer } from 'socket.io'
import {
  attachRealtime, joinedServer, presenceChanged,
  serverGone, leftServer, profileChanged,
} from './realtime'

function fakeIo() {
  const emit = vi.fn()
  const to = vi.fn(() => ({ emit }))
  const global = vi.fn()
  return { io: { to, emit: global } as unknown as SocketServer, to, emit, global }
}

describe('avisos de constelacao', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => { f = fakeIo(); attachRealtime(f.io) })

  it('fui adicionado -> sala PESSOAL, nao a da constelacao', () => {
    joinedServer('u9', 'srv1')
    expect(f.to).toHaveBeenCalledWith('user:u9')
    expect(f.to).not.toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_joined', { serverId: 'srv1' })
  })

  it('constelacao apagada -> avisa a sala DELA (o aviso tem que sair antes do delete)', () => {
    serverGone('srv1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_gone', { serverId: 'srv1' })
  })

  it('perdi acesso -> sala PESSOAL (banido ja saiu da sala da constelacao)', () => {
    leftServer('u9', 'srv1')
    expect(f.to).toHaveBeenCalledWith('user:u9')
    expect(f.to).not.toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_left', { serverId: 'srv1', motivo: 'saiu', reason: null })
  })

  it('expulsao e banimento viajam com o motivo (e o banimento com a justificativa)', () => {
    leftServer('u9', 'srv1', 'expulso')
    expect(f.emit).toHaveBeenCalledWith('server_left', { serverId: 'srv1', motivo: 'expulso', reason: null })

    leftServer('u9', 'srv1', 'banido', 'spam')
    expect(f.emit).toHaveBeenCalledWith('server_left', { serverId: 'srv1', motivo: 'banido', reason: 'spam' })
  })
})

describe('presenca', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => { f = fakeIo(); attachRealtime(f.io) })

  it('status normal vai como esta, pra todo mundo', () => {
    presenceChanged('u1', 'DND')
    expect(f.global).toHaveBeenCalledWith('presence_update', { userId: 'u1', status: 'DND' })
  })

  it('INVISIVEL sai como OFFLINE — e o ponto inteiro de ser invisivel', () => {
    presenceChanged('u1', 'INVISIBLE')
    expect(f.global).toHaveBeenCalledWith('presence_update', { userId: 'u1', status: 'OFFLINE' })
    const enviado = f.global.mock.calls.map((c) => JSON.stringify(c)).join()
    expect(enviado).not.toContain('INVISIBLE')
  })
})

describe('perfil mudou', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => { f = fakeIo(); attachRealtime(f.io) })

  const publico = { username: 'ana', displayName: 'Ana', avatarUrl: null, displayFont: null }

  it('vai com o perfil dentro, pra ninguem precisar buscar', () => {
    profileChanged('u1', publico)
    expect(f.global).toHaveBeenCalledWith('profile_updated', { userId: 'u1', publico })
  })

  it('sem perfil, o aviso nao inventa a chave — e ausente significa "a lista nao mudou"', () => {
    profileChanged('u1')
    expect(f.global).toHaveBeenCalledWith('profile_updated', { userId: 'u1' })
  })
})

describe('sem io (boot, testes, worker)', () => {
  it('nao explode quando o io nunca foi ligado', () => {
    attachRealtime(undefined as unknown as SocketServer)
    expect(() => {
      serverGone('srv1')
      presenceChanged('u1', 'ONLINE')
    }).not.toThrow()
  })
})
