/**
 * Cliente do highlighter — fala com o Web Worker (highlighter.worker) e
 * cai pro fallback main-thread (highlighter.ts) se o worker não existir
 * ou falhar (WebView muito antigo).
 *
 * Uso no CodeBlock: highlightCode(code, lang) → Promise<html | null>.
 */
import { isSupportedLang } from './shikiLangs'

export { isSupportedLang }

let worker: Worker | null = null
let workerBroken = false
let seq = 0
const pending = new Map<number, { resolve: (h: string) => void; reject: (e: unknown) => void }>()

function getWorker(): Worker | null {
  if (workerBroken) return null
  if (worker) return worker
  try {
    worker = new Worker(new URL('./highlighter.worker.ts', import.meta.url), { type: 'module' })
    worker.onmessage = (e: MessageEvent<{ id: number; html?: string; error?: string }>) => {
      const { id, html, error } = e.data
      const p = pending.get(id)
      if (!p) return
      pending.delete(id)
      if (error || html == null) p.reject(new Error(error ?? 'no html'))
      else p.resolve(html)
    }
    worker.onerror = () => {
      // Worker morreu — marca quebrado e rejeita os pendentes pro fallback assumir.
      workerBroken = true
      for (const [, p] of pending) p.reject(new Error('worker error'))
      pending.clear()
      worker = null
    }
    return worker
  } catch {
    workerBroken = true
    return null
  }
}

async function fallbackMainThread(code: string, lang: string): Promise<string | null> {
  try {
    const { getHighlighter } = await import('./highlighter')
    const h = await getHighlighter(lang)
    return h.codeToHtml(code, { lang, theme: 'min-dark' })
  } catch {
    return null
  }
}

export async function highlightCode(code: string, lang: string): Promise<string | null> {
  const norm = lang.toLowerCase()
  if (!isSupportedLang(norm)) return null

  const w = getWorker()
  if (w) {
    try {
      const id = ++seq
      return await new Promise<string>((resolve, reject) => {
        pending.set(id, { resolve, reject })
        w.postMessage({ id, code, lang: norm })
      })
    } catch {
      // cai pro fallback main-thread
    }
  }
  return fallbackMainThread(code, norm)
}
