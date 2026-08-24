import { describe, it, expect, vi, afterEach } from 'vitest'

// EM PRODUCAO, SEM BUCKET, O UPLOAD TEM DE FALHAR — e este arquivo existe porque a
// alternativa falha em SILENCIO, meses depois, longe da causa.
//
// A historia curta: o disco do Render e efemero. Enquanto `putAttachment` caia pro disco
// local ao nao achar o bucket, uma variavel de ambiente faltando por dez minutos gravava
// `/uploads/xxx` no BANCO. O arquivo morria no proximo deploy; o endereco ficava. O
// sintoma aparecia semanas depois e so pra CONTA NOVA — quem ja tinha visto a imagem
// continuava enxergando, porque o cliente guarda em disco. Nada nos logs, nenhum upload
// falhado, nada pra investigar.
//
// Testar o CAMINHO DE ERRO e nao o feliz e deliberado: o caminho feliz precisa de bucket
// de verdade, e o que quebrou nunca foi ele.
//
// `resetModules` + import dinamico porque a decisao e tomada na CARGA do modulo (o `s3` e
// o `EXIGE_BUCKET` sao constantes de topo). Importar uma vez no topo do arquivo
// congelaria o ambiente do primeiro teste pros dois.

async function carregarStorageCom(env: Record<string, string | undefined>) {
  vi.resetModules()
  for (const [k, v] of Object.entries(env)) {
    if (v === undefined) vi.stubEnv(k, '')
    else vi.stubEnv(k, v)
  }
  return import('./storage')
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('putAttachment sem bucket', () => {
  it('RECUSA em produção, em vez de gravar uma URL que vai morrer', async () => {
    const { putAttachment } = await carregarStorageCom({
      NODE_ENV: 'production',
      S3_ENDPOINT: '', R2_ACCOUNT_ID: '', R2_ACCESS_KEY_ID: '',
      R2_SECRET_ACCESS_KEY: '', R2_BUCKET: '', R2_PUBLIC_URL: '',
    })

    await expect(putAttachment('x.webp', Buffer.from([1, 2, 3]), 'image/webp'))
      .rejects.toMatchObject({ status: 503, code: 'storage_indisponivel' })
  })

  it('em desenvolvimento continua caindo pro disco — a máquina de quem programa não tem bucket', async () => {
    const { putAttachment } = await carregarStorageCom({
      NODE_ENV: 'development',
      S3_ENDPOINT: '', R2_ACCOUNT_ID: '', R2_ACCESS_KEY_ID: '',
      R2_SECRET_ACCESS_KEY: '', R2_BUCKET: '', R2_PUBLIC_URL: '',
    })

    const url = await putAttachment('teste-do-storage.webp', Buffer.from([1, 2, 3]), 'image/webp')
    expect(url).toBe('/uploads/teste-do-storage.webp')

    // Limpa o que o proprio teste escreveu: um arquivo solto em `uploads/` seria lixo
    // que aparece no `git status` de quem rodar a suite.
    const { promises: fs } = await import('fs')
    const path = await import('path')
    await fs.unlink(path.resolve(process.cwd(), 'uploads', 'teste-do-storage.webp')).catch(() => {})
  })

  it('diz QUAIS variáveis faltam, e só os nomes', async () => {
    const { storageFalta, storageMode } = await carregarStorageCom({
      NODE_ENV: 'production',
      S3_ENDPOINT: '', R2_ACCOUNT_ID: '', R2_ACCESS_KEY_ID: '',
      R2_SECRET_ACCESS_KEY: '', R2_BUCKET: '', R2_PUBLIC_URL: '',
    })

    expect(storageMode).toBe('local')
    expect(storageFalta).toContain('R2_BUCKET')

    // NOMES, NUNCA VALORES — esta lista vai pro log e pro /health, e os dois sao lidos
    // por gente que nao deveria ver credencial.
    //
    // A regra e "cada item e um NOME de variavel de ambiente", e nao "nao contem a
    // palavra secret": `R2_SECRET_ACCESS_KEY` e um nome legitimo que contem "SECRET" — foi
    // exatamente nisso que a primeira versao deste teste tropecou. Um valor vazado nao
    // casaria com esta forma (tem minuscula, digito colado, `=`, `/`, `+`).
    for (const item of storageFalta) {
      expect(item).toMatch(/^[A-Z][A-Z0-9_]*$/)
    }
  })
})
