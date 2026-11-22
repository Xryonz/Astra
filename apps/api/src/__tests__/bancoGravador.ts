import { getTableName, type SQL, type Table } from 'drizzle-orm'
import { PgDialect } from 'drizzle-orm/pg-core'

type Consulta = { passo: string; sql: string; params: unknown[]; valores: unknown }

const dialeto = new PgDialect()

export const gravador = {
  passos: [] as string[],
  consultas: [] as Consulta[],
  respostas: new Map<string, unknown[]>(),
  responder(chave: string, ...resultados: unknown[]) {
    this.respostas.set(chave, resultados)
  },
  anotar(passo: string) {
    this.passos.push(passo)
  },
  limpar() {
    this.passos.length = 0
    this.consultas.length = 0
    this.respostas.clear()
  },
  consulta(passo: string): Consulta | undefined {
    return this.consultas.find((c) => c.passo === passo)
  },
}

function proximaResposta(chave: string): unknown {
  const fila = gravador.respostas.get(chave)
  return fila?.length ? fila.shift() : []
}

function cadeia(onde: string, operacao: string, tabela?: Table) {
  let nome = tabela ? getTableName(tabela) : ''
  let trava = ''
  let condicao: SQL | undefined
  let valores: unknown
  const comando: Record<string, unknown> = new Proxy(
    {},
    {
      get(_alvo, metodo) {
        if (metodo === 'then') {
          return (resolver: (v: unknown) => void) => {
            const passo = `${onde}:${operacao} ${nome}${trava ? ` for ${trava}` : ''}`
            const texto = condicao ? dialeto.sqlToQuery(condicao) : { sql: '', params: [] }
            gravador.passos.push(passo)
            gravador.consultas.push({ passo, sql: texto.sql, params: texto.params, valores })
            resolver(proximaResposta(`${operacao}:${nome}`))
          }
        }
        return (...args: unknown[]) => {
          if (metodo === 'from') nome = getTableName(args[0] as Table)
          if (metodo === 'for') trava = String(args[0])
          if (metodo === 'where') condicao = args[0] as SQL
          if (metodo === 'values') valores = args[0]
          return comando
        }
      },
    },
  )
  return comando
}

function executor(onde: string) {
  return {
    select: () => cadeia(onde, 'select'),
    update: (tabela: Table) => cadeia(onde, 'update', tabela),
    insert: (tabela: Table) => cadeia(onde, 'insert', tabela),
    delete: (tabela: Table) => cadeia(onde, 'delete', tabela),
  }
}

export const bancoGravador = {
  ...executor('db'),
  transaction: async <T>(trabalho: (tx: ReturnType<typeof executor>) => Promise<T>): Promise<T> => {
    gravador.passos.push('begin')
    try {
      const resultado = await trabalho(executor('tx'))
      gravador.passos.push('commit')
      return resultado
    } catch (erro) {
      gravador.passos.push('rollback')
      throw erro
    }
  },
}
