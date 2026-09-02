import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { Server as SocketServer } from 'socket.io'
import { attachRealtime } from './realtime'
import { canalSumiu, categoriaMudou, categoriaSumiu } from './canais'

function fakeIo() {
  const emit = vi.fn()
  const to = vi.fn(() => ({ emit }))
  return { io: { to, emit: vi.fn() } as unknown as SocketServer, to, emit }
}

describe('avisos de canal e categoria', () => {
  let f: ReturnType<typeof fakeIo>
  beforeEach(() => { f = fakeIo(); attachRealtime(f.io) })

  it('canal excluido leva so o id — nao vaza o nome pra quem nao via o canal', () => {
    canalSumiu('srv1', 'ch1')
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_channel_gone', { serverId: 'srv1', channelId: 'ch1' })
    expect(JSON.stringify(f.emit.mock.calls)).not.toContain('name')
  })

  it('categoria vai inteira — categoria nao tem privacidade', () => {
    const categoria = { id: 'cat1', name: 'Vozes', position: 2, botEnabled: null }
    categoriaMudou('srv1', categoria)
    expect(f.to).toHaveBeenCalledWith('server:srv1')
    expect(f.emit).toHaveBeenCalledWith('server_category_upserted', { serverId: 'srv1', categoria })
  })

  it('categoria excluida leva so o id', () => {
    categoriaSumiu('srv1', 'cat1')
    expect(f.emit).toHaveBeenCalledWith('server_category_gone', { serverId: 'srv1', categoryId: 'cat1' })
  })

  it('nao explode quando o io nunca foi ligado', () => {
    attachRealtime(undefined as unknown as SocketServer)
    expect(() => {
      canalSumiu('srv1', 'ch1')
      categoriaSumiu('srv1', 'cat1')
    }).not.toThrow()
  })
})
