import { env } from './env'
import { DISCO_APAGADO } from './autorDaMensagem'

const raizPublica = env.API_URL?.replace(/\/+$/, '') ?? ''

export const IMAGEM_INDISPONIVEL = `${raizPublica}/static/imagem-indisponivel.png`

type Anexo = { url?: unknown; name?: unknown; size?: unknown }

export function semAnexosPerdidos<T>(anexos: T[]): T[] {
  return anexos.map((item) => {
    const anexo = item as Anexo
    if (typeof anexo?.url !== 'string' || !anexo.url.startsWith(DISCO_APAGADO)) return item
    return {
      url: IMAGEM_INDISPONIVEL,
      type: 'image/png',
      name: anexo.name,
      size: anexo.size,
      width: 960,
      height: 540,
    } as T
  })
}
