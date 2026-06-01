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

/**
 * Map estática de lang → import. Vite resolve cada `() => import(...)` num chunk
 * separado em build time. O bundle "agrupado" não existe — cada lang é só
 * baixada quando alguém pede.
 */
const LANG_LOADERS: Record<string, () => Promise<any>> = {
  js:         () => import('@shikijs/langs/javascript'),
  javascript: () => import('@shikijs/langs/javascript'),
  jsx:        () => import('@shikijs/langs/jsx'),
  ts:         () => import('@shikijs/langs/typescript'),
  typescript: () => import('@shikijs/langs/typescript'),
  tsx:        () => import('@shikijs/langs/tsx'),
  json:       () => import('@shikijs/langs/json'),
  python:     () => import('@shikijs/langs/python'),
  py:         () => import('@shikijs/langs/python'),
  rust:       () => import('@shikijs/langs/rust'),
  go:         () => import('@shikijs/langs/go'),
  java:       () => import('@shikijs/langs/java'),
  kotlin:     () => import('@shikijs/langs/kotlin'),
  swift:      () => import('@shikijs/langs/swift'),
  bash:       () => import('@shikijs/langs/bash'),
  shell:      () => import('@shikijs/langs/shellscript'),
  sh:         () => import('@shikijs/langs/shellscript'),
  sql:        () => import('@shikijs/langs/sql'),
  html:       () => import('@shikijs/langs/html'),
  css:        () => import('@shikijs/langs/css'),
  scss:       () => import('@shikijs/langs/scss'),
  yaml:       () => import('@shikijs/langs/yaml'),
  yml:        () => import('@shikijs/langs/yaml'),
  toml:       () => import('@shikijs/langs/toml'),
  xml:        () => import('@shikijs/langs/xml'),
  md:         () => import('@shikijs/langs/markdown'),
  markdown:   () => import('@shikijs/langs/markdown'),
  php:        () => import('@shikijs/langs/php'),
  ruby:       () => import('@shikijs/langs/ruby'),
  c:          () => import('@shikijs/langs/c'),
  cpp:        () => import('@shikijs/langs/cpp'),
  csharp:     () => import('@shikijs/langs/csharp'),
  cs:         () => import('@shikijs/langs/csharp'),
  dockerfile: () => import('@shikijs/langs/docker'),
  diff:       () => import('@shikijs/langs/diff'),
  graphql:    () => import('@shikijs/langs/graphql'),
  vue:        () => import('@shikijs/langs/vue'),
  svelte:     () => import('@shikijs/langs/svelte'),
}

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

/** Verifica se uma lang é suportada pelo highlighter. */
export function isSupportedLang(lang: string): boolean {
  return lang.toLowerCase() in LANG_LOADERS
}
