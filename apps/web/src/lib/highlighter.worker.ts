
import { createHighlighterCore, type HighlighterCore } from 'shiki/core'
import { createOnigurumaEngine } from 'shiki/engine/oniguruma'
import { LANG_LOADERS } from './shikiLangs'

let highlighterPromise: Promise<HighlighterCore> | null = null
const loadedLangs = new Set<string>()

async function ensure(lang: string): Promise<HighlighterCore> {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighterCore({
      themes: [import('@shikijs/themes/min-dark')],
      langs:  [],
      engine: createOnigurumaEngine(import('shiki/wasm')),
    })
  }
  const h = await highlighterPromise
  if (!loadedLangs.has(lang) && LANG_LOADERS[lang]) {
    await h.loadLanguage(LANG_LOADERS[lang]() as any)
    loadedLangs.add(lang)
  }
  return h
}

self.onmessage = async (e: MessageEvent<{ id: number; code: string; lang: string }>) => {
  const { id, code, lang } = e.data
  try {
    const h = await ensure(lang)
    const html = h.codeToHtml(code, { lang, theme: 'min-dark' })
    ;(self as unknown as Worker).postMessage({ id, html })
  } catch (err) {
    ;(self as unknown as Worker).postMessage({ id, error: String(err) })
  }
}
