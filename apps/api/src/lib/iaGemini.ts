import type { BlocoIa, FerramentaIa, PedidoIa, RespostaIa } from './ia'

const BASE = 'https://generativelanguage.googleapis.com/v1beta/models'

export const GEMINI_CONVERSA = process.env.GEMINI_MODEL      ?? 'gemini-2.5-flash'
export const GEMINI_RESUMO   = process.env.GEMINI_MODEL_FAST ?? 'gemini-2.5-flash-lite'

interface ParteGemini {
  text?:             string
  functionCall?:     { name: string; args?: Record<string, unknown> }
  functionResponse?: { name: string; response: Record<string, unknown> }
}

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

function paraConteudo(mensagens: any[]): Array<{ role: string; parts: ParteGemini[] }> {
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
          partes.push({ functionResponse: { name: nome, response: { resultado: String(b.content ?? '') } } })
        }
      }
    }
    if (partes.length === 0) continue
    saida.push({ role: m.role === 'assistant' ? 'model' : 'user', parts: partes })
  }
  return saida
}

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

export async function chamarGemini(chave: string, opts: PedidoIa): Promise<RespostaIa> {
  const corpo: Record<string, unknown> = {
    contents: paraConteudo(opts.messages),
    systemInstruction: { parts: [{ text: opts.system }] },
    generationConfig: { maxOutputTokens: opts.maxTokens, temperature: 0.7 },
  }
  if (opts.tools?.length) {
    corpo.tools = [{
      functionDeclarations: opts.tools.map((t: FerramentaIa) => ({
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
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': chave },
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
