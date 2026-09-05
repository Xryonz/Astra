export const DESEJO_MINIMO = 4
export const DESEJO_MAXIMO = 500

export const DESEJOS_NA_JANELA = 3
export const JANELA_DO_DESEJO_MS = 10 * 60_000

// eslint-disable-next-line no-control-regex
const CARACTERE_DE_CONTROLE = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g

export function limparDesejo(cru: string): string {
  return cru
    .normalize('NFKC')
    .replace(CARACTERE_DE_CONTROLE, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

const TETO_DE_PESSOAS_LEMBRADAS = 500
const recentes = new Map<string, number[]>()

export function podeDesejar(userId: string, agora: number = Date.now()): boolean {
  if (recentes.size > TETO_DE_PESSOAS_LEMBRADAS) esquecerVencidos(agora)
  const marcas = (recentes.get(userId) ?? []).filter((t) => agora - t < JANELA_DO_DESEJO_MS)
  if (marcas.length >= DESEJOS_NA_JANELA) {
    recentes.set(userId, marcas)
    return false
  }
  marcas.push(agora)
  recentes.set(userId, marcas)
  return true
}

function esquecerVencidos(agora: number): void {
  for (const [id, marcas] of recentes) {
    const vivas = marcas.filter((t) => agora - t < JANELA_DO_DESEJO_MS)
    if (vivas.length === 0) recentes.delete(id)
    else recentes.set(id, vivas)
  }
}
