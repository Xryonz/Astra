import 'dotenv/config'
import { Pool } from 'pg'
import sharp from 'sharp'
import { putAttachment, storageMode, storageFalta } from '../lib/storage'

// BACKFILL: gera a versão de exibição das imagens que já estão no bucket.
//
// `persistImagemDeExibicao` só age em envio NOVO, então toda imagem que já existia
// continua descendo em tamanho cheio — 1024px para desenhar um círculo de 22. Este script
// passa por elas uma vez.
//
//   cd apps/api && npm run img:encolher          # SIMULA: diz o que faria, não muda nada
//   cd apps/api && npm run img:encolher -- --vai  # executa de verdade
//
// SIMULA POR PADRÃO, e é deliberado: um backfill escreve no banco E no bucket, e é o tipo
// de coisa que se roda uma vez, às pressas, no fim do dia. Ter de digitar `--vai` é o
// atrito que separa "quero ver o que aconteceria" de "faz".
//
// ---- O que ele NÃO faz, e por quê ----
//
// NÃO APAGA NADA. A imagem antiga vira o valor da coluna `...FullUrl` e continua no
// bucket. Isso custa espaço e é o preço de poder voltar atrás: se a versão pequena sair
// ruim num caso que ninguém previu, o conserto é um UPDATE trocando as colunas de volta,
// e não uma imagem perdida para sempre.
//
// NÃO TOCA EM /uploads/. Essas URLs apontam para o disco efêmero do Render e os arquivos
// não existem mais (ver `diagnosticoDeImagens`). Baixar daria 404 e o único efeito seria
// poluir o relatório com erro que não é erro deste script.
//
// NÃO TOCA EM GIF, pelo mesmo motivo do caminho de envio: esta configuração do `sharp`
// devolveria só o primeiro quadro, e avatar que para de animar é regressão.
//
// NÃO TOCA EM HOST DE TERCEIRO (avatar do Google, por exemplo): não é nosso para
// reprocessar, e nem sempre continua lá.

const LADO_DE_EXIBICAO = 256

// Os campos que passam por `persistImagemDeExibicao` no caminho de envio. Banner fica de
// fora aqui pela mesma razão de lá: é desenhado grande.
const ALVOS = [
  { tabela: 'User',   coluna: 'avatarUrl', par: 'avatarFullUrl' },
  { tabela: 'Server', coluna: 'iconUrl',   par: 'iconFullUrl' },
] as const

type Pendente = { id: string; url: string }

async function baixar(url: string): Promise<Buffer | null> {
  try {
    // Prazo curto: o backfill roda em lote, e uma URL que não responde não pode segurar
    // a fila. Quem falhar fica para a próxima execução — o script é idempotente.
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
  } catch { /* URL estranha: o Pool reclama melhor que eu */ }
  console.log(vai ? '[ENCOLHER] MODO REAL — vai escrever.' : '[ENCOLHER] simulação (use --vai para executar).')

  // SEM BUCKET, NÃO RODA — e esta guarda é a mais importante do arquivo.
  //
  // `putAttachment` tem um fallback para disco local, e ele é certo no servidor: melhor
  // salvar em algum lugar do que recusar o upload de alguém. Aqui ele seria um DESASTRE
  // silencioso. Rodando desta máquina sem as variáveis do bucket, cada imagem viraria uma
  // URL `/uploads/...` gravada no banco de PRODUÇÃO, apontando para um arquivo que existe
  // só neste computador. O resultado seria trocar imagens que funcionam por imagens
  // quebradas — exatamente o defeito que este trabalho todo existe para consertar.
  //
  // Só os NOMES das variáveis que faltam são impressos; valor de credencial nunca.
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

    // A COLUNA PAR VAZIA É A MARCA DE "ainda não passou por aqui". É também o que torna o
    // script idempotente: rodar duas vezes não reprocessa nada, porque a segunda execução
    // não encontra mais essas linhas.
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

      // Mesma regra do caminho de envio: não vale guardar dois arquivos para servir o pior.
      if (pequeno.length >= bruto.length) {
        pulos++
        continue
      }

      bytesAntes += bruto.length
      bytesDepois += pequeno.length
      feitas++

      if (!vai) continue

      // A ORDEM IMPORTA: sobe o arquivo ANTES de mexer no banco. Ao contrário, uma falha
      // no meio deixaria a coluna apontando para um endereço que não existe — e aí a
      // imagem some para todo mundo, que é bem pior que não ter encolhido.
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
