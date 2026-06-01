/**
 * ProfileHoverCard — mini-card editorial que aparece em hover de @mentions / avatares.
 *
 * Fetch lazy: só dispara request quando o card abre (delay 250ms padrão Radix).
 * Cache: useQuery com staleTime 5min — hover repetido não refetch.
 *
 * Uso:
 *   <ProfileHoverCard username="maria">
 *     <span className="...">@maria</span>
 *   </ProfileHoverCard>
 *
 *   ou
 *
 *   <ProfileHoverCard userId="abc123">
 *     <Avatar>...</Avatar>
 *   </ProfileHoverCard>
 */
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { MessageCircle } from 'lucide-react'
import { motion, type Variants } from 'motion/react'
import { api, resolveApiUrl } from '@/lib/api'
import { HoverCard, HoverCardTrigger, HoverCardContent } from '@/components/ui/hover-card'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { Spinner } from '@/components/ui/spinner'
import { Button } from '@/components/ui/button'
import { toast } from '@/components/ui/sonner'
import { cn } from '@/lib/utils'

// Animação cascata: container orquestra filhos com delay incremental.
const containerVariants: Variants = {
  hidden:  { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.05, delayChildren: 0.04 } },
}
const itemVariants: Variants = {
  hidden:  { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.32, ease: [0.16, 1, 0.3, 1] } },
}

interface ProfileMini {
  id:             string
  username:       string
  displayName:    string
  avatarUrl:      string | null
  bio:            string | null
  bannerUrl:      string | null
  bannerColor:    string | null
  isBot:          boolean
  customStatus:   string | null
  effectiveStatus: 'ONLINE' | 'IDLE' | 'DND' | 'INVISIBLE' | 'OFFLINE'
}

const PRESENCE_LABEL: Record<string, string> = {
  ONLINE: 'Online', IDLE: 'Ausente', DND: 'Ocupado', OFFLINE: 'Offline', INVISIBLE: 'Offline',
}
const PRESENCE_DOT: Record<string, string> = {
  ONLINE:    'bg-(--success)',
  IDLE:      'bg-yellow-500',
  DND:       'bg-(--danger)',
  OFFLINE:   'bg-(--text-3)',
  INVISIBLE: 'bg-(--text-3)',
}

interface Props {
  /** Use username OU userId, não os dois. */
  username?: string
  userId?:   string
  children:  React.ReactNode
  side?:     'top' | 'right' | 'bottom' | 'left'
  align?:    'start' | 'center' | 'end'
}

export function ProfileHoverCard({ username, userId, children, side = 'top', align = 'start' }: Props) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()

  // Só dispara query quando o card ABRE — economia de banda.
  const key = username ?? userId
  const { data, isLoading } = useQuery<ProfileMini>({
    queryKey: ['profile-mini', key],
    queryFn:  async () => {
      if (username) return (await api.get(`/api/profile/by-username/${username}`)).data.data
      return (await api.get(`/api/profile/${userId}`)).data.data.user
    },
    enabled:   open && !!key,
    staleTime: 5 * 60_000,
  })

  const startDM = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (!data) return
    try {
      const res = await api.post('/api/dm/open', { userId: data.id })
      const conversationId = res.data.data.conversationId as string
      navigate('/app/dm', {
        state: {
          conversationId,
          otherUser: {
            id:          data.id,
            username:    data.username,
            displayName: data.displayName,
            avatarUrl:   data.avatarUrl ?? null,
          },
        },
      })
    } catch (err: any) {
      toast.error(err?.response?.data?.error ?? 'Erro ao abrir DM')
    }
  }

  return (
    <HoverCard openDelay={350} closeDelay={120} onOpenChange={setOpen}>
      <HoverCardTrigger asChild>
        {children}
      </HoverCardTrigger>
      <HoverCardContent
        side={side}
        align={align}
        sideOffset={8}
        className="w-90 p-0 overflow-hidden rounded-xl bg-(--overlay) backdrop-blur-md z-9999 shadow-[0_24px_80px_-12px_rgba(0,0,0,0.85)] border-(--border-bright) isolate will-change-transform"
      >
        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {/* Banner — fade simples (não usa item pra não cortar com overflow) */}
          <motion.div
            variants={itemVariants}
            className="h-20 w-full bg-(--raised)"
            style={{
              background: data?.bannerUrl
                ? `url(${resolveApiUrl(data.bannerUrl)}) center / cover`
                : data?.bannerColor ?? 'var(--raised)',
            }}
          />

          <div className="px-5 pb-5 -mt-10">
            {/* Avatar + presence */}
            <motion.div variants={itemVariants} className="relative inline-block">
              <Avatar className="size-20 border-4 border-(--overlay) rounded-full">
                {data?.avatarUrl && <AvatarImage src={resolveApiUrl(data.avatarUrl)} alt={data.displayName} />}
                <AvatarFallback className="text-2xl font-(family-name:--font-display) bg-(--raised) text-(--text-2) rounded-full">
                  {data?.displayName?.slice(0, 1).toUpperCase() ?? '?'}
                </AvatarFallback>
              </Avatar>
              {data && (
                <span
                  className={cn(
                    'absolute bottom-1 right-1 size-4 rounded-full border-2 border-(--overlay)',
                    PRESENCE_DOT[data.effectiveStatus] ?? 'bg-(--text-3)',
                  )}
                  aria-hidden
                />
              )}
            </motion.div>

            {isLoading && (
              <motion.div variants={itemVariants} className="flex items-center gap-2 mt-3 text-(--text-3) text-sm">
                <Spinner size={14} /> Carregando…
              </motion.div>
            )}

            {data && (
              <>
                <motion.div variants={itemVariants} className="mt-3 flex items-baseline gap-2 flex-wrap">
                  <h3 className="ed-h text-xl m-0 truncate">
                    {data.displayName}
                  </h3>
                  {data.isBot && <Badge>Bot</Badge>}
                </motion.div>
                <motion.p variants={itemVariants} className="text-xs font-mono text-(--text-3) m-0 mt-1 truncate">
                  @{data.username} · {PRESENCE_LABEL[data.effectiveStatus]}
                </motion.p>

                {data.customStatus && (
                  <motion.p variants={itemVariants} className="text-sm italic text-(--text-2) m-0 mt-3 line-clamp-2">
                    {data.customStatus}
                  </motion.p>
                )}

                {data.bio && (
                  <motion.div variants={itemVariants}>
                    <Separator className="my-4" />
                    <p className="text-sm text-(--text-2) m-0 line-clamp-4 leading-relaxed">
                      {data.bio}
                    </p>
                  </motion.div>
                )}

                <motion.div variants={itemVariants}>
                  <Separator className="my-4" />
                  <Button onClick={startDM} variant="secondary" className="w-full gap-2 h-9 text-sm rounded-lg">
                    <MessageCircle className="size-3.5" /> Enviar DM
                  </Button>
                </motion.div>
              </>
            )}
          </div>
        </motion.div>
      </HoverCardContent>
    </HoverCard>
  )
}
