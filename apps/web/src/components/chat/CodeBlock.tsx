import { useEffect, useState } from 'react'
import { highlightCode, isSupportedLang } from '@/lib/highlighterClient'

interface Props {
  code: string
  lang: string
}

export default function CodeBlock({ code, lang }: Props) {
  const [html, setHtml] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const normLang = isSupportedLang(lang) ? lang.toLowerCase() : null

    if (!normLang) { setHtml(null); return }

    highlightCode(code, normLang)
      .then((out) => { if (!cancelled) setHtml(out) })
      .catch(() => { if (!cancelled) setHtml(null) })

    return () => { cancelled = true }
  }, [code, lang])

  if (html) {
    return (
      <div
        className="my-2 overflow-x-auto border border-(--border) bg-(--raised) [&_pre]:p-3 [&_pre]:m-0 [&_pre]:text-[12px] [&_pre]:bg-transparent! [&_pre]:font-(family-name:--font-mono)"
        dangerouslySetInnerHTML={{ __html: html }}
      />
    )
  }

  return (
    <pre
      className="my-2 p-3 overflow-x-auto border border-(--border) bg-(--raised) text-[12px] font-(family-name:--font-mono) text-foreground"
    >
      {code}
    </pre>
  )
}
