import { describe, it, expect } from 'vitest'
import { paraMensagens } from './iaGroq'

// A traducao de historico e o unico lugar do adaptador onde da pra errar em
// silencio: se a ordem sair torta ou um tool_call ficar sem resposta, o Groq
// devolve 400 e a bot responde "problema tecnico" pra sempre — sem pista de que a
// culpa foi de uma conversao, nao do modelo.

const FERRAMENTA = {
  role: 'assistant',
  content: [
    { type: 'text', text: 'deixa eu ver' },
    { type: 'tool_use', id: 'call_1', name: 'buscar', input: { termo: 'x' } },
  ],
}
const RESULTADO = {
  role: 'user',
  content: [{ type: 'tool_result', tool_use_id: 'call_1', content: 'achei 3' }],
}

describe('paraMensagens', () => {
  it('poe o system na frente, uma vez so', () => {
    const r = paraMensagens('seja legal', [{ role: 'user', content: 'oi' }])
    expect(r[0]).toEqual({ role: 'system', content: 'seja legal' })
    expect(r.filter((m) => m.role === 'system')).toHaveLength(1)
  })

  it('tool_result vira mensagem propria de papel tool, nao fica dentro do user', () => {
    const r = paraMensagens('s', [{ role: 'user', content: 'oi' }, FERRAMENTA, RESULTADO])
    const tool = r.find((m) => m.role === 'tool')
    expect(tool).toEqual({ role: 'tool', tool_call_id: 'call_1', content: 'achei 3' })
    expect(r.some((m) => m.role === 'user' && /achei 3/.test(m.content ?? ''))).toBe(false)
  })

  // A API recusa com 400 se a resposta nao vier logo depois de quem pediu.
  it('a resposta da ferramenta vem depois da chamada', () => {
    const r = paraMensagens('s', [FERRAMENTA, RESULTADO])
    const iChamada  = r.findIndex((m) => m.tool_calls?.length)
    const iResposta = r.findIndex((m) => m.role === 'tool')
    expect(iChamada).toBeGreaterThanOrEqual(0)
    expect(iResposta).toBe(iChamada + 1)
  })

  it('input da ferramenta vira string de JSON', () => {
    const r = paraMensagens('s', [FERRAMENTA])
    const c = r.find((m) => m.tool_calls?.length)!.tool_calls![0]
    expect(c.type).toBe('function')
    expect(c.function.name).toBe('buscar')
    expect(JSON.parse(c.function.arguments)).toEqual({ termo: 'x' })
  })

  // Alguns modelos do Groq recusam a mensagem se o campo sumir.
  it('assistente com tool_calls mantem content, mesmo vazio', () => {
    const semTexto = { role: 'assistant', content: [{ type: 'tool_use', id: 'c', name: 'n', input: {} }] }
    const m = paraMensagens('s', [semTexto]).find((x) => x.tool_calls?.length)!
    expect(m.content).toBe('')
  })

  it('nao emite mensagem vazia quando o bloco nao tem nada aproveitavel', () => {
    const r = paraMensagens('s', [{ role: 'assistant', content: [] }, { role: 'user', content: '' }])
    expect(r).toHaveLength(1) // so o system
  })

  it('varios tool_use no mesmo turno viram varias chamadas em UMA mensagem', () => {
    const duplo = {
      role: 'assistant',
      content: [
        { type: 'tool_use', id: 'a', name: 'um',  input: {} },
        { type: 'tool_use', id: 'b', name: 'dois', input: {} },
      ],
    }
    const r = paraMensagens('s', [duplo])
    const comChamada = r.filter((m) => m.tool_calls?.length)
    expect(comChamada).toHaveLength(1)
    expect(comChamada[0].tool_calls).toHaveLength(2)
  })
})
