import type { BlocoIa, PedidoIa, RespostaIa } from './ia'

// Adaptador do Groq — o provedor PADRAO da Astra.
//
// Escolhido por eliminacao honesta: a Anthropic e paga, o AI Studio do Google
// recusa a conta do dono, e uma bot que responde "estou offline" pra sempre nao e
// uma bot. O Groq da chave sem cartao, sem projeto de cloud, sem burocracia — e
// roda em LPU, o que na pratica significa resposta quase instantanea.
//
// O QUE SE PERDE: o teto gratis e por minuto e por dia (algo como 30 pedidos/min).
// Num servidor movimentado isso ESTOURA, e por isso o 429 vira uma mensagem
// propria la embaixo em vez de "erro tecnico" generico — a pessoa precisa saber
// que e pra tentar de novo em um minuto, nao que a bot quebrou.
//
// A API e compativel com a da OpenAI. Toda a traducao do nosso formato (blocos
// estilo Anthropic) acontece aqui dentro; o laco de ferramentas do bot.ts nao sabe
// que provedor esta atendendo.

const BASE = 'https://api.groq.com/openai/v1/chat/completions'

// Configuraveis por variavel: provedor gratis aposenta modelo sem avisar, e trocar
// no painel do Render e mais rapido que esperar um deploy.
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

// A conversao mais delicada do arquivo.
//
// No nosso formato o resultado de uma ferramenta vem DENTRO de uma mensagem de
// usuario (`{ role:'user', content:[{type:'tool_result'}] }`). No formato da OpenAI
// ele e uma mensagem separada, de papel 'tool', e precisa vir logo depois da
// mensagem do assistente que pediu. Entao um item da nossa lista pode virar varios
// aqui — e a ordem tem que ser preservada, senao a API recusa com 400.
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
      // Com tool_calls o content pode ficar vazio, mas nao pode sumir: alguns
      // modelos do Groq recusam o campo ausente.
      saida.push({ role: 'assistant', content: textos.join('\n'), tool_calls: chamadas })
    } else if (textos.length) {
      saida.push({ role: m.role === 'assistant' ? 'assistant' : 'user', content: textos.join('\n') })
    }
    // As respostas de ferramenta vao depois das chamadas, sempre.
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
    // 429 e o caso COMUM na camada gratis, nao a excecao. Vira um tipo proprio pra
    // quem chama poder dizer "tenta daqui a pouco" em vez de "deu erro".
    if (res.status === 429) {
      return { error: { type: 'limite', message: `HTTP 429: ${texto.slice(0, 200)}` } }
    }
    // Modelo aposentado da 400/404 com o id no corpo. A mensagem aponta o conserto
    // em vez de deixar alguem cacando isso no log.
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
    // Os argumentos chegam como STRING de JSON. Modelo pequeno as vezes manda algo
    // mal formado; um objeto vazio faz a ferramenta reclamar de parametro faltando,
    // que e recuperavel — um throw aqui mataria a conversa inteira.
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
