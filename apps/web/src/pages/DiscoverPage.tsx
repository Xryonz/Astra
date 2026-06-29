
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Compass, Search, Users } from 'lucide-react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { toast } from '@/components/ui/sonner'
import { Reveal } from '@/components/anim/Reveal'
import { ConstellationBanner } from '@/components/astra/Constellation'
import ConstellationEmpty from '@/components/astra/ConstellationEmpty'
import type { DiscoverServer } from '@astra/types'

export default function DiscoverPage() {
  const { t }       = useTranslation()
  const [q, setQ] = useState('')
  const navigate    = useNavigate()
  const queryClient = useQueryClient()

  const { data: servers = [], isLoading } = useQuery<DiscoverServer[]>({
    queryKey: ['discover', q],
    queryFn:  async () => (await api.get(`/api/discover?q=${encodeURIComponent(q)}`)).data.data,
    staleTime: 15_000,
  })

  const join = useMutation({
    mutationFn: async (serverId: string) => (await api.post(`/api/discover/${serverId}/join`)).data.data,
    onSuccess: (_d, serverId) => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      const srv = servers.find((s) => s.id === serverId)
      toast.success(t('discover.welcome', { name: srv?.name ?? t('discover.fallbackName') }))
      navigate('/app')
    },
    onError: (e: any) => {
      toast.error(e?.response?.data?.error ?? t('discover.joinError'))
    },
  })

  return (
    <div className="flex-1 min-w-0 h-full overflow-y-auto relative astra-scrollable">
      <div className="ed-vignette" />
      <div className="max-w-5xl mx-auto px-6 sm:px-10 py-12 relative">
        <Reveal>
          <div className="flex items-center gap-3 mb-1">
            <Compass className="size-6 text-(--accent)" />
            <h1 className="ed-h text-3xl m-0">{t('discover.title')}</h1>
          </div>
          <p className="text-sm text-(--text-2) m-0 mt-1 mb-6">
            {t('discover.subtitle')}
          </p>
        </Reveal>

        <Reveal delay={0.05}>
          <div className="relative max-w-md mb-8">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-(--text-3) pointer-events-none" />
            <Input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t('discover.searchPlaceholder')}
              className="pl-9"
            />
          </div>
        </Reveal>

        {isLoading ? (
          <div className="flex items-center gap-2 text-sm text-(--text-3) py-12">
            <Spinner size={14} /> {t('discover.loading')}
          </div>
        ) : servers.length === 0 ? (
          <ConstellationEmpty
            title={q ? t('discover.emptyFoundTitle') : t('discover.emptyTitle')}
            description={q ? t('discover.emptyFoundDesc') : t('discover.emptyDesc')}
            className="py-12"
          />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {servers.map((s, i) => (
              <Reveal key={s.id} delay={Math.min(i * 0.04, 0.3)}>
                <div className="border border-(--border) bg-(--overlay) overflow-hidden flex flex-col h-full">
                  <div className="relative h-24">
                    {s.bannerUrl
                      ? <img src={s.bannerUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover" />
                      : <ConstellationBanner name={s.name} stars={s.members || undefined} className="w-full h-full" />}
                    <div className="absolute inset-x-0 bottom-0 px-3 py-2 bg-gradient-to-t from-black/75 to-transparent">
                      <h3 className="text-base m-0 text-white font-normal tracking-tight truncate" style={{ fontFamily: 'var(--font-display)' }}>
                        {s.name}
                      </h3>
                    </div>
                  </div>
                  <div className="p-3 flex flex-col gap-3 flex-1">
                    <p className="text-xs text-(--text-2) m-0 line-clamp-2 min-h-8 flex-1">
                      {s.description || t('discover.noDescription')}
                    </p>
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[11px] font-mono text-(--text-3) flex items-center gap-1">
                        <Users className="size-3" /> {s.members}
                      </span>
                      <Button size="sm" onClick={() => join.mutate(s.id)} disabled={join.isPending}>
                        {t('discover.join')}
                      </Button>
                    </div>
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
