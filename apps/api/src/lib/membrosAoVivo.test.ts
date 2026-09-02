import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { Server as SocketServer } from 'socket.io'
import { attachRealtime } from './realtime'
import { membroSaiu, membroMudouDeCargo } from './membrosAoVivo'

function fakeIo() {
  const emit = vi.fn()
  const to = vi.fn(() => ({ emit }))
  return { io: { to, emit: vi.fn() } as unknown as SocketServer, to, emit }
}

describe('deltas de membro', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => { f = fakeIo(); attachRealtime(f.io) })

  it('saiu leva so o id — quem ouve tira da lista sem buscar nada', () => {
    membroSaiu('srv1', 'u9')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_member_removed', { serverId: 'srv1', userId: 'u9' })
  })

  it('cargo trocado leva o id do membro, nao o do usuario', () => {
    membroMudouDeCargo('srv1', 'm7', 'ADMIN')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_member_role', { serverId: 'srv1', memberId: 'm7', role: 'ADMIN' })
  })

  it('nao explode quando o io nunca foi ligado', () => {
    attachRealtime(undefined as unknown as SocketServer)
    expect(() => {
      membroSaiu('srv1', 'u9')
      membroMudouDeCargo('srv1', 'm7', 'MEMBER')
    }).not.toThrow()
  })
})
