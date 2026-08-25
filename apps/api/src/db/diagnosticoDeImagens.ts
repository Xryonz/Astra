import 'dotenv/config'
import { Pool } from 'pg'

type Linha = { tabela: string; coluna: string; total: number; uploads: number; bucket: number; dataUri: number; outro: number; vazio: number }

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
