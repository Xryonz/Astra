import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { Server, Socket } from 'socket.io'
import { setupSocket, userSockets } from './socket'

const h = vi.hoisted(() => ({
  ordem: [] as string[],
  terminarOnline: [] as Array<() => void>,
  responderMembros: [] as Array<(linhas: Array<{ serverId: string }>) => void>,
  encerradas: new Set<string>(),
}))

vi.mock('../lib/redis', () => ({
  redis: {},
  vozKeys: { membro: () => 'voz' },
  VOZ_TTL_SEGUNDOS: 60,
  isTokenBlacklisted: async () => false,
  isSessaoEncerrada: async (sessao: string) => h.encerradas.has(sessao),
  setUserOnline: () =>
    new Promise<void>((pronto) => {
      h.terminarOnline.push(() => {
        h.ordem.push('online')
        pronto()
      })
    }),
  setUserOffline: async () => {
    h.ordem.push('offline')
  },
  refreshPresence: async () => {},
  setUserActivity: async () => null,
  clearUserActivity: async () => {},
}))

vi.mock('../db', () => ({
  db: {
    select: () => ({
      from: () => ({
        where: () =>
          new Promise<Array<{ serverId: string }>>((responder) => {
            h.responderMembros.push(responder)
          }),
      }),
    }),
  },
}))

vi.mock('../db/prepared', () => ({ selectAuthorById: {}, selectMemberColor: {} }))
vi.mock('../lib/notifications', () => ({ notify: vi.fn(), resumoDaMensagem: vi.fn() }))
vi.mock('../lib/spamDetector', () => ({
  trackMessage: vi.fn(),
  isUserMuted: vi.fn(),
  muteUser: vi.fn(),
  getMuteExpiry: vi.fn(),
}))
vi.mock('../lib/bot', () => ({
  getBotId: vi.fn(),
  askBot: vi.fn(),
  handleBotCommand: vi.fn(),
  prefixoUsado: vi.fn(),
  semPrefixo: vi.fn(),
  sincronizaPersona: vi.fn(),
}))
vi.mock('../lib/botSussurro', () => ({ responderNoSussurro: vi.fn() }))
vi.mock('../lib/mentions', () => ({ parseMentions: vi.fn() }))
vi.mock('../lib/xp', () => ({ xpPorMensagem: vi.fn() }))
vi.mock('../lib/botAvisos', () => ({ comemorarNivel: vi.fn() }))
vi.mock('../lib/missoes', () => ({ eventoDeMissao: vi.fn() }))
vi.mock('../lib/blocks', () => ({ haBloqueio: vi.fn() }))
vi.mock('../lib/botScope', () => ({ botNaOrbita: vi.fn() }))
vi.mock('../lib/dmCalls', () => ({ registrarChamadasDeSussurro: () => {} }))
vi.mock('../lib/realtime', () => ({ entregarSussurro: vi.fn() }))
vi.mock('../lib/quemMeVe', () => ({ salasQueMeVeem: async () => null }))
vi.mock('../lib/permissions', () => ({ membrosQueVeemCanal: vi.fn(), userCanSeeChannel: vi.fn() }))
vi.mock('../lib/fichaDoCanal', () => ({ fichaDoCanal: vi.fn() }))

function soqueteFalso(sid?: string) {
  const ouvintes = new Map<string, (...args: unknown[]) => unknown>()
  const salas: string[] = []
  const semNinguem = { emit: () => {} }
  const socket = {
    id: 'sock-1',
    data: { userId: 'u1', status: 'ONLINE', sid },
    disconnected: false,
    disconnect: vi.fn(),
    rooms: new Set<string>(),
    on: (evento: string, ouvinte: (...args: unknown[]) => unknown) => {
      ouvintes.set(evento, ouvinte)
    },
    join: (sala: string) => {
      salas.push(sala)
    },
    to: () => semNinguem,
    broadcast: { emit: () => {}, to: () => semNinguem },
  }
  return { socket, ouvintes, salas }
}

function conectar(socket: unknown) {
  let aoConectar: ((s: Socket) => void) | undefined
  const io = {
    use: () => {},
    on: (evento: string, ouvinte: (s: Socket) => void) => {
      if (evento === 'connection') aoConectar = ouvinte
    },
    to: () => ({ emit: () => {} }),
  }
  setupSocket(io as unknown as Server)
  aoConectar?.(socket as Socket)
}

describe('chegada de uma conexao', () => {
  beforeEach(() => {
    h.ordem.length = 0
    h.terminarOnline.length = 0
    h.responderMembros.length = 0
    h.encerradas.clear()
    userSockets.clear()
  })

  it('sessao encerrada entre a conferencia do aperto de mao e a entrada na sala: a conexao cai', async () => {
    h.encerradas.add('s-morta')
    const { socket, salas } = soqueteFalso('s-morta')
    conectar(socket)
    expect(salas).toEqual(['user:u1'])
    await vi.waitFor(() => expect(socket.disconnect).toHaveBeenCalledWith(true))
  })

  it('sessao valendo continua conectada depois de entrar na sala', async () => {
    const { socket } = soqueteFalso('s-viva')
    conectar(socket)
    await new Promise((pronto) => setTimeout(pronto, 20))
    expect(socket.disconnect).not.toHaveBeenCalled()
  })

  it('os pedidos de entrar ja tem ouvinte enquanto o Redis e o banco ainda respondem', () => {
    const { socket, ouvintes, salas } = soqueteFalso()
    conectar(socket)

    expect([...ouvintes.keys()]).toEqual(
      expect.arrayContaining(['join_channel', 'join_dm', 'join_server', 'fast_send_text', 'disconnect']),
    )
    expect(salas).toEqual(['user:u1'])
    expect(h.terminarOnline).toHaveLength(1)
  })

  it('quem cai antes da chegada terminar sai como offline depois do online, sem entrar nas constelacoes', async () => {
    const { socket, ouvintes, salas } = soqueteFalso()
    conectar(socket)

    socket.disconnected = true
    const saida = ouvintes.get('disconnect')?.()
    h.terminarOnline.shift()?.()
    await saida

    expect(h.ordem).toEqual(['online', 'offline'])
    expect(salas).toEqual(['user:u1'])
    expect(h.responderMembros).toHaveLength(0)
  })

  it('a sala de cada constelacao entra quando o banco responde', async () => {
    const { socket, salas } = soqueteFalso()
    conectar(socket)

    h.terminarOnline.shift()?.()
    await vi.waitFor(() => expect(h.responderMembros).toHaveLength(1))
    h.responderMembros.shift()?.([{ serverId: 's1' }, { serverId: 's2' }])

    await vi.waitFor(() => expect(salas).toEqual(['user:u1', 'server:s1', 'server:s2']))
  })
})
