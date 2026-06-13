/**
 * Shiki highlighter — fine-grained, lazy por linguagem.
 *
 * Por que não `import { createHighlighter } from 'shiki'`?
 * Esse entry-point carrega TODAS as ~150 langs e ~50 themes no bundle (1.6MB gzip!).
 * Aqui usamos `shiki/core` + grammar/theme individuais via dynamic import:
 *  - Engine + theme carregam UMA VEZ (~50KB gzip).
 *  - Cada linguagem é seu próprio chunk de ~5-20KB, carregado só quando o user
 *    abre um code block daquela lang.
 *  - Lang desconhecida → fallback plain (sem download de grammar).
 */
import type { HighlighterCore } from 'shiki/core'
import { LANG_LOADERS, isSupportedLang } from './shikiLangs'

export { isSupportedLang }

let highlighterPromise: Promise<HighlighterCore> | null = null
const loadedLangs = new Set<string>()

/**
 * Garante que o engine está pronto E que a linguagem pedida está carregada.
 * Idempotente: se a lang já foi carregada, não baixa de novo.
 *
 * @param lang — id normalizado (lowercase). Use isSupportedLang antes pra evitar erro.
 */
export async function getHighlighter(lang: string): Promise<HighlighterCore> {
  if (!highlighterPromise) {
    highlighterPromise = (async () => {
      const [{ createHighlighterCore }, { createOnigurumaEngine }] = await Promise.all([
        import('shiki/core'),
        import('shiki/engine/oniguruma'),
      ])
      return createHighlighterCore({
        themes: [import('@shikijs/themes/min-dark')],
        langs:  [],
        engine: createOnigurumaEngine(import('shiki/wasm')),
      })
    })()
  }

  const h = await highlighterPromise

  if (!loadedLangs.has(lang) && LANG_LOADERS[lang]) {
    await h.loadLanguage(LANG_LOADERS[lang]() as any)
    loadedLangs.add(lang)
  }

  return h
}
