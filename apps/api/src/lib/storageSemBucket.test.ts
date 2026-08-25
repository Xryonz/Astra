import { describe, it, expect, vi, afterEach } from 'vitest'

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

    for (const item of storageFalta) {
      expect(item).toMatch(/^[A-Z][A-Z0-9_]*$/)
    }
  })
})
