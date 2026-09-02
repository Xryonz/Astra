import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { Server as SocketServer } from 'socket.io'
import { attachRealtime } from './realtime'
import { membroSaiu, membroMudouDeCargo, corQueVence, type CargoDoMembro } from './membros'

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

describe('cor que vence', () => {
  const cargo = (id: string, position: number, color: string | null): CargoDoMembro =>
    ({ id, name: id, color, iconUrl: null, position, hoist: false })

  it('e a do cargo mais alto que TEM cor, nao a do mais alto', () => {
    const cargos = [cargo('chefe', 9, null), cargo('veterano', 5, '#c9a96e'), cargo('todos', 0, '#888888')]
    expect(corQueVence(cargos)).toBe('#c9a96e')
  })

  it('sem nenhum cargo colorido, nao ha cor — o nome fica com a do tema', () => {
    expect(corQueVence([cargo('chefe', 9, null)])).toBeNull()
    expect(corQueVence([])).toBeNull()
  })
})
