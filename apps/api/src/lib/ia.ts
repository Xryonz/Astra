import { env } from './env'

// O CEREBRO DA ASTRA, num arquivo so.
//
// Roda no Gemini e nao na Anthropic por um motivo unico e concreto: a API da
// Anthropic e paga, o dono nao tem cartao, e uma bot que responde "estou offline"
// pra sempre nao e uma bot. O Google AI Studio da chave sem cartao. A qualidade do
// Flash sobra pra o que a Sparkle faz — conversar com memoria de 24h e chamar meia
// duzia de ferramentas.
//
// O PRECO DISSO, que precisa estar escrito em algum lugar: na camada gratuita o
// Google usa as conversas pra melhorar os modelos deles. As mensagens do canal que
// a bot le vao junto. Quem paga tem opt-out; quem nao paga, nao.
//
// O FORMATO DE FORA E O DA ANTHROPIC (blocos `text` / `tool_use` / `tool_result`),
// de proposito. O laco de ferramentas do bot.ts foi escrito nesse formato, funciona,
// e reescrever ele pra falar Gemini seria mexer na parte que ja esta certa pra
// acomodar a parte que mudou. Toda a traducao acontece aqui dentro.

const BASE = 'https://generativelanguage.googleapis.com/v1beta/models'

// Modelos configuraveis por variavel: quando o Google aposenta um id (acontece), da
// pra trocar no painel do Render sem esperar um deploy de codigo.
export const MODELO_CONVERSA = process.env.GEMINI_MODEL       ?? 'gemini-2.5-flash'
export const MODELO_RESUMO   = process.env.GEMINI_MODEL_FAST  ?? 'gemini-2.5-flash-lite'

export const IA_LIGADA = !!env.GEMINI_API_KEY

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

// ---- Traducao: nosso formato -> Gemini ----

interface ParteGemini {
  text?:             string
  functionCall?:     { name: string; args?: Record<string, unknown> }
  functionResponse?: { name: string; response: Record<string, unknown> }
}

// O Gemini identifica a resposta de uma ferramenta pelo NOME dela; a Anthropic usa um
// id opaco. Como o historico so guarda o id, a conversao precisa lembrar qual id era
// qual nome — dai este mapa, montado varrendo o que ja passou.
function mapaDeIds(mensagens: any[]): Map<string, string> {
  const mapa = new Map<string, string>()
  for (const m of mensagens) {
    if (!Array.isArray(m.content)) continue
    for (const b of m.content) {
      if (b?.type === 'tool_use' && b.id && b.name) mapa.set(b.id, b.name)
    }
  }
  return mapa
}

function paraConteudoGemini(mensagens: any[]): Array<{ role: string; parts: ParteGemini[] }> {
  const nomePorId = mapaDeIds(mensagens)
  const saida: Array<{ role: string; parts: ParteGemini[] }> = []

  for (const m of mensagens) {
    const partes: ParteGemini[] = []
    if (typeof m.content === 'string') {
      if (m.content) partes.push({ text: m.content })
    } else if (Array.isArray(m.content)) {
      for (const b of m.content) {
        if (b?.type === 'text' && b.text) partes.push({ text: b.text })
        else if (b?.type === 'tool_use' && b.name) {
          partes.push({ functionCall: { name: b.name, args: (b.input ?? {}) as Record<string, unknown> } })
        } else if (b?.type === 'tool_result') {
          const nome = nomePorId.get(b.tool_use_id) ?? 'ferramenta'
          // O Gemini exige um objeto na resposta; as nossas ferramentas devolvem
          // texto puro, entao vai embrulhado.
          partes.push({ functionResponse: { name: nome, response: { resultado: String(b.content ?? '') } } })
        }
      }
    }
    if (partes.length === 0) continue
    // Gemini so conhece 'user' e 'model'.
    saida.push({ role: m.role === 'assistant' ? 'model' : 'user', parts: partes })
  }
  return saida
}

// O schema de ferramenta da Anthropic ja e JSON Schema, que e o que o Gemini quer —
// menos os campos que ele recusa. `additionalProperties` e `$schema` derrubam a
// requisicao inteira com 400, entao saem aqui em vez de virarem um bug silencioso.
function limparSchema(schema: unknown): unknown {
  if (Array.isArray(schema)) return schema.map(limparSchema)
  if (!schema || typeof schema !== 'object') return schema
  const saida: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(schema as Record<string, unknown>)) {
    if (k === 'additionalProperties' || k === '$schema') continue
    saida[k] = limparSchema(v)
  }
  return saida
}

export interface PedidoIa {
  model:      string
  system:     string
  messages:   any[]
  tools?:     FerramentaIa[]
  maxTokens:  number
}

export async function chamarIa(opts: PedidoIa): Promise<RespostaIa> {
  if (!env.GEMINI_API_KEY) return { error: { type: 'config', message: 'sem chave de IA' } }

  const corpo: Record<string, unknown> = {
    contents: paraConteudoGemini(opts.messages),
    systemInstruction: { parts: [{ text: opts.system }] },
    generationConfig: { maxOutputTokens: opts.maxTokens, temperature: 0.7 },
  }
  if (opts.tools?.length) {
    corpo.tools = [{
      functionDeclarations: opts.tools.map((t) => ({
        name:        t.name,
        description: t.description,
        parameters:  limparSchema(t.input_schema),
      })),
    }]
  }

  let res: Response
  try {
    res = await fetch(`${BASE}/${opts.model}:generateContent`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body:    JSON.stringify(corpo),
    })
  } catch (e) {
    return { error: { type: 'rede', message: (e as Error).message } }
  }
  if (!res.ok) {
    const texto = await res.text().catch(() => '')
    return { error: { type: 'http', message: `HTTP ${res.status}: ${texto.slice(0, 200)}` } }
  }

  const json = await res.json() as {
    candidates?: Array<{ content?: { parts?: ParteGemini[] }; finishReason?: string }>
    usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number; cachedContentTokenCount?: number }
  }

  const partes = json.candidates?.[0]?.content?.parts ?? []
  const blocos: BlocoIa[] = []
  let chamou = 0
  for (const p of partes) {
    if (p.text) blocos.push({ type: 'text', text: p.text })
    else if (p.functionCall?.name) {
      blocos.push({
        type: 'tool_use',
        // Id proprio: o Gemini nao manda um, e o laco do bot.ts casa
        // tool_result com tool_use por id.
        id:    `fc_${chamou++}_${p.functionCall.name}`,
        name:  p.functionCall.name,
        input: p.functionCall.args ?? {},
      })
    }
  }

  return {
    content:     blocos,
    stop_reason: blocos.some((b) => b.type === 'tool_use') ? 'tool_use' : 'end_turn',
    usage: {
      input_tokens:            json.usageMetadata?.promptTokenCount ?? 0,
      output_tokens:           json.usageMetadata?.candidatesTokenCount ?? 0,
      cache_read_input_tokens: json.usageMetadata?.cachedContentTokenCount ?? 0,
    },
  }
}

// Atalho pra quem so quer uma resposta de texto (o tradutor).
export async function gerarTexto(model: string, system: string, entrada: string, maxTokens: number): Promise<string | null> {
  const r = await chamarIa({ model, system, messages: [{ role: 'user', content: entrada }], maxTokens })
  if (r.error) return null
  return r.content?.find((b) => b.type === 'text')?.text?.trim() || null
}
