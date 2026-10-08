import sharp from 'sharp'
import { bytesDoDataUri } from './storage'

export const LADO_DA_FOTO = 256
const TETO_DE_PIXELS_DA_ANIMACAO = 60_000_000

export interface Recorte {
  x: number
  y: number
  lado: number
}

export class AnimacaoGrandeDemais extends Error {}

export async function recortarFoto(dataUri: string, recorte: Recorte): Promise<string | null> {
  const bytes = bytesDoDataUri(dataUri)
  if (!bytes) return null

  const meta = await sharp(bytes, { animated: true }).metadata()
  const largura = meta.width ?? 0
  const altura = meta.pageHeight ?? meta.height ?? 0
  const quadros = meta.pages ?? 1
  if (largura * altura * quadros > TETO_DE_PIXELS_DA_ANIMACAO) throw new AnimacaoGrandeDemais()

  const lado = Math.min(recorte.lado, largura - recorte.x, altura - recorte.y)
  if (lado < 1) return null

  const animada = quadros > 1
  const saida = await sharp(bytes, { animated: true })
    .extract({ left: recorte.x, top: recorte.y, width: lado, height: lado })
    .resize({ width: LADO_DA_FOTO, height: LADO_DA_FOTO, withoutEnlargement: true })
    .webp(animada
      ? { quality: 80, effort: 6 }
      : { quality: 92, effort: 6, alphaQuality: 100, smartSubsample: true })
    .toBuffer()
  return `data:image/webp;base64,${saida.toString('base64')}`
}
