import { describe, it, expect, vi, afterEach } from 'vitest'
import { createHmac } from 'crypto'

async function carregarGeloCom(ambiente: Record<string, string>) {
  vi.resetModules()
  for (const [k, v] of Object.entries(ambiente)) vi.stubEnv(k, v)
  return import('./gelo')
}

const SEM_TURN = {
  STUN_URLS: '', TURN_URLS: '', TURN_SECRET: '', TURN_USERNAME: '', TURN_PASSWORD: '',
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('servidores de gelo (ICE)', () => {
  it('sem TURN configurado, entrega só STUN — e mais de um, para não haver ponto único', async () => {
    const { servidoresDeGelo } = await carregarGeloCom(SEM_TURN)

    const servidores = servidoresDeGelo('u_123')
    expect(servidores).toHaveLength(1)
    expect(servidores[0].urls.length).toBeGreaterThan(1)
    expect(servidores[0].username).toBeUndefined()
    expect(servidores[0].credential).toBeUndefined()
  })

  it('com TURN_URLS mas sem credencial nenhuma, NÃO oferece o TURN', async () => {
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN, TURN_URLS: 'turn:relay.astra:3478',
    })

    expect(servidoresDeGelo('u_123')).toHaveLength(1)
  })

  it('com segredo, a credencial é efêmera e o segredo não sai do servidor', async () => {
    const segredo = 'segredo-de-teste-do-turn'
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN,
      TURN_URLS: 'turn:relay.astra:3478?transport=udp,turn:relay.astra:3478?transport=tcp',
      TURN_SECRET: segredo,
      TURN_TTL: '3600',
    })

    const agora = 1_800_000_000_000
    const [, turn] = servidoresDeGelo('u_123', agora)

    expect(turn.urls).toHaveLength(2)
    expect(turn.username).toBe(`${Math.floor(agora / 1000) + 3600}:u_123`)
    expect(turn.credential).toBe(
      createHmac('sha1', segredo).update(turn.username!).digest('base64'),
    )

    const inteiro = JSON.stringify(servidoresDeGelo('u_123', agora))
    expect(inteiro).not.toContain(segredo)
  })

  it('a validade fica no futuro e cada pessoa recebe a sua', async () => {
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN, TURN_URLS: 'turn:relay.astra:3478', TURN_SECRET: 's', TURN_TTL: '3600',
    })

    const agora = 1_800_000_000_000
    const [, minha] = servidoresDeGelo('u_123', agora)
    const [, dela] = servidoresDeGelo('u_456', agora)

    expect(Number(minha.username!.split(':')[0])).toBeGreaterThan(agora / 1000)
    expect(minha.credential).not.toBe(dela.credential)
  })

  it('id com dois-pontos não quebra o formato que o coturn espera', async () => {
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN, TURN_URLS: 'turn:relay.astra:3478', TURN_SECRET: 's',
    })

    const [, turn] = servidoresDeGelo('u:12:3')
    expect(turn.username!.split(':')).toHaveLength(2)
  })

  it('provedor de credencial fixa também serve', async () => {
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN,
      TURN_URLS: 'turn:relay.provedor:3478',
      TURN_USERNAME: 'quem',
      TURN_PASSWORD: 'senha',
    })

    const [, turn] = servidoresDeGelo('u_123')
    expect(turn.username).toBe('quem')
    expect(turn.credential).toBe('senha')
  })

  it('variável deixada vazia no painel não derruba o servidor nem oferece TURN quebrado', async () => {
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN,
      TURN_TTL: '',
      TURN_URLS: 'turn:relay.astra:3478',
      TURN_SECRET: '   ',
    })

    expect(servidoresDeGelo('u_123')).toHaveLength(1)
  })

  it('STUN_URLS substitui a lista de fábrica', async () => {
    const { servidoresDeGelo } = await carregarGeloCom({
      ...SEM_TURN, STUN_URLS: 'stun:meu.servidor:3478',
    })

    expect(servidoresDeGelo('u_123')[0].urls).toEqual(['stun:meu.servidor:3478'])
  })
})
