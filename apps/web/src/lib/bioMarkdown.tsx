
import * as React from 'react'

const URL_RE = /^https?:\/\/[^\s]+$/i

type Token =
  | { kind: 'text';   value: string }
  | { kind: 'bold';   value: string }
  | { kind: 'italic'; value: string }
  | { kind: 'code';   value: string }
  | { kind: 'link';   text: string; href: string }
  | { kind: 'br' }

function tokenize(input: string): Token[] {
  const tokens: Token[] = []
  let i = 0
  const len = input.length

  while (i < len) {

    if (input[i] === '\n') { tokens.push({ kind: 'br' }); i++; continue }

    if (input[i] === '*' && input[i+1] === '*') {
      const end = input.indexOf('**', i + 2)
      if (end !== -1) {
        tokens.push({ kind: 'bold', value: input.slice(i+2, end) })
        i = end + 2; continue
      }
    }

    if (input[i] === '*') {
      const end = input.indexOf('*', i + 1)
      if (end !== -1) {
        tokens.push({ kind: 'italic', value: input.slice(i+1, end) })
        i = end + 1; continue
      }
    }

    if (input[i] === '`') {
      const end = input.indexOf('`', i + 1)
      if (end !== -1) {
        tokens.push({ kind: 'code', value: input.slice(i+1, end) })
        i = end + 1; continue
      }
    }

    if (input[i] === '[') {
      const close = input.indexOf(']', i + 1)
      if (close !== -1 && input[close+1] === '(') {
        const urlEnd = input.indexOf(')', close + 2)
        if (urlEnd !== -1) {
          const text = input.slice(i+1, close)
          const href = input.slice(close+2, urlEnd)
          if (URL_RE.test(href)) {
            tokens.push({ kind: 'link', text, href })
            i = urlEnd + 1; continue
          }
        }
      }
    }

    let j = i
    while (j < len && !'*`[\n'.includes(input[j])) j++
    if (j > i) {
      tokens.push({ kind: 'text', value: input.slice(i, j) })
      i = j
    } else {

      tokens.push({ kind: 'text', value: input[i] })
      i++
    }
  }

  return tokens
}

export interface BioMarkdownProps {
  text:      string
  className?: string
}

export function BioMarkdown({ text, className }: BioMarkdownProps) {
  const tokens = React.useMemo(() => tokenize(text), [text])
  return (
    <span className={className}>
      {tokens.map((t, i) => {
        switch (t.kind) {
          case 'text':   return <React.Fragment key={i}>{t.value}</React.Fragment>
          case 'bold':   return <strong key={i} className="font-semibold text-(--text-1)">{t.value}</strong>
          case 'italic': return <em     key={i} className="italic">{t.value}</em>
          case 'code':   return <code   key={i} className="font-mono text-[0.9em] px-1 py-0.5 rounded bg-(--raised) text-(--text-1)">{t.value}</code>
          case 'link':   return (
            <a key={i} href={t.href} target="_blank" rel="noopener noreferrer"
               className="text-(--accent) underline underline-offset-2 hover:opacity-80 transition-opacity">
              {t.text}
            </a>
          )
          case 'br':     return <br key={i} />
        }
      })}
    </span>
  )
}
