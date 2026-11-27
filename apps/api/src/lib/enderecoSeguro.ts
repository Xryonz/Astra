import { lookup } from 'node:dns/promises'
import net from 'node:net'

const FAIXAS_BLOQUEADAS: Array<[string, number, 'ipv4' | 'ipv6']> = [
  ['0.0.0.0', 8, 'ipv4'],
  ['10.0.0.0', 8, 'ipv4'],
  ['100.64.0.0', 10, 'ipv4'],
  ['127.0.0.0', 8, 'ipv4'],
  ['169.254.0.0', 16, 'ipv4'],
  ['172.16.0.0', 12, 'ipv4'],
  ['192.0.0.0', 24, 'ipv4'],
  ['192.0.2.0', 24, 'ipv4'],
  ['192.88.99.0', 24, 'ipv4'],
  ['192.168.0.0', 16, 'ipv4'],
  ['198.18.0.0', 15, 'ipv4'],
  ['198.51.100.0', 24, 'ipv4'],
  ['203.0.113.0', 24, 'ipv4'],
  ['224.0.0.0', 4, 'ipv4'],
  ['240.0.0.0', 4, 'ipv4'],
  ['::', 128, 'ipv6'],
  ['::1', 128, 'ipv6'],
  ['fc00::', 7, 'ipv6'],
  ['fe80::', 10, 'ipv6'],
  ['ff00::', 8, 'ipv6'],
]

const BLOQUEADOS = new net.BlockList()
for (const [base, bits, tipo] of FAIXAS_BLOQUEADAS) BLOQUEADOS.addSubnet(base, bits, tipo)

export function semColchetes(host: string): string {
  return host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host
}

export function ehIpPublico(ip: string): boolean {
  const cru = semColchetes(ip).split('%')[0]
  const versao = net.isIP(cru)
  if (versao === 0) return false
  return !BLOQUEADOS.check(cru, versao === 4 ? 'ipv4' : 'ipv6')
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
