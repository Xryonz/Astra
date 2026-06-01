import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { ACCENT_OPTIONS, BG_OPTIONS, applyTheme } from '@/lib/theme'
import { cn } from '@/lib/utils'
import { SectionHeader, Row } from './_shared'

/**
 * Aparência: cor de destaque + fundo. Aplica direto no DOM via applyTheme,
 * persiste no localStorage. Sem botão "Salvar" — feedback é instantâneo.
 */
export default function AppearanceSection() {
  const [accentId, setAccentId] = useState(() => localStorage.getItem('umbra-accent') ?? 'gold')
  const [bgId,     setBgId]     = useState(() => localStorage.getItem('umbra-bg')     ?? 'void')

  // Aplica imediatamente ao mudar
  useEffect(() => { applyTheme(accentId, bgId) }, [accentId, bgId])

  return (
    <div>
      <SectionHeader
        title="Aparência"
        description="Personalize as cores da interface. Mudanças aparecem na hora."
      />

      <Row label="Cor de destaque" hint="Usada em botões, links, hover e elementos ativos.">
        <div className="flex flex-wrap gap-2">
          {ACCENT_OPTIONS.map((a) => {
            const active = accentId === a.id
            return (
              <button
                key={a.id}
                type="button"
                title={a.label}
                onClick={() => setAccentId(a.id)}
                className={cn(
                  'size-9 cursor-pointer transition-all border-2',
                  active ? 'border-foreground scale-110' : 'border-transparent hover:scale-105',
                )}
                style={{ background: a.value }}
              >
                {active && <Check className="size-3.5 text-white mx-auto" />}
              </button>
            )
          })}
        </div>
        <p className="text-xs text-(--text-3) mt-2 m-0">
          Atual: <span className="text-foreground">{ACCENT_OPTIONS.find((a) => a.id === accentId)?.label}</span>
        </p>
      </Row>

      <Row label="Fundo da interface" hint="Tom base do app. Afeta todas as telas.">
        <div className="flex flex-col gap-1.5">
          {BG_OPTIONS.map((b) => {
            const active = bgId === b.id
            return (
              <button
                key={b.id}
                type="button"
                onClick={() => setBgId(b.id)}
                className={cn(
                  'flex items-center gap-3 px-3 py-2 cursor-pointer transition-colors border text-left',
                  active
                    ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                    : 'border-(--border) text-(--text-2) hover:border-(--accent)',
                )}
              >
                <div className="w-9 h-6 border border-white/10 shrink-0" style={{ background: b.base }} />
                <span className="text-sm font-medium flex-1">{b.label}</span>
                {active && <Check className="size-4" />}
              </button>
            )
          })}
        </div>
      </Row>
    </div>
  )
}
