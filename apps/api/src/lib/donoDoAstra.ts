import { eq } from 'drizzle-orm'
import { db } from '../db'
import { users } from '../db/schema'
import { env } from './env'

// QUEM PODE MEXER NA APARENCIA DAS BOTS.
//
// Isto NAO e permissao de constelacao: a Sparkle e a Sparxie sao uma conta so,
// compartilhada por todas as constelacoes, e a cara delas e a mesma em qualquer
// lugar. Delegar isso a quem administra UMA constelacao deixaria essa pessoa
// mudando a bot pra todo mundo. Por isso a lista mora fora do banco (env), e nao
// ha rota que conceda: quem edita e quem tem acesso a hospedagem.
//
// Comparacao por @ e nao por id porque id de usuario e gerado no cadastro — pra
// preencher a variavel seria preciso ir ao banco buscar. O @ e unico, o dono sabe
// o dele de cabeca, e trocar de @ e coisa que se faz uma vez na vida.
const DONOS: ReadonlySet<string> = new Set(
  (env.ASTRA_OWNER_USERNAMES ?? '')
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean),
)

// Vazio = ninguem edita. Ausencia de configuracao virando "todo mundo pode" e o
// jeito classico de uma trava dessas nao existir na producao sem ninguem notar.
export function haDono(): boolean {
  return DONOS.size > 0
}

export async function ehDonoDoAstra(userId: string | undefined): Promise<boolean> {
  if (!userId || DONOS.size === 0) return false
  const [u] = await db.select({ username: users.username })
    .from(users).where(eq(users.id, userId)).limit(1)
  return !!u && DONOS.has(u.username.toLowerCase())
}
