import { useEffect, useRef, useState } from 'react'
import { Check } from 'lucide-react'
import { ACCENT_OPTIONS, BG_OPTIONS, applyTheme } from '@/lib/theme'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/lib/utils'
import { SectionHeader, Row } from './_shared'

/**
 * Aparência: cor de destaque + fundo. Aplica direto no DOM via applyTheme,
 * persiste no localStorage. Sem botão "Salvar" — feedback é instantâneo.
 *
 * Sync server: PATCH /api/profile/preferences debounced (600ms). Erro silencioso
 * — local já está aplicado, sync server eventualmente refaz.
 */
export default function AppearanceSection() {
  const [accentId, setAccentId] = useState(() =>
    localStorage.getItem('astra-accent') ?? localStorage.getItem('umbra-accent') ?? 'gold',
  )
  const [bgId,     setBgId]     = useState(() =>
    localStorage.getItem('astra-bg')     ?? localStorage.getItem('umbra-bg')     ?? 'void',
  )

  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const debounceRef = useRef<number | null>(null)

  useEffect(() => {
    applyTheme(accentId, bgId)
    if (!isAuthenticated) return
    if (debounceRef.current) window.clearTimeout(debounceRef.current)
    debounceRef.current = window.setTimeout(() => {
      api.patch('/api/profile/preferences', {
        preferences: { accent: accentId, bg: bgId },
      }).catch(() => {})
    }, 600)
    return () => { if (debounceRef.current) window.clearTimeout(debounceRef.current) }
  }, [accentId, bgId, isAuthenticated])

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
