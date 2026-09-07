const APLICATIVO = /^\d{5,25}$/
const IMPRESSAO = /^[0-9a-f]{32}$/

export const LADO_DA_ARTE = 128

export type ArteDeAtividade = {
  aplicativo: string
  impressao: string
}

export function parDaArte(aplicativo: unknown, impressao: unknown): ArteDeAtividade | null {
  if (typeof aplicativo !== 'string' || typeof impressao !== 'string') return null

  const limpa = impressao.replace(/\.webp$/i, '')
  if (!APLICATIVO.test(aplicativo) || !IMPRESSAO.test(limpa)) return null

  return { aplicativo, impressao: limpa }
}

export function enderecoNoCatalogo(par: ArteDeAtividade): string {
  return `https://cdn.discordapp.com/app-icons/${par.aplicativo}/${par.impressao}.png?size=${LADO_DA_ARTE}`
}

export function chaveNoBucket(par: ArteDeAtividade): string {
  return `atividade/${par.aplicativo}-${par.impressao}.webp`
}

export function marcaNoRedis(par: ArteDeAtividade): string {
  return `arte-atividade:${par.aplicativo}:${par.impressao}`
}
