import { lookup } from 'node:dns/promises'
import net from 'node:net'

const FAIXAS_V4_BLOQUEADAS: Array<[string, number]> = [
  ['0.0.0.0', 8],
  ['10.0.0.0', 8],
  ['100.64.0.0', 10],
  ['127.0.0.0', 8],
  ['169.254.0.0', 16],
  ['172.16.0.0', 12],
  ['192.0.0.0', 24],
  ['192.0.2.0', 24],
  ['192.88.99.0', 24],
  ['192.168.0.0', 16],
  ['198.18.0.0', 15],
  ['198.51.100.0', 24],
  ['203.0.113.0', 24],
  ['224.0.0.0', 4],
  ['240.0.0.0', 4],
]

function paraInteiro(ip: string): number {
  return ip.split('.').reduce((acc, parte) => (acc << 8) + Number(parte), 0) >>> 0
}

export function ehV4Publico(ip: string): boolean {
  const alvo = paraInteiro(ip)
  return !FAIXAS_V4_BLOQUEADAS.some(([base, bits]) => {
    const mascara = bits === 0 ? 0 : (0xffffffff << (32 - bits)) >>> 0
    return (alvo & mascara) === (paraInteiro(base) & mascara)
  })
}

export function ehV6Publico(ip: string): boolean {
  const cru = ip.toLowerCase().split('%')[0]
  if (cru === '::' || cru === '::1') return false
  if (cru.startsWith('fe80:')) return false
  if (cru.startsWith('ff')) return false
  const primeiro = parseInt(cru.split(':')[0] || '0', 16)
  if ((primeiro & 0xfe00) === 0xfc00) return false
  const mapeado = cru.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/)
  if (mapeado) return ehV4Publico(mapeado[1])
  return true
}

export function semColchetes(host: string): string {
  return host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host
}

export function ehIpPublico(ip: string): boolean {
  const cru = semColchetes(ip)
  const versao = net.isIP(cru)
  if (versao === 4) return ehV4Publico(cru)
  if (versao === 6) return ehV6Publico(cru)
  return false
}

export function urlUsavel(cru: string): URL | null {
  let u: URL
  try {
    u = new URL(cru)
  } catch {
    return null
  }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return null
  if (u.username || u.password) return null
  if (!u.hostname) return null
  const host = semColchetes(u.hostname)
  if (net.isIP(host) && !ehIpPublico(host)) return null
  return u
}

export async function apontaParaForaDaRede(bruto: string): Promise<boolean> {
  const host = semColchetes(bruto)
  if (net.isIP(host)) return ehIpPublico(host)
  const achados = await lookup(host, { all: true, verbatim: true }).catch(() => [])
  if (achados.length === 0) return false
  return achados.every((a) => ehIpPublico(a.address))
}
