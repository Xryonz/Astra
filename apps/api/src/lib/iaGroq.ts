import type { BlocoIa, PedidoIa, RespostaIa } from './ia'

const BASE = 'https://api.groq.com/openai/v1/chat/completions'

export const GROQ_CONVERSA = process.env.GROQ_MODEL      ?? 'llama-3.3-70b-versatile'
export const GROQ_RESUMO   = process.env.GROQ_MODEL_FAST ?? 'llama-3.1-8b-instant'

interface ChamadaDeFerramenta {
  id:       string
  type:     'function'
  function: { name: string; arguments: string }
}

interface MensagemOpenAi {
  role:          'system' | 'user' | 'assistant' | 'tool'
  content?:      string
  tool_calls?:   ChamadaDeFerramenta[]
  tool_call_id?: string
}

export function paraMensagens(system: string, mensagens: any[]): MensagemOpenAi[] {
  const saida: MensagemOpenAi[] = [{ role: 'system', content: system }]

  for (const m of mensagens) {
    if (typeof m.content === 'string') {
      if (m.content) saida.push({ role: m.role === 'assistant' ? 'assistant' : 'user', content: m.content })
      continue
    }
    if (!Array.isArray(m.content)) continue

    const textos: string[] = []
    const chamadas: ChamadaDeFerramenta[] = []
    const respostas: MensagemOpenAi[] = []

    for (const b of m.content) {
      if (b?.type === 'text' && b.text) textos.push(b.text)
      else if (b?.type === 'tool_use' && b.name) {
        chamadas.push({
          id:       b.id,
          type:     'function',
          function: { name: b.name, arguments: JSON.stringify(b.input ?? {}) },
        })
      } else if (b?.type === 'tool_result') {
        respostas.push({ role: 'tool', tool_call_id: b.tool_use_id, content: String(b.content ?? '') })
      }
    }

    if (chamadas.length) {
      saida.push({ role: 'assistant', content: textos.join('\n'), tool_calls: chamadas })
    } else if (textos.length) {
      saida.push({ role: m.role === 'assistant' ? 'assistant' : 'user', content: textos.join('\n') })
    }
    saida.push(...respostas)
  }

  return saida
}

export async function chamarGroq(chave: string, opts: PedidoIa): Promise<RespostaIa> {
  const corpo: Record<string, unknown> = {
    model:                 opts.model,
    messages:              paraMensagens(opts.system, opts.messages),
    max_completion_tokens: opts.maxTokens,
    temperature:           0.7,
  }
  if (opts.tools?.length) {
    corpo.tools = opts.tools.map((t) => ({
      type:     'function',
      function: { name: t.name, description: t.description, parameters: t.input_schema },
    }))
  }

  let res: Response
  try {
    res = await fetch(BASE, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${chave}` },
      body:    JSON.stringify(corpo),
    })
  } catch (e) {
    return { error: { type: 'rede', message: (e as Error).message } }
  }

  if (!res.ok) {
    const texto = await res.text().catch(() => '')
    if (res.status === 429) {
      return { error: { type: 'limite', message: `HTTP 429: ${texto.slice(0, 200)}` } }
    }
    if ((res.status === 400 || res.status === 404) && /model/i.test(texto)) {
      return {
        error: {
          type:    'modelo',
          message: `modelo "${opts.model}" recusado pelo Groq — troque GROQ_MODEL/GROQ_MODEL_FAST. (${texto.slice(0, 160)})`,
        },
      }
    }
    return { error: { type: 'http', message: `HTTP ${res.status}: ${texto.slice(0, 200)}` } }
  }

  const json = await res.json() as {
    choices?: Array<{ message?: { content?: string | null; tool_calls?: ChamadaDeFerramenta[] }; finish_reason?: string }>
    usage?:   { prompt_tokens?: number; completion_tokens?: number }
  }

  const msg = json.choices?.[0]?.message
  const blocos: BlocoIa[] = []
  if (msg?.content) blocos.push({ type: 'text', text: msg.content })
  for (const c of msg?.tool_calls ?? []) {
    let entrada: unknown = {}
    try {
      entrada = c.function?.arguments ? JSON.parse(c.function.arguments) : {}
    } catch {
      entrada = {}
    }
    blocos.push({ type: 'tool_use', id: c.id, name: c.function?.name, input: entrada })
  }

  return {
    content:     blocos,
    stop_reason: blocos.some((b) => b.type === 'tool_use') ? 'tool_use' : 'end_turn',
    usage: {
      input_tokens:  json.usage?.prompt_tokens ?? 0,
      output_tokens: json.usage?.completion_tokens ?? 0,
    },
  }
}
