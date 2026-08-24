import 'dotenv/config'
import { Pool } from 'pg'

// DIAGNÓSTICO DE IMAGENS — conta o estado de cada imagem do banco, sem mudar nada.
//
// Existe para responder duas perguntas que estavam travando decisões, e nenhuma delas
// dava para responder de fora:
//
//  1. "Conta nova não carrega as imagens já colocadas."  Há uma hipótese forte: as
//     imagens salvas quando o storage era LOCAL viraram `/uploads/xxx`, e esses arquivos
//     morreram no disco efêmero do Render. Contas antigas ainda veem por causa do cache em
//     disco do Coil; conta nova pede ao servidor e leva 404. Se este relatório mostrar
//     linhas em `/uploads/`, a hipótese está confirmada — e o número diz o tamanho do
//     estrago.
//
//  2. "Vale reprocessar o que já está no bucket?"  A versão de exibição (256px) só é
//     gerada em envio novo. Este relatório diz quantas imagens ainda estão sem ela, que é
//     exatamente o custo de um backfill — e se ele se paga.
//
// SÓ LÊ. Nenhum UPDATE, nenhum DELETE, nenhuma chamada ao bucket. Rodar isto em produção é
// tão seguro quanto abrir o painel do banco, e é para ser rodado em produção mesmo: é o
// banco de verdade que tem a resposta.
//
//   cd apps/api && npm run img:diag
//
// Precisa de DATABASE_URL no ambiente — o mesmo que o `db:migrate` usa.

type Linha = { tabela: string; coluna: string; total: number; uploads: number; bucket: number; dataUri: number; outro: number; vazio: number }

// Cada campo de imagem do Astra, com o que ele é. `encolhe` marca os que passam por
// `persistImagemDeExibicao` — só esses têm versão de exibição a ganhar.
const CAMPOS: Array<{ tabela: string; coluna: string; encolhe: boolean; par?: string }> = [
  { tabela: 'User',       coluna: 'avatarUrl',  encolhe: true,  par: 'avatarFullUrl' },
  { tabela: 'User',       coluna: 'bannerUrl',  encolhe: false },
  { tabela: 'Server',     coluna: 'iconUrl',    encolhe: true,  par: 'iconFullUrl' },
  { tabela: 'Server',     coluna: 'bannerUrl',  encolhe: false },
  { tabela: 'ServerRole', coluna: 'iconUrl',    encolhe: true },
  { tabela: 'ServerEmoji', coluna: 'url',       encolhe: false },
]

async function main() {
  const url = process.env.DATABASE_URL
  if (!url) {
    console.error('[IMG] DATABASE_URL não definido — abortando.')
    process.exit(1)
  }
  const local = url.includes('localhost') || url.includes('127.0.0.1') || url.includes('.railway.internal')

  // DIZ CONTRA QUEM VAI RODAR, antes de rodar. Isto só lê, então errar de banco aqui não
  // estraga nada — mas ler o banco errado e tirar conclusão dele estraga a DECISÃO, que é
  // o produto deste script. O host basta para reconhecer; usuário e senha ficam de fora.
  try {
    const u = new URL(url)
    console.log(`[IMG] banco: ${u.hostname}${u.pathname}  (ssl=${!local})`)
  } catch {
    console.log(`[IMG] banco: (não consegui ler o host da URL)  (ssl=${!local})`)
  }

  const pool = new Pool({ connectionString: url, ssl: local ? false : { rejectUnauthorized: false } })

  const linhas: Linha[] = []
  let orfas = 0
  let semEncolher = 0

  for (const campo of CAMPOS) {
    // A tabela pode não existir num banco antigo (ServerRole nasceu depois). Falhar por
    // isso transformaria um diagnóstico em erro, e o diagnóstico é justamente para quem
    // não sabe o que tem.
    const existe = await pool.query(
      `SELECT 1 FROM information_schema.columns WHERE table_name = $1 AND column_name = $2`,
      [campo.tabela, campo.coluna],
    )
    if (existe.rowCount === 0) {
      console.log(`  (pulando ${campo.tabela}.${campo.coluna}: não existe neste banco)`)
      continue
    }

    const q = await pool.query<{ classe: string; n: string }>(
      `SELECT CASE
         WHEN "${campo.coluna}" IS NULL OR "${campo.coluna}" = '' THEN 'vazio'
         WHEN "${campo.coluna}" LIKE '/uploads/%' THEN 'uploads'
         WHEN "${campo.coluna}" LIKE 'data:%'     THEN 'dataUri'
         WHEN "${campo.coluna}" LIKE 'http%'      THEN 'bucket'
         ELSE 'outro'
       END AS classe, COUNT(*)::text AS n
       FROM "${campo.tabela}" GROUP BY 1`,
    )

    const l: Linha = { tabela: campo.tabela, coluna: campo.coluna, total: 0, uploads: 0, bucket: 0, dataUri: 0, outro: 0, vazio: 0 }
    for (const r of q.rows) {
      const n = Number(r.n)
      l.total += n
      ;(l as unknown as Record<string, number>)[r.classe] = n
    }
    linhas.push(l)
    orfas += l.uploads

    // Quantas já têm a versão de exibição: a coluna par preenchida é a marca de que a
    // imagem passou por `persistImagemDeExibicao` depois da mudança.
    if (campo.encolhe && campo.par) {
      const temPar = await pool.query(
        `SELECT 1 FROM information_schema.columns WHERE table_name = $1 AND column_name = $2`,
        [campo.tabela, campo.par],
      )
      if (temPar.rowCount) {
        const r = await pool.query<{ n: string }>(
          `SELECT COUNT(*)::text AS n FROM "${campo.tabela}"
           WHERE "${campo.coluna}" IS NOT NULL AND "${campo.coluna}" <> ''
             AND "${campo.par}" IS NULL`,
        )
        const n = Number(r.rows[0]?.n ?? 0)
        semEncolher += n
        console.log(`  ${campo.tabela}.${campo.coluna}: ${n} sem versão de exibição`)
      }
    }
  }

  console.log('')
  console.log('  tabela.coluna              total   /uploads   bucket   data:   vazio')
  console.log('  ' + '-'.repeat(68))
  for (const l of linhas) {
    const nome = `${l.tabela}.${l.coluna}`.padEnd(24)
    console.log(
      `  ${nome} ${String(l.total).padStart(6)} ${String(l.uploads).padStart(10)} ` +
        `${String(l.bucket).padStart(8)} ${String(l.dataUri).padStart(7)} ${String(l.vazio).padStart(7)}`,
    )
  }

  console.log('')
  if (orfas > 0) {
    console.log(`  ${orfas} imagem(ns) em /uploads/ — o disco do Render não sobrevive a um reinício,`)
    console.log('  então estas são as que NÃO CARREGAM em conta ou máquina nova. Hipótese CONFIRMADA.')
  } else {
    console.log('  Nenhuma imagem em /uploads/ — a hipótese das órfãs está DESCARTADA, e')
    console.log('  "conta nova não carrega" tem outra causa. O rede.txt do app diz qual.')
  }
  if (semEncolher > 0) {
    console.log(`  ${semEncolher} imagem(ns) ainda sem versão de exibição (baixam em tamanho cheio).`)
  }

  await pool.end()
}

main().catch((e) => {
  console.error('[IMG] falhou:', e)
  process.exit(1)
})
