import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { Server as SocketServer } from 'socket.io'
import {
  attachRealtime, channelsChanged, membersChanged, joinedServer, presenceChanged,
  serverUpdated, serverGone, rolesChanged, leftServer,
} from './realtime'

// Trava o CONTRATO dos avisos de tempo real: nome da sala, nome do evento e o
// que vai no payload.
//
// Por que este teste existe: canal novo so aparecia pros outros quando eles
// reabriam o app, porque simplesmente NAO HAVIA evento. Erros dessa familia
// (sala errada, evento renomeado num lado so, payload sem o id) nao quebram
// nada — o app segue rodando, apenas mudo. Sao os mais caros de achar, porque
// so aparecem com DUAS pontas e ninguem testa assim por acidente.

function fakeIo() {
  const emit = vi.fn()
  const to = vi.fn(() => ({ emit }))
  const global = vi.fn()
  return { io: { to, emit: global } as unknown as SocketServer, to, emit, global }
}

describe('avisos de constelacao', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => { f = fakeIo(); attachRealtime(f.io) })

  it('canal mudou -> sala da constelacao', () => {
    channelsChanged('srv1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_channels', { serverId: 'srv1' })
  })

  it('membros mudaram -> sala da constelacao', () => {
    membersChanged('srv1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_members', { serverId: 'srv1' })
  })

  it('fui adicionado -> sala PESSOAL, nao a da constelacao', () => {
    // Detalhe que importa: quem acabou de ser adicionado ainda nao esta na sala
    // do servidor, entao mandar pra la nao chegaria em ninguem.
    joinedServer('u9', 'srv1')
    expect(f.to).toHaveBeenCalledWith('user:u9')
    expect(f.to).not.toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_joined', { serverId: 'srv1' })
  })

  it('constelacao editada -> sala da constelacao', () => {
    serverUpdated('srv1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_updated', { serverId: 'srv1' })
  })

  it('cargos mexeram -> sala da constelacao', () => {
    rolesChanged('srv1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_roles', { serverId: 'srv1' })
  })

  it('constelacao apagada -> avisa a sala DELA (o aviso tem que sair antes do delete)', () => {
    // Depois do DELETE nao existe mais membro no banco pra descobrir quem avisar.
    // Se este teste virar "manda pra sala pessoal de cada um", quebrou o contrato.
    serverGone('srv1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_gone', { serverId: 'srv1' })
  })

  it('perdi acesso -> sala PESSOAL (banido ja saiu da sala da constelacao)', () => {
    leftServer('u9', 'srv1')
    expect(f.to).toHaveBeenCalledWith('user:u9')
    expect(f.to).not.toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_left', { serverId: 'srv1' })
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
    // Nunca pode vazar o valor real pra fora.
    const enviado = f.global.mock.calls.map((c) => JSON.stringify(c)).join()
    expect(enviado).not.toContain('INVISIBLE')
  })
})

describe('sem io (boot, testes, worker)', () => {
  it('nao explode quando o io nunca foi ligado', () => {
    attachRealtime(undefined as unknown as SocketServer)
    expect(() => {
      channelsChanged('srv1')
      presenceChanged('u1', 'ONLINE')
    }).not.toThrow()
  })
})
