import { Router, Request, Response } from 'express'
import { requireAuth } from '../middleware/auth'
import { asyncHandler } from '../lib/asyncHandler'
import { env } from '../lib/env'

/**
 * Proxy pra Giphy API v1. Mantém a API key no backend.
 * Endpoints:
 *   GET /api/gif/featured?pos=&limit=
 *   GET /api/gif/search?q=&pos=&limit=
 *   GET /api/gif/enabled
 *
 * Resposta normalizada: { results: [{ id, title, preview, full, width, height, size }] }
 */

const GIPHY_BASE = 'https://api.giphy.com/v1/gifs'

interface GiphyImage { url: string; width?: string; height?: string; size?: string }
interface GiphyGif {
  id:    string
  title: string
  images: {
    original?:           GiphyImage
    fixed_height?:       GiphyImage
    fixed_height_small?: GiphyImage
    preview_gif?:        GiphyImage
    downsized?:          GiphyImage
  }
}

function normalize(items: GiphyGif[]) {
  return items.map((g) => {
    const original = g.images.original
    const preview  = g.images.fixed_height_small?.url
                  || g.images.preview_gif?.url
                  || g.images.fixed_height?.url
                  || original?.url
                  || ''
    const full = original?.url || g.images.downsized?.url || preview
    return {
      id:     g.id,
      title:  g.title || 'gif',
      preview,
      full,
      width:  original?.width  ? Number(original.width)  : undefined,
      height: original?.height ? Number(original.height) : undefined,
      size:   original?.size   ? Number(original.size)   : 0,
    }
  }).filter((g) => g.full)
}

const router = Router()

function ensureKey(res: Response): boolean {
  if (!env.GIPHY_API_KEY) {
    res.status(503).json({ error: 'GIF picker desabilitado — GIPHY_API_KEY ausente no backend' })
    return false
  }
  return true
}

async function giphyFetch(path: string, params: Record<string, string>) {
  const url = new URL(`${GIPHY_BASE}${path}`)
  url.searchParams.set('api_key', env.GIPHY_API_KEY!)
  url.searchParams.set('rating',  'pg-13')
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v)

  const r = await fetch(url.toString())
  if (!r.ok) throw new Error(`Giphy ${r.status}`)
  return r.json() as Promise<any>
}

router.get(
  '/featured',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    if (!ensureKey(res)) return
    const limit  = String(Math.min(Number(req.query.limit ?? 24), 50))
    const offset = String(req.query.pos ?? '0')
    const data   = await giphyFetch('/trending', { limit, offset })
    const next   = String((Number(offset) || 0) + Number(limit))
    res.json({ data: { results: normalize(data.data ?? []), next } })
  })
)

router.get(
  '/search',
  requireAuth,
  asyncHandler(async (req: Request, res: Response) => {
    if (!ensureKey(res)) return
    const q = String(req.query.q ?? '').trim()
    if (!q) return res.json({ data: { results: [], next: null } })
    const limit  = String(Math.min(Number(req.query.limit ?? 24), 50))
    const offset = String(req.query.pos ?? '0')
    const data   = await giphyFetch('/search', { q, limit, offset })
    const next   = String((Number(offset) || 0) + Number(limit))
    res.json({ data: { results: normalize(data.data ?? []), next } })
  })
)

router.get(
  '/enabled',
  asyncHandler(async (_req: Request, res: Response) => {
    res.json({ data: { enabled: !!env.GIPHY_API_KEY } })
  })
)

export default router
