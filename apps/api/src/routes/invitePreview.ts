import { Router, Response } from 'express'
import type { Request } from '../lib/requisicao'
import { and, eq, sql } from 'drizzle-orm'
import { NAO_E_BOT } from '../lib/contagemDeMembros'
import { db } from '../db'
import { servers, serverMembers } from '../db/schema'
import { asyncHandler } from '../lib/asyncHandler'

const router = Router()

const PAGINA_DE_DOWNLOAD = 'https://github.com/Xryonz/Astra/releases/latest'

const ESTILO = `
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px 16px;
    background: #06060e; color: #e4e4eb;
    font: 15px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif;
  }
  main { width: 100%; max-width: 440px; background: #09091a; border: 1px solid #363741; border-radius: 12px; overflow: hidden; }
  .capa { display: block; width: 100%; height: 132px; object-fit: cover; background: #0f0f24; }
  .corpo { padding: 22px 22px 24px; }
  .marca { margin: 0 0 14px; font-size: 12px; letter-spacing: .28em; text-transform: uppercase; color: #8c8c94; }
  .icone { display: block; width: 64px; height: 64px; margin-bottom: 14px; border-radius: 50%; object-fit: cover; background: #0f0f24; }
  h1 { margin: 0; font: 400 26px/1.2 Georgia, "Times New Roman", serif; color: #e4e4eb; overflow-wrap: anywhere; }
  .estrelas { margin: 6px 0 0; color: #c0c0c6; }
  .bloco { margin-top: 16px; padding: 14px 16px; background: #0f0f24; border: 1px solid #363741; border-radius: 8px; }
  h2 { margin: 0 0 6px; font-size: 13px; font-weight: 600; color: #e4e4eb; }
  .bloco p { margin: 0; color: #c0c0c6; font-size: 14px; }
  .bloco p.apoio { margin-top: 10px; color: #8c8c94; font-size: 13px; }
  .codigo {
    display: block; margin-top: 10px; padding: 10px 12px; background: #15152e; border-radius: 8px;
    font: 15px/1.4 ui-monospace, "Cascadia Mono", Consolas, monospace; color: #e4e4eb;
    user-select: all; overflow-wrap: anywhere;
  }
  .baixar {
    display: inline-block; margin-top: 12px; padding: 9px 16px; border-radius: 8px;
    background: #15152e; border: 1px solid #d4d8e0; color: #e4e4eb; font-weight: 600; text-decoration: none;
  }
  .baixar:hover { background: #1c1c38; }
  .baixar:focus-visible { outline: 2px solid #d4d8e0; outline-offset: 3px; }
`

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function moldura(titulo: string, cabecalhoExtra: string, conteudo: string): string {
  return `<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex">
  <meta name="theme-color" content="#06060e">
  <title>${titulo}</title>
${cabecalhoExtra}
  <style>${ESTILO}</style>
</head>
<body>
  <main>
${conteudo}
  </main>
</body>
</html>`
}

const BLOCO_DE_DOWNLOAD = `
      <section class="bloco">
        <h2>Ainda não tem o Astra</h2>
        <p>O Astra é para Windows. Baixe o arquivo que termina em <strong>win-x64.zip</strong>, extraia e abra o Astra.exe.</p>
        <a class="baixar" href="${PAGINA_DE_DOWNLOAD}">Baixar o Astra</a>
        <p class="apoio">Depois de entrar na sua conta, volte a este convite.</p>
      </section>`

router.get(
  '/:code',
  asyncHandler(async (req: Request, res: Response) => {
    const codigo = req.params.code

    const [server] = await db.select({
      id:        servers.id,
      name:      servers.name,
      iconUrl:   servers.iconUrl,
      bannerUrl: servers.bannerUrl,
    }).from(servers).where(eq(servers.inviteCode, codigo)).limit(1)

    res.setHeader('Content-Type', 'text/html; charset=utf-8')

    if (!server) {
      res.setHeader('Cache-Control', 'no-store')
      return res.status(404).send(moldura('Convite não encontrado · Astra', '', `
    <div class="corpo">
      <p class="marca">Astra</p>
      <h1>Este convite não existe mais</h1>
      <p class="estrelas">O link pode ter expirado ou chegado incompleto. Peça um novo a quem te convidou.</p>${BLOCO_DE_DOWNLOAD}
    </div>`))
    }

    const [{ count }] = await db.select({ count: sql<number>`count(*)::int` })
      .from(serverMembers).where(and(eq(serverMembers.serverId, server.id), NAO_E_BOT))

    const apiBase = `${req.protocol}://${req.get('host')}`
    const toPublicUrl = (u: string | null): string | null => {
      if (!u || u.startsWith('data:')) return null
      return u.startsWith('/') ? `${apiBase}${u}` : u
    }
    const banner = toPublicUrl(server.bannerUrl)
    const icone  = toPublicUrl(server.iconUrl)
    const imagemDaPrevia = banner ?? icone

    const titulo = esc(`Junte-se à constelação ${server.name} no Astra`)
    const estrelas = `${count} ${count === 1 ? 'estrela brilha' : 'estrelas brilham'} por aqui`
    const previa = [
      `  <meta property="og:title" content="${titulo}">`,
      `  <meta property="og:description" content="${esc(`${estrelas}. Toque para entrar.`)}">`,
      imagemDaPrevia ? `  <meta property="og:image" content="${esc(imagemDaPrevia)}">` : '',
      `  <meta property="og:type" content="website">`,
      `  <meta property="og:site_name" content="Astra">`,
      `  <meta name="twitter:card" content="summary">`,
    ].filter(Boolean).join('\n')

    res.setHeader('Cache-Control', 'public, max-age=300')
    res.send(moldura(titulo, previa, `
    ${banner ? `<img class="capa" src="${esc(banner)}" alt="">` : ''}
    <div class="corpo">
      <p class="marca">Astra · convite</p>
      ${!banner && icone ? `<img class="icone" src="${esc(icone)}" alt="">` : ''}
      <h1>${esc(server.name)}</h1>
      <p class="estrelas">${esc(estrelas)}.</p>
      <section class="bloco">
        <h2>Já tem o Astra</h2>
        <p>Clique em <strong>+</strong> na barra lateral, escolha <strong>Entrar com convite</strong> e cole este código:</p>
        <code class="codigo">${esc(codigo)}</code>
      </section>${BLOCO_DE_DOWNLOAD}
    </div>`))
  })
)

export default router
