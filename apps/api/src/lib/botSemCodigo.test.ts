import { describe, it, expect } from 'vitest'
import { semMarcaDeCodigo } from './bot'

// A bot conversa, ela não documenta. Caixa de código no meio de uma conversa faz a
// resposta parecer saída de terminal — o oposto da persona.
//
// Estes testes existem porque a instrução no prompt NÃO é garantia: modelo de
// linguagem trata regra de formato como preferência forte, não como contrato. A
// instrução reduz a frequência; esta função decide o resultado — e o que decide o
// resultado é o que precisa de teste.
describe('semMarcaDeCodigo', () => {
  it('tira a cerca e MANTÉM o conteúdo', () => {
    // Apagar o bloco inteiro perderia a resposta. O que incomoda é a caixa.
    expect(semMarcaDeCodigo('olha:\n```\nnpm install\n```')).toBe('olha:\nnpm install')
  })

  it('tira a linguagem declarada junto da cerca', () => {
    expect(semMarcaDeCodigo('```ts\nconst x = 1\n```')).toBe('const x = 1')
  })

  it('fecha cerca órfã (resposta cortada no limite de tokens)', () => {
    // Sem isto sobra uma cerca solta e o cliente desenha uma caixa aberta que vai
    // até o fim da mensagem — pior que o bloco original.
    expect(semMarcaDeCodigo('tenta assim:\n```bash\nls -la')).toBe('tenta assim:\nls -la')
  })

  it('tira crase simples e dupla', () => {
    expect(semMarcaDeCodigo('roda `ls` ou ``echo `oi` ``')).toBe('roda ls ou echo oi')
  })

  it('não deixa buraco de linhas em branco onde estava a cerca', () => {
    expect(semMarcaDeCodigo('antes\n\n```\nmeio\n```\n\ndepois')).toBe('antes\n\nmeio\n\ndepois')
  })

  it('não mexe em texto sem marca de código', () => {
    const t = 'Bom dia. O **negrito** e as listas continuam valendo:\n- um\n- dois'
    expect(semMarcaDeCodigo(t)).toBe(t)
  })
})
