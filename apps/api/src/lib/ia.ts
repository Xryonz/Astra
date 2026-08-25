import { env } from './env'
import { chamarGroq, GROQ_CONVERSA, GROQ_RESUMO } from './iaGroq'
import { chamarGemini, GEMINI_CONVERSA, GEMINI_RESUMO } from './iaGemini'

export type Provedor = 'groq' | 'gemini' | 'off'

export const IA_PROVEDOR: Provedor =
  process.env.IA_PROVIDER === 'gemini' && env.GEMINI_API_KEY ? 'gemini'
  : process.env.IA_PROVIDER === 'groq' && env.GROQ_API_KEY   ? 'groq'
  : env.GROQ_API_KEY   ? 'groq'
  : env.GEMINI_API_KEY ? 'gemini'
  : 'off'

export const IA_LIGADA = IA_PROVEDOR !== 'off'

export const MODELO_CONVERSA = IA_PROVEDOR === 'gemini' ? GEMINI_CONVERSA : GROQ_CONVERSA
export const MODELO_RESUMO   = IA_PROVEDOR === 'gemini' ? GEMINI_RESUMO   : GROQ_RESUMO

export interface BlocoIa {
  type:  'text' | 'tool_use'
  text?: string
  id?:   string
  name?: string
  input?: unknown
}

export interface RespostaIa {
  content?:     BlocoIa[]
  stop_reason?: string
  usage?:       { input_tokens?: number; output_tokens?: number; cache_read_input_tokens?: number }
  error?:       { type?: string; message?: string }
}

export interface FerramentaIa {
  name:         string
  description:  string
  input_schema: Record<string, unknown>
}

export interface PedidoIa {
  model:      string
  system:     string
  messages:   any[]
  tools?:     FerramentaIa[]
  maxTokens:  number
}

export async function chamarIa(opts: PedidoIa): Promise<RespostaIa> {
  if (IA_PROVEDOR === 'groq')   return chamarGroq(env.GROQ_API_KEY!, opts)
  if (IA_PROVEDOR === 'gemini') return chamarGemini(env.GEMINI_API_KEY!, opts)
  return { error: { type: 'config', message: 'sem chave de IA' } }
}

export async function gerarTexto(model: string, system: string, entrada: string, maxTokens: number): Promise<string | null> {
  const r = await chamarIa({ model, system, messages: [{ role: 'user', content: entrada }], maxTokens })
  if (r.error) return null
  return r.content?.find((b) => b.type === 'text')?.text?.trim() || null
}
