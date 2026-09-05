import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { Server as SocketServer } from 'socket.io'
import {
  attachRealtime, joinedServer, presenceChanged,
  serverGone, leftServer, profileChanged,
} from './realtime'
import { salasQueMeVeem } from './quemMeVe'

vi.mock('./quemMeVe', () => ({ salasQueMeVeem: vi.fn() }))
const quemMeVe = vi.mocked(salasQueMeVeem)

function fakeIo() {
  const emit = vi.fn()
  const to = vi.fn(function encadeia() { return { to, emit } })
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
  beforeEach(() => {
    f = fakeIo()
    attachRealtime(f.io)
    quemMeVe.mockReset()
    quemMeVe.mockResolvedValue(['user:u1', 'server:s1', 'user:amigo'])
  })

  it('status normal vai como esta', async () => {
    await presenceChanged('u1', 'DND')
    expect(f.emit).toHaveBeenCalledWith('presence_update', { userId: 'u1', status: 'DND' })
  })

  it('INVISIVEL sai como OFFLINE — e o ponto inteiro de ser invisivel', async () => {
    await presenceChanged('u1', 'INVISIBLE')
    expect(f.emit).toHaveBeenCalledWith('presence_update', { userId: 'u1', status: 'OFFLINE' })
    const enviado = f.emit.mock.calls.map((c) => JSON.stringify(c)).join()
    expect(enviado).not.toContain('INVISIBLE')
  })

  it('vai para as constelacoes e amigos, e NAO para todo mundo', async () => {
    await presenceChanged('u1', 'ONLINE')
    expect(f.to).toHaveBeenCalledWith('server:s1')
    expect(f.to).toHaveBeenCalledWith('user:amigo')
    expect(f.global).not.toHaveBeenCalled()
  })

  it('se nao der para descobrir quem ve, avisa todo mundo em vez de ninguem', async () => {
    quemMeVe.mockResolvedValue(null)
    await presenceChanged('u1', 'ONLINE')
    expect(f.global).toHaveBeenCalledWith('presence_update', { userId: 'u1', status: 'ONLINE' })
  })

  it('lista vazia tambem cai no plano B — silencio seria pior', async () => {
    quemMeVe.mockResolvedValue([])
    await presenceChanged('u1', 'ONLINE')
    expect(f.global).toHaveBeenCalledWith('presence_update', { userId: 'u1', status: 'ONLINE' })
  })
})

describe('perfil mudou', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => {
    f = fakeIo()
    attachRealtime(f.io)
    quemMeVe.mockReset()
    quemMeVe.mockResolvedValue(['user:u1', 'server:s1'])
  })

  const publico = { username: 'ana', displayName: 'Ana', avatarUrl: null, displayFont: null }

  it('vai com o perfil dentro, pra ninguem precisar buscar', async () => {
    await profileChanged('u1', publico)
    expect(f.emit).toHaveBeenCalledWith('profile_updated', { userId: 'u1', publico })
  })

  it('sem perfil, o aviso nao inventa a chave — e ausente significa "a lista nao mudou"', async () => {
    await profileChanged('u1')
    expect(f.emit).toHaveBeenCalledWith('profile_updated', { userId: 'u1' })
  })

  it('o perfil nao sai para quem nao divide constelacao nem amizade', async () => {
    await profileChanged('u1', publico)
    expect(f.global).not.toHaveBeenCalled()
    expect(f.to).toHaveBeenCalledWith('server:s1')
  })
})

describe('sem io (boot, testes, worker)', () => {
  it('nao explode quando o io nunca foi ligado', async () => {
    attachRealtime(undefined as unknown as SocketServer)
    serverGone('srv1')
    await expect(presenceChanged('u1', 'ONLINE')).resolves.toBeUndefined()
  })
})
