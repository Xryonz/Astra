import { env } from './env'
import { chamarGroq, GROQ_CONVERSA, GROQ_RESUMO } from './iaGroq'
import { chamarGemini, GEMINI_CONVERSA, GEMINI_RESUMO } from './iaGemini'

// O CEREBRO DA ASTRA — so a porta de entrada. Cada provedor mora no seu arquivo.
//
// O FORMATO DE FORA E O DA ANTHROPIC (blocos `text` / `tool_use` / `tool_result`),
// de proposito. O laco de ferramentas do bot.ts foi escrito nesse formato, funciona,
// e reescrever ele pra falar dialeto de provedor seria mexer na parte que ja esta
// certa pra acomodar a parte que muda. Cada adaptador traduz por dentro.
//
// Ter dois provedores nao e flexibilidade especulativa: o do Gemini ja estava
// escrito e testado quando o AI Studio recusou a conta do dono. Jogar codigo bom
// fora pra depois reescrever quando a chave sair seria pior que os 10 minutos que
// custou transformar a escolha numa variavel de ambiente.

export type Provedor = 'groq' | 'gemini' | 'off'

// Quem tem chave, atende. Groq primeiro por ser o unico que o dono consegue criar
// hoje; se um dia as duas existirem, IA_PROVIDER desempata sem deploy.
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
  // type: 'config' | 'rede' | 'http' | 'limite' | 'modelo'
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

// Atalho pra quem so quer uma resposta de texto (o tradutor, o resumo de memoria).
export async function gerarTexto(model: string, system: string, entrada: string, maxTokens: number): Promise<string | null> {
  const r = await chamarIa({ model, system, messages: [{ role: 'user', content: entrada }], maxTokens })
  if (r.error) return null
  return r.content?.find((b) => b.type === 'text')?.text?.trim() || null
}
