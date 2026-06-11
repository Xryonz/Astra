import { Router, Request, Response } from 'express'
import { eq, sql } from 'drizzle-orm'
import { db } from '../db'
import { servers, serverMembers } from '../db/schema'
import { asyncHandler } from '../lib/asyncHandler'
import { env } from '../lib/env'

const router = Router()

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * GET /i/:code — página HTML com OG tags + redirect imediato pro site.
 *
 * O site é SPA (Vercel serve o mesmo index.html pra toda rota), então o
 * crawler do WhatsApp/Telegram/Discord nunca veria um preview por convite.
 * O link compartilhado (lib/native.ts shareInvite) aponta pra cá: crawler
 * lê as OG tags; humano é redirecionado pro /invite/:code do site.
 */
router.get(
  '/:code',
  asyncHandler(async (req: Request, res: Response) => {
    const target = `${env.CLIENT_URL}/invite/${encodeURIComponent(req.params.code)}`

    const [server] = await db.select({
      id:      servers.id,
      name:    servers.name,
      iconUrl: servers.iconUrl,
    }).from(servers).where(eq(servers.inviteCode, req.params.code)).limit(1)

    // Convite inválido: manda pro site mesmo assim — lá tem a tela de erro
    if (!server) return res.redirect(target)

    const [{ count }] = await db.select({ count: sql<number>`count(*)::int` })
      .from(serverMembers).where(eq(serverMembers.serverId, server.id))

    const apiBase = `${req.protocol}://${req.get('host')}`
    const image   = server.iconUrl
      ? `${apiBase}${server.iconUrl}`
      : `${env.CLIENT_URL}/web-app-manifest-512x512.png`
    const title = esc(`Junte-se à constelação ${server.name} no Astra`)
    const desc  = esc(`${count} ${count === 1 ? 'estrela brilha' : 'estrelas brilham'} por aqui. Toque para entrar.`)

    res.setHeader('Cache-Control', 'public, max-age=300')
    res.setHeader('Content-Type', 'text/html; charset=utf-8')
    res.send(`<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <title>${title}</title>
  <meta property="og:title" content="${title}">
  <meta property="og:description" content="${desc}">
  <meta property="og:image" content="${esc(image)}">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Astra">
  <meta name="twitter:card" content="summary">
  <meta name="theme-color" content="#06060e">
  <meta http-equiv="refresh" content="0;url=${esc(target)}">
</head>
<body style="background:#06060e;color:#e8e6e3;font-family:system-ui;display:grid;place-items:center;min-height:100vh;margin:0">
  <a href="${esc(target)}" style="color:#c9a96e">Entrar na constelação ${esc(server.name)} →</a>
</body>
</html>`)
  })
)

export default router
