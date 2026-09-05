import { redis } from './redis'
import { logger } from './logger'
import { urlUsavel, apontaParaForaDaRede } from './enderecoSeguro'

const SALTOS_MAXIMOS = 3
const PRAZO_MS = 5_000
const TETO_DE_BYTES = 512 * 1024
const VALIDADE_SEGUNDOS = 60 * 60 * 24
const VALIDADE_DO_FRACASSO = 60 * 10

export interface CartaoDeLink {
  url: string
  titulo?: string
  descricao?: string
  imagem?: string
  site?: string
}

function decodificarEntidades(s: string): string {
  return s
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&amp;/g, '&')
}

function meta(html: string, chaves: string[]): string | undefined {
  for (const chave of chaves) {
    const alvo = chave.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const padroes = [
      new RegExp(`<meta[^>]+(?:property|name)=["']${alvo}["'][^>]+content=["']([^"']*)["']`, 'i'),
      new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']${alvo}["']`, 'i'),
    ]
    for (const p of padroes) {
      const achado = html.match(p)?.[1]
      if (achado?.trim()) return decodificarEntidades(achado.trim())
    }
  }
  return undefined
}

function encurtar(s: string | undefined, teto: number): string | undefined {
  if (!s) return undefined
  return s.length <= teto ? s : `${s.slice(0, teto - 1)}…`
}

async function lerComTeto(resposta: Response): Promise<string> {
  const tamanho = Number(resposta.headers.get('content-length') ?? 0)
  if (tamanho > TETO_DE_BYTES) throw new Error('resposta grande demais')

  const leitor = resposta.body?.getReader()
  if (!leitor) return ''

  const pedacos: Uint8Array[] = []
  let lidos = 0
  for (;;) {
    const { done, value } = await leitor.read()
    if (done) break
    if (!value) continue
    lidos += value.length
    if (lidos > TETO_DE_BYTES) {
      await leitor.cancel().catch(() => {})
      break
    }
    pedacos.push(value)
  }
  return Buffer.concat(pedacos).toString('utf8')
}

async function buscarPagina(inicial: URL): Promise<{ html: string; final: URL } | null> {
  let atual = inicial
  for (let salto = 0; salto <= SALTOS_MAXIMOS; salto++) {
    if (!(await apontaParaForaDaRede(atual.hostname))) return null

    const resposta = await fetch(atual, {
      redirect: 'manual',
      signal: AbortSignal.timeout(PRAZO_MS),
      headers: { accept: 'text/html,application/xhtml+xml', 'user-agent': 'AstraBot/1.0 (+link preview)' },
    }).catch(() => null)
    if (!resposta) return null

    if (resposta.status >= 300 && resposta.status < 400) {
      const destino = resposta.headers.get('location')
      if (!destino) return null
      const seguinte = urlUsavel(new URL(destino, atual).toString())
      if (!seguinte) return null
      atual = seguinte
      continue
    }

    if (!resposta.ok) return null
    if (!(resposta.headers.get('content-type') ?? '').includes('html')) return null
    return { html: await lerComTeto(resposta), final: atual }
  }
  return null
}

function lerCartao(html: string, final: URL): CartaoDeLink | null {
  const titulo = meta(html, ['og:title', 'twitter:title']) ??
    decodificarEntidades(html.match(/<title[^>]*>([^<]*)<\/title>/i)?.[1]?.trim() ?? '')
  const descricao = meta(html, ['og:description', 'twitter:description', 'description'])
  const imagemCrua = meta(html, ['og:image:secure_url', 'og:image', 'twitter:image'])

  let imagem: string | undefined
  if (imagemCrua) {
    const absoluta = urlUsavel(new URL(imagemCrua, final).toString())
    imagem = absoluta?.toString()
  }

  if (!titulo && !descricao && !imagem) return null
  return {
    url: final.toString(),
    titulo: encurtar(titulo || undefined, 140),
    descricao: encurtar(descricao, 240),
    imagem,
    site: meta(html, ['og:site_name']) ?? final.hostname,
  }
}

export async function cartaoDoLink(cru: string): Promise<CartaoDeLink | null> {
  const alvo = urlUsavel(cru)
  if (!alvo) return null

  const chave = `unfurl:${alvo.toString()}`
  const guardado = await redis.get(chave).catch(() => null)
  if (guardado) return guardado === 'nao' ? null : (JSON.parse(guardado) as CartaoDeLink)

  const pagina = await buscarPagina(alvo).catch((e) => {
    logger.warn('Unfurl', `não consegui ler ${alvo.hostname}: ${e?.message ?? e}`)
    return null
  })
  const cartao = pagina ? lerCartao(pagina.html, pagina.final) : null

  await redis
    .set(chave, cartao ? JSON.stringify(cartao) : 'nao', 'EX', cartao ? VALIDADE_SEGUNDOS : VALIDADE_DO_FRACASSO)
    .catch(() => {})

  return cartao
}
