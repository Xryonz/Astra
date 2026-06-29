
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '@/lib/api'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import type { UserBadges } from '@astra/types'

interface Chip {
  key: string
  icon: string
  name: string
  color: string
  description: string | null
  sub?: string
}

export function ProfileBadges({ userId }: { userId: string }) {
  const { t } = useTranslation()
  const { data } = useQuery<UserBadges>({
    queryKey:  ['badges', userId],
    queryFn:   async () => (await api.get(`/api/users/${userId}/badges`)).data.data,
    staleTime: 60_000,
  })

  const chips: Chip[] = [
    ...(data?.global ?? []).map((b) => ({
      key: `g-${b.id}`, icon: b.icon, name: b.name, color: b.color, description: b.description,
    })),
    ...(data?.server ?? []).map((b) => ({
      key: `s-${b.badgeId}`, icon: b.icon, name: b.name,
      color: b.color ?? 'var(--accent)', description: b.description, sub: b.serverName,
    })),
  ]

  if (chips.length === 0) return null

  return (
    <div className="mb-6">
      <span className="ed-label block mb-1.5">{t('profile.badges')}</span>
      <div className="flex flex-wrap gap-2">
        {chips.map((b) => (
          <Tooltip key={b.key}>
            <TooltipTrigger asChild>
              <span
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs cursor-default"
                style={{
                  borderColor: `color-mix(in srgb, ${b.color} 45%, transparent)`,
                  background:   `color-mix(in srgb, ${b.color} 12%, transparent)`,
                  color:        b.color,
                }}
              >
                <span className="text-sm leading-none">{b.icon}</span>
                <span className="font-medium" style={{ fontFamily: 'var(--font-display)' }}>{b.name}</span>
              </span>
            </TooltipTrigger>
            <TooltipContent side="top">
              <p className="m-0 text-xs font-medium">{b.name}{b.sub ? ` · ${b.sub}` : ''}</p>
              {b.description && <p className="m-0 text-[11px] text-(--text-3)">{b.description}</p>}
            </TooltipContent>
          </Tooltip>
        ))}
      </div>
    </div>
  )
}
