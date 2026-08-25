import 'dotenv/config'
import { Pool } from 'pg'
import sharp from 'sharp'
import { putAttachment, storageMode, storageFalta } from '../lib/storage'

const LADO_DE_EXIBICAO = 256

const ALVOS = [
  { tabela: 'User',   coluna: 'avatarUrl', par: 'avatarFullUrl' },
  { tabela: 'Server', coluna: 'iconUrl',   par: 'iconFullUrl' },
] as const

type Pendente = { id: string; url: string }

async function baixar(url: string): Promise<Buffer | null> {
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(20_000) })
    if (!r.ok) return null
    return Buffer.from(await r.arrayBuffer())
  } catch {
    return null
  }
}

async function main() {
  const vai = process.argv.includes('--vai')
  const url = process.env.DATABASE_URL
  if (!url) {
    console.error('[ENCOLHER] DATABASE_URL não definido — abortando.')
    process.exit(1)
  }
  const local = url.includes('localhost') || url.includes('127.0.0.1') || url.includes('.railway.internal')
  try {
    const u = new URL(url)
    console.log(`[ENCOLHER] banco: ${u.hostname}${u.pathname}`)
  } catch {  }
  console.log(vai ? '[ENCOLHER] MODO REAL — vai escrever.' : '[ENCOLHER] simulação (use --vai para executar).')

  if (vai && storageMode === 'local') {
    console.error('')
    console.error('[ENCOLHER] ABORTANDO: o storage está em modo LOCAL, não no bucket.')
    console.error(`[ENCOLHER] Falta no ambiente: ${storageFalta.join(', ') || '(não sei dizer)'}`)
    console.error('[ENCOLHER] Rodar assim gravaria URLs /uploads/ no banco, apontando para')
    console.error('[ENCOLHER] arquivos que só existem nesta máquina. As imagens quebrariam.')
    process.exit(1)
  }

  const pool = new Pool({ connectionString: url, ssl: local ? false : { rejectUnauthorized: false } })

  let feitas = 0, pulos = 0, falhas = 0, bytesAntes = 0, bytesDepois = 0

  for (const alvo of ALVOS) {
    const existe = await pool.query(
      `SELECT 1 FROM information_schema.columns WHERE table_name = $1 AND column_name = $2`,
      [alvo.tabela, alvo.par],
    )
    if (existe.rowCount === 0) {
      console.log(`  (pulando ${alvo.tabela}: a coluna ${alvo.par} ainda não existe — suba o servidor uma vez)`)
      continue
    }

    const { rows } = await pool.query<Pendente>(
      `SELECT id, "${alvo.coluna}" AS url FROM "${alvo.tabela}"
       WHERE "${alvo.coluna}" IS NOT NULL AND "${alvo.coluna}" <> ''
         AND "${alvo.par}" IS NULL
         AND "${alvo.coluna}" LIKE 'http%'
         AND "${alvo.coluna}" NOT LIKE '%.gif'`,
    )
    console.log(`\n  ${alvo.tabela}.${alvo.coluna}: ${rows.length} para processar`)

    for (const linha of rows) {
      const bruto = await baixar(linha.url)
      if (!bruto) {
        falhas++
        console.log(`    ! ${linha.id}: não consegui baixar`)
        continue
      }

      let pequeno: Buffer
      try {
        pequeno = await sharp(bruto)
          .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
          .webp({ quality: 90, effort: 6, alphaQuality: 100, smartSubsample: true })
          .toBuffer()
      } catch {
        falhas++
        console.log(`    ! ${linha.id}: imagem que o sharp não leu`)
        continue
      }

      if (pequeno.length >= bruto.length) {
        pulos++
        continue
      }

      bytesAntes += bruto.length
      bytesDepois += pequeno.length
      feitas++

      if (!vai) continue

      const chave = `${linha.id.replace(/[^a-zA-Z0-9]/g, '')}_${Date.now()}_x${LADO_DE_EXIBICAO}.webp`
      const novaUrl = await putAttachment(chave, pequeno, 'image/webp')
      await pool.query(
        `UPDATE "${alvo.tabela}" SET "${alvo.coluna}" = $1, "${alvo.par}" = $2 WHERE id = $3`,
        [novaUrl, linha.url, linha.id],
      )
    }
  }

  const mb = (n: number) => (n / 1024 / 1024).toFixed(1)
  console.log('')
  console.log(`  ${feitas} encolhida(s) · ${pulos} já eram pequenas · ${falhas} falha(s)`)
  if (feitas > 0) {
    console.log(`  ${mb(bytesAntes)} MB -> ${mb(bytesDepois)} MB por rodada de download dos clientes`)
    console.log(`  (${(bytesAntes / Math.max(bytesDepois, 1)).toFixed(1)}x menos)`)
  }
  if (!vai && feitas > 0) console.log('\n  Nada foi gravado. Repita com --vai para valer.')

  await pool.end()
}

main().catch((e) => {
  console.error('[ENCOLHER] falhou:', e)
  process.exit(1)
})
