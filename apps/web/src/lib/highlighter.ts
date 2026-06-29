
import type { HighlighterCore } from 'shiki/core'
import { LANG_LOADERS, isSupportedLang } from './shikiLangs'

export { isSupportedLang }

let highlighterPromise: Promise<HighlighterCore> | null = null
const loadedLangs = new Set<string>()

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
