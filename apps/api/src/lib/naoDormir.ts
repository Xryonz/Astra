import { env } from './env'
import { logger } from './logger'

const INTERVALO_MS = 10 * 60 * 1000
const TEMPO_LIMITE_MS = 20_000
const FUSO_DA_CASA = 'America/Sao_Paulo'
const COMECA_A_DORMIR = 0
const VOLTA_A_ACORDAR = 7

const relogioDaCasa = new Intl.DateTimeFormat('en-US', {
  timeZone: FUSO_DA_CASA,
  hour: '2-digit',
  hourCycle: 'h23',
})

export function horaDeDeixarDormir(quando: Date): boolean {
  const hora = Number(relogioDaCasa.format(quando))
  return hora >= COMECA_A_DORMIR && hora < VOLTA_A_ACORDAR
}

export function enderecoParaSeCutucar(bruto: string | undefined): string | null {
  if (!bruto) return null
  try {
    const url = new URL(bruto)
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null
    if (url.hostname === 'localhost' || url.hostname === '127.0.0.1') return null
    return `${url.origin}/live`
  } catch {
    return null
  }
}

export function naoDeixarDormir(): void {
  if (env.NODE_ENV !== 'production') return
  const alvo = enderecoParaSeCutucar(env.RENDER_EXTERNAL_URL ?? env.API_URL)
  if (!alvo) {
    logger.info('NaoDormir', 'sem endereco publico — dependendo so do robo externo')
    return
  }
  const relogio = setInterval(() => {
    if (horaDeDeixarDormir(new Date())) return
    void fetch(alvo, {
      signal: AbortSignal.timeout(TEMPO_LIMITE_MS),
      headers: { 'user-agent': 'Astra-NaoDormir' },
    }).catch((e) => logger.warn('NaoDormir', `nao consegui me cutucar: ${String(e)}`))
  }, INTERVALO_MS)
  relogio.unref()
  logger.info(
    'NaoDormir',
    `cutucando ${alvo} a cada ${INTERVALO_MS / 60_000} min, menos das ${COMECA_A_DORMIR}h as ${VOLTA_A_ACORDAR}h`,
  )
}
