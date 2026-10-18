import { redis } from './redis'

export async function contarNaJanela(chave: string, segundos: number): Promise<number> {
  const passos = await redis.multi().set(chave, '0', 'EX', segundos, 'NX').incr(chave).exec()
  const [erro, contagem] = passos?.[1] ?? [new Error(`a contagem de ${chave} foi abortada`), null]
  if (erro) throw erro
  return Number(contagem)
}
