
import { decode } from 'blurhash'

const cache = new Map<string, string | null>()

export function blurhashToDataURL(hash: string, w = 32, h = 32): string | null {
  const hit = cache.get(hash)
  if (hit !== undefined) return hit
  try {
    const pixels = decode(hash, w, h)
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) { cache.set(hash, null); return null }
    const imageData = ctx.createImageData(w, h)
    imageData.data.set(pixels)
    ctx.putImageData(imageData, 0, 0)
    const url = canvas.toDataURL()
    cache.set(hash, url)
    return url
  } catch {
    cache.set(hash, null)
    return null
  }
}
