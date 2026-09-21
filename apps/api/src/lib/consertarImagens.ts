import fs from 'fs'
import path from 'path'
import sharp from 'sharp'
import { sql } from 'drizzle-orm'
import { db } from '../db'
import { putAttachment, storageMode } from './storage'
import { logger } from './logger'

const LADO_DE_EXIBICAO = 256
const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')
const DATA_URI = /^data:([\w.+-]+\/[\w.+-]+)?(;base64)?,(.*)$/s

const CAMPOS = [
  { tabela: 'User', coluna: 'avatarUrl', par: 'avatarFullUrl' },
  { tabela: 'User', coluna: 'bannerUrl', par: null },
  { tabela: 'Server', coluna: 'iconUrl', par: 'iconFullUrl' },
  { tabela: 'Server', coluna: 'bannerUrl', par: null },
  { tabela: 'ServerRole', coluna: 'iconUrl', par: null },
  { tabela: 'ServerEmoji', coluna: 'url', par: null },
] as const

type Linha = { id: string; valor: string; par: string | null }

function bytesDoDataUri(valor: string): Buffer | null {
  const m = DATA_URI.exec(valor)
  if (!m) return null
  try {
    return m[2] ? Buffer.from(m[3], 'base64') : Buffer.from(decodeURIComponent(m[3]))
  } catch {
    return null
  }
}

async function baixar(url: string): Promise<Buffer | null> {
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(20_000) })
    if (!r.ok) return null
    return Buffer.from(await r.arrayBuffer())
  } catch {
    return null
  }
}

function doDisco(valor: string): Buffer | null {
  const nome = path.basename(valor)
  const caminho = path.join(UPLOAD_DIR, nome)
  if (!caminho.startsWith(UPLOAD_DIR) || !fs.existsSync(caminho)) return null
  try {
    return fs.readFileSync(caminho)
  } catch {
    return null
  }
}

async function origem(linha: Linha): Promise<Buffer | null> {
  const doPar = linha.par
  if (doPar) {
    const embutida = bytesDoDataUri(doPar)
    if (embutida) return embutida
    if (doPar.startsWith('http')) {
      const baixada = await baixar(doPar)
      if (baixada) return baixada
    }
  }
  return doDisco(linha.valor)
}

export async function consertarImagensMortas(): Promise<void> {
  if (storageMode === 'local') return
  try {
    let recuperadas = 0
    let limpas = 0

    for (const campo of CAMPOS) {
      const coluna = sql.identifier(campo.coluna)
      const tabela = sql.identifier(campo.tabela)
      const par = campo.par ? sql.identifier(campo.par) : sql`NULL`
      const { rows } = await db.execute<Linha>(sql`
        SELECT id, ${coluna} AS valor, ${par} AS par
        FROM ${tabela}
        WHERE ${coluna} LIKE '/uploads/%'
        LIMIT 500
      `)
      if (rows.length === 0) continue

      for (const linha of rows) {
        const bruto = await origem(linha)
        let novo: string | null = null
        if (bruto) {
          try {
            const pequeno = await sharp(bruto)
              .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
              .webp({ quality: 90, effort: 6, alphaQuality: 100, smartSubsample: true })
              .toBuffer()
            novo = await putAttachment(`${linha.id}-${Date.now().toString(36)}.webp`, pequeno, 'image/webp')
          } catch {
            novo = null
          }
        }
        await db.execute(sql`UPDATE ${tabela} SET ${coluna} = ${novo} WHERE id = ${linha.id}`)
        if (novo) recuperadas++
        else limpas++
      }
      logger.info('Imagens', `${campo.tabela}.${campo.coluna}: ${rows.length} endereços mortos tratados`)
    }

    if (recuperadas || limpas) {
      logger.info('Imagens', `conserto concluído: ${recuperadas} recuperadas, ${limpas} limpas`)
    }
  } catch (e) {
    logger.warn('Imagens', 'conserto falhou', e)
  }
}
