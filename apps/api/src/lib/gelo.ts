import { createHmac } from 'crypto'
import { env } from './env'

export type ServidorDeGelo = {
  urls: string[]
  username?: string
  credential?: string
}

const STUN_PADRAO = [
  'stun:stun.l.google.com:19302',
  'stun:stun1.l.google.com:19302',
  'stun:stun.cloudflare.com:3478',
]

function lista(bruto: string | undefined): string[] {
  return (bruto ?? '').split(',').map((s) => s.trim()).filter(Boolean)
}

function texto(bruto: string | undefined): string {
  return (bruto ?? '').trim()
}

export function servidoresDeStun(): string[] {
  const daCasa = lista(env.STUN_URLS)
  return daCasa.length > 0 ? daCasa : STUN_PADRAO
}

export function servidorDeTurn(userId: string, agoraMs = Date.now()): ServidorDeGelo | null {
  const urls = lista(env.TURN_URLS)
  if (urls.length === 0) return null

  const segredo = texto(env.TURN_SECRET)
  if (segredo) {
    const expira = Math.floor(agoraMs / 1000) + env.TURN_TTL
    const username = `${expira}:${userId.replace(/:/g, '')}`
    const credential = createHmac('sha1', segredo).update(username).digest('base64')
    return { urls, username, credential }
  }

  const usuario = texto(env.TURN_USERNAME)
  const senha = texto(env.TURN_PASSWORD)
  if (usuario && senha) return { urls, username: usuario, credential: senha }

  return null
}

export function servidoresDeGelo(userId: string, agoraMs = Date.now()): ServidorDeGelo[] {
  const saida: ServidorDeGelo[] = [{ urls: servidoresDeStun() }]
  const turn = servidorDeTurn(userId, agoraMs)
  if (turn) saida.push(turn)
  return saida
}
