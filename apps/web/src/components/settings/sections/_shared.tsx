import { ReactNode } from 'react'

/**
 * SectionHeader — título grande + descrição cinza + hairline.
 * Respira mais em telas grandes (mb-10 lg).
 */
export function SectionHeader({ title, description }: { title: string; description?: string }) {
  return (
    <header className="mb-8 sm:mb-10 pb-5 sm:pb-6 border-b border-(--border)">
      <h2
        className="text-2xl sm:text-3xl m-0 mb-2 font-normal tracking-tight text-foreground leading-tight"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        {title}
      </h2>
      {description && (
        <p className="text-sm sm:text-base text-(--text-3) m-0 max-w-prose leading-relaxed">
          {description}
        </p>
      )}
    </header>
  )
}

/**
 * Row — bloco de subsetting (label + hint acima, content abaixo).
 * Padding generoso (py-6) + gap-3 entre label e content pra não ficar "pressionado".
 */
export function Row({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-3 py-5 sm:py-6 border-b border-(--border) last:border-b-0">
      <div>
        <p className="text-sm font-medium text-foreground m-0">{label}</p>
        {hint && <p className="text-xs text-(--text-3) m-0 mt-1 leading-relaxed max-w-prose">{hint}</p>}
      </div>
      <div>{children}</div>
    </div>
  )
}

/**
 * SaveStatus — indicador '✓ Salvo' / 'Salvando…' / erro.
 */
export function SaveStatus({ status, error }: { status: 'idle' | 'saving' | 'saved' | 'error'; error?: string }) {
  if (status === 'idle') return null
  return (
    <p className={
      status === 'error'  ? 'text-xs text-(--danger) m-0' :
      status === 'saving' ? 'text-xs text-(--text-3) m-0' :
                            'text-xs text-(--success) m-0'
    }>
      {status === 'saving' && 'Salvando…'}
      {status === 'saved'  && '✓ Salvo'}
      {status === 'error'  && (error ?? 'Erro ao salvar')}
    </p>
  )
}
