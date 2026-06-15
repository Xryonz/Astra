import { useEffect, useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Users as UsersIcon, Image as ImageIcon, Shield, Crown, Trash2, UserMinus, ChevronDown, ChevronRight, Clock, Tag, Plus, Pencil, Check, Ban, Hash, Eye, EyeOff, RefreshCw, UserPlus, Search, Link as LinkIcon, Copy, Smile, X, Loader2, Award } from 'lucide-react'
import { motion, AnimatePresence } from 'motion/react'
import { api } from '@/lib/api'
import { shareInvite } from '@/lib/native'
import { useAuthStore } from '@/store/authStore'
import { useMyPerms } from '@/hooks/useMyPerms'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import { registerBackHandler } from '@/lib/backHandler'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog'
import { useConfirm, usePrompt } from '@/hooks/useConfirm'
import { toast } from '@/components/ui/sonner'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { ConstellationBanner } from '@/components/astra/Constellation'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import { Spinner } from '@/components/ui/spinner'
import { Checkbox } from '@/components/ui/checkbox'
import type { ServerWithChannels } from '@astra/types'
import { useServerEmojis, useUploadEmoji, useDeleteEmoji } from '@/hooks/useServerEmojis'
import { resolveApiUrl } from '@/lib/api'

interface RoleSummary { id: string; name: string; color: string|null; position: number; hoist: boolean }
interface Member {
  id: string; userId: string; role: 'OWNER'|'ADMIN'|'MEMBER'; nameColor: string|null; joinedAt: string
  user: { id: string; username: string; displayName: string; avatarUrl: string|null; bio: string|null }
  roles?: RoleSummary[]
  topColor?: string | null
}
interface Role {
  id: string; serverId: string; name: string; color: string|null; position: number; permissions: string[]; hoist: boolean
}
interface ServerBadge {
  id: string; serverId: string; name: string; icon: string; color: string|null; description: string|null
  createdAt: string; grantedUserIds: string[]
}

type SectionId = 'overview' | 'channels' | 'emojis' | 'members' | 'roles' | 'badges' | 'bans'
interface NavItem { id: SectionId; label: string; icon: React.ReactNode; group: 'geral' | 'comunidade'; show: boolean }

// chaves de permissão — label/desc vêm do i18n (srv.perms.<KEY>.label/desc)
const PERM_OPTIONS = ['MANAGE_SERVER', 'MANAGE_ROLES', 'MANAGE_CHANNELS', 'KICK_MEMBERS', 'BAN_MEMBERS', 'MANAGE_MESSAGES', 'MENTION_EVERYONE'] as const

const MAX_ICON_BYTES   = 5 * 1024 * 1024
const MAX_BANNER_BYTES = 8 * 1024 * 1024

export default function ServerSettingsPage() {
  const { t }        = useTranslation()
  const { serverId } = useParams<{ serverId: string }>()
  const navigate     = useNavigate()
  const queryClient  = useQueryClient()
  const currentUser  = useAuthStore((s) => s.user)

  const [name,         setName]         = useState('')
  const [iconUrl,      setIconUrl]      = useState<string | null>(null)
  const [bannerUrl,    setBannerUrl]    = useState<string | null>(null)
  const [retentionDays, setRetentionDays] = useState<number>(0)
  const [isPublic,     setIsPublic]     = useState(false)
  const [description,  setDescription]  = useState('')
  const [error,        setError]        = useState('')
  const [kickTarget,   setKickTarget]   = useState<Member | null>(null)
  const prompt = usePrompt()

  // Navegação por seção (espelha o SettingsPage): desktop = sidebar à
  // esquerda; mobile = drill-down de cards. mobileOpen null = home de cards.
  const [section,    setSection]    = useState<SectionId>('overview')
  const [mobileOpen, setMobileOpen] = useState<SectionId | null>(null)
  const pickSection = (id: SectionId) => { setSection(id); setMobileOpen(id) }

  // Back nativo (Android): seção aberta no mobile recua pros cards em vez
  // de sair do server settings inteiro. Consistente com o SettingsPage.
  useEffect(() => {
    if (mobileOpen === null) return
    return registerBackHandler(() => { setMobileOpen(null); return true })
  }, [mobileOpen])

  // Server data
  const { data: servers = [] } = useQuery<ServerWithChannels[]>({
    queryKey: ['servers'],
    queryFn:  async () => (await api.get('/api/servers')).data.data,
  })
  const server = servers.find((s) => s.id === serverId)

  useEffect(() => {
    if (server) {
      setName(server.name)
      setIconUrl(server.iconUrl ?? null)
      setBannerUrl(server.bannerUrl ?? null)
      setRetentionDays(server.messageRetentionDays ?? 0)
      setIsPublic(server.isPublic ?? false)
      setDescription(server.description ?? '')
    }
  }, [server?.id])

  // Members
  const { data: members = [] } = useQuery<Member[]>({
    queryKey: ['members', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/members`)).data.data,
    enabled: !!serverId,
  })

  const perms = useMyPerms(serverId)
  const isOwner = perms.isOwner
  const isAdmin = perms.isAdmin || isOwner

  const updateServer = useMutation({
    mutationFn: async (patch: { name?: string; iconUrl?: string | null; bannerUrl?: string | null; messageRetentionDays?: number | null; isPublic?: boolean; description?: string | null }) =>
      (await api.patch(`/api/servers/${serverId}`, patch)).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setError('')
    },
    onError: (e: any) => setError(e.response?.data?.error ?? t('srv.toast.saveError')),
  })

  const retentionPresets: Array<{ v: number; key: string }> = [
    { v: 0,   key: 'forever' },
    { v: 1,   key: 'd1' },
    { v: 7,   key: 'd7' },
    { v: 30,  key: 'd30' },
    { v: 90,  key: 'd90' },
    { v: 365, key: 'd365' },
  ]

  const setMemberRole = useMutation({
    mutationFn: async ({ memberId, role }: { memberId: string; role: 'ADMIN'|'MEMBER' }) =>
      (await api.patch(`/api/servers/${serverId}/members/${memberId}`, { role })).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      queryClient.invalidateQueries({ queryKey: ['perms', serverId] })
    },
  })

  const kickMember = useMutation({
    mutationFn: async (memberId: string) => api.delete(`/api/servers/${serverId}/members/${memberId}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['members', serverId] }); setKickTarget(null) },
    onError:   (e: any) => setError(e.response?.data?.error ?? t('srv.toast.kickError')),
  })

  const banMember = useMutation({
    mutationFn: async (p: { userId: string; reason?: string|null }) =>
      api.post(`/api/servers/${serverId}/bans`, p),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      queryClient.invalidateQueries({ queryKey: ['bans', serverId] })
    },
    onError: (e: any) => setError(e.response?.data?.error ?? t('srv.toast.banError')),
  })

  const deleteServer = useMutation({
    mutationFn: async () => api.delete(`/api/servers/${serverId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      navigate('/app')
    },
  })

  const [showDelete, setShowDelete] = useState(false)
  const [showInviteFriends, setShowInviteFriends] = useState(false)
  const confirm = useConfirm()

  // Regenera inviteCode: invalida link antigo, atualiza cache de servers
  const regenerateInvite = useMutation({
    mutationFn: async () => (await api.post(`/api/servers/${serverId}/regenerate-invite`)).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      toast.success(t('srv.toast.inviteRegenerated'))
    },
    onError: (e: any) => toast.error(e.response?.data?.error ?? t('srv.toast.inviteRegenError')),
  })

  const handleRegenerateInvite = async () => {
    const ok = await confirm({
      title: t('srv.regen.title'),
      description: t('srv.regen.desc'),
      confirmLabel: t('srv.regen.confirm'),
      destructive: true,
    })
    if (ok) regenerateInvite.mutate()
  }

  const copyInvite = () => {
    if (!server) return
    // App nativo: share sheet do OS. Web: clipboard como antes.
    shareInvite(server.inviteCode)
      .then((mode) => { if (mode === 'copied') toast.success(t('srv.toast.linkCopied')) })
      .catch(() => toast.error(t('srv.toast.copyFailed')))
  }

  const handleBannerFile = (file: File) => {
    if (file.size > MAX_BANNER_BYTES) { setError(t('srv.overview.bannerTooLarge')); return }
    const fr = new FileReader()
    fr.onload = () => { const url = String(fr.result); setBannerUrl(url); updateServer.mutate({ bannerUrl: url }) }
    fr.readAsDataURL(file)
  }

  const handleIconFile = (file: File) => {
    if (file.size > MAX_ICON_BYTES) { setError(t('srv.overview.iconTooLarge')); return }
    const fr = new FileReader()
    fr.onload = () => { const url = String(fr.result); setIconUrl(url); updateServer.mutate({ iconUrl: url }) }
    fr.readAsDataURL(file)
  }

  if (!server) {
    return (
      <div className="flex-1 flex items-center justify-center gap-2 h-full text-sm text-(--text-3)">
        <Spinner size={14} /> {t('srv.common.loading')}
      </div>
    )
  }

  const allNav: NavItem[] = [
    { id: 'overview', label: t('srv.nav.overview'), icon: <ImageIcon className="size-3.5" />, group: 'geral',      show: true },
    { id: 'channels', label: t('srv.nav.channels'), icon: <Hash className="size-3.5" />,      group: 'geral',      show: perms.has('MANAGE_CHANNELS') },
    { id: 'emojis',   label: t('srv.nav.emojis'),   icon: <Smile className="size-3.5" />,     group: 'geral',      show: perms.has('MANAGE_CHANNELS') },
    { id: 'members',  label: t('srv.nav.members'),  icon: <UsersIcon className="size-3.5" />, group: 'comunidade', show: true },
    { id: 'roles',    label: t('srv.nav.roles'),    icon: <Tag className="size-3.5" />,       group: 'comunidade', show: perms.has('MANAGE_ROLES') },
    { id: 'badges',   label: t('srv.nav.badges'),   icon: <Award className="size-3.5" />,     group: 'comunidade', show: perms.has('MANAGE_SERVER') || isOwner },
    { id: 'bans',     label: t('srv.nav.bans'),     icon: <Ban className="size-3.5" />,       group: 'comunidade', show: perms.has('BAN_MEMBERS') },
  ]
  const navItems = allNav.filter((n) => n.show)
  const currentLabel = navItems.find((n) => n.id === section)?.label ?? ''
  const groupLabel = { geral: t('srv.groups.geral'), comunidade: t('srv.groups.comunidade') } as const

  return (
    // h-full: vive DENTRO do shell do AppPage (tab bar + notch já descontados).
    // Espelha o SettingsPage: sidebar no desktop, drill-down de cards no mobile.
    <main className="flex-1 min-w-0 flex h-full font-(family-name:--font-body) overflow-hidden">
      {/* ─── Sidebar (md+) ─── */}
      <aside className="hidden md:flex w-60 lg:w-64 shrink-0 border-r border-(--border) bg-(--raised)/30 flex-col overflow-hidden">
        <header className="h-16 px-4 flex items-center gap-2.5 border-b border-(--border) shrink-0">
          <button
            onClick={() => navigate(-1)}
            className="size-8 grid place-items-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer rounded-lg shrink-0"
            aria-label={t('srv.common.back')}
          >
            <ArrowLeft className="size-4" />
          </button>
          <div className="min-w-0">
            <p className="ed-marg m-0 leading-none">{server.isGroup ? t('srv.common.group') : t('srv.common.server')}</p>
            <h1 className="text-base m-0 font-normal tracking-tight text-foreground truncate leading-tight" style={{ fontFamily: 'var(--font-display)' }}>
              {server.name}
            </h1>
          </div>
        </header>
        <ScrollArea className="flex-1">
          <nav className="py-4">
            {(['geral', 'comunidade'] as const).map((grp) => {
              const items = navItems.filter((n) => n.group === grp)
              if (items.length === 0) return null
              return (
                <div key={grp} className="mb-5">
                  <p className="px-5 mb-2 text-[10px] uppercase tracking-wider text-(--text-3) font-mono m-0">— {groupLabel[grp]}</p>
                  <ul className="flex flex-col gap-0.5">
                    {items.map((n) => {
                      const active = section === n.id
                      return (
                        <li key={n.id}>
                          <button
                            onClick={() => setSection(n.id)}
                            className={cn(
                              'group w-full flex items-center gap-3 px-5 py-2 text-left text-sm cursor-pointer transition-colors border-l-2 rounded-r-lg',
                              active
                                ? 'bg-(--accent-dim) text-(--accent) border-(--accent)'
                                : 'text-(--text-2) hover:bg-(--raised)/60 hover:text-foreground border-transparent',
                            )}
                          >
                            <span className={active ? 'text-(--accent)' : 'text-(--text-3) group-hover:text-(--text-2)'}>{n.icon}</span>
                            <span>{n.label}{n.id === 'members' ? ` (${members.length})` : ''}</span>
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )
            })}
          </nav>
        </ScrollArea>
      </aside>

      {/* ─── Conteúdo ─── */}
      <section className="flex-1 overflow-y-auto relative">
        <div className="sticky top-0 z-10 backdrop-blur bg-(--base)/90 border-b border-(--border)">
          {/* Mobile header: home (back ao servidor) ou seção (back aos cards) */}
          <div className="md:hidden flex items-center gap-2 px-4 py-3">
            {mobileOpen === null ? (
              <>
                <button onClick={() => navigate(-1)} className="size-10 -ml-1 grid place-items-center text-(--text-2) hover:text-(--accent) transition-colors cursor-pointer shrink-0" aria-label={t('srv.common.back')}>
                  <ArrowLeft className="size-5" />
                </button>
                <div className="min-w-0">
                  <p className="ed-marg m-0 leading-none">{server.isGroup ? t('srv.common.group') : t('srv.common.server')}</p>
                  <h1 className="text-lg m-0 font-normal tracking-tight text-foreground truncate leading-tight" style={{ fontFamily: 'var(--font-display)' }}>{server.name}</h1>
                </div>
              </>
            ) : (
              <>
                <button onClick={() => setMobileOpen(null)} className="size-10 -ml-1 grid place-items-center text-(--text-2) hover:text-(--accent) transition-colors cursor-pointer shrink-0" aria-label={t('srv.common.back')}>
                  <ArrowLeft className="size-5" />
                </button>
                <h1 className="flex-1 text-lg m-0 font-normal tracking-tight text-foreground truncate" style={{ fontFamily: 'var(--font-display)' }}>{currentLabel}</h1>
              </>
            )}
          </div>
          <div className="hidden md:block max-w-3xl mx-auto px-6 sm:px-8 py-3.5">
            <h2 className="text-base m-0 font-normal text-foreground" style={{ fontFamily: 'var(--font-display)' }}>{currentLabel}</h2>
          </div>
        </div>

        {/* Mobile: home de cards agrupados */}
        {mobileOpen === null && (
          <div className="md:hidden max-w-xl mx-auto px-4 py-5 pb-safe space-y-6">
            {(['geral', 'comunidade'] as const).map((grp) => {
              const items = navItems.filter((n) => n.group === grp)
              if (items.length === 0) return null
              return (
                <div key={grp}>
                  <p className="px-1 mb-2 text-[10px] uppercase tracking-wider text-(--text-3) font-mono m-0">— {groupLabel[grp]}</p>
                  <div className="rounded-2xl border border-(--border) bg-(--raised)/30 overflow-hidden divide-y divide-(--border)">
                    {items.map((n) => (
                      <button key={n.id} onClick={() => pickSection(n.id)} className="w-full flex items-center gap-3 px-3.5 py-3.5 text-left transition-colors active:bg-(--raised)/60 cursor-pointer">
                        <span className="size-9 rounded-xl bg-(--accent-dim) text-(--accent) grid place-items-center shrink-0">{n.icon}</span>
                        <span className="flex-1 text-sm text-foreground">{n.label}{n.id === 'members' ? ` (${members.length})` : ''}</span>
                        <ChevronRight className="size-4 text-(--text-3) shrink-0" />
                      </button>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Conteúdo: mobile = seção desliza ao abrir (cards somem, sem flash da
            seção anterior); desktop = seção fixa que troca com slide. */}
        {(() => {
          const sectionContent = (
            <>

              {section === 'overview' && (
                <div className="space-y-8">
            <section className="space-y-3">
              <span className="ed-label">{t('srv.overview.icon')}</span>
              <div className="flex items-center gap-5 flex-wrap">
                <div
                  className="size-24 flex items-center justify-center border border-(--border) bg-(--raised) overflow-hidden shrink-0"
                  style={{ fontFamily: 'var(--font-display)' }}
                >
                  {iconUrl
                    ? <img src={iconUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover" />
                    : <span className="text-2xl text-(--text-2)">{server.name.slice(0,2).toUpperCase()}</span>}
                </div>
                <div className="flex flex-col gap-2">
                  <input
                    id="iconUpload"
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    className="hidden"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) handleIconFile(f) }}
                    disabled={!isAdmin}
                  />
                  <label
                    htmlFor="iconUpload"
                    className={`inline-flex items-center gap-2 px-4 h-10 border border-(--border) text-sm cursor-pointer transition-colors ${isAdmin ? 'hover:border-(--accent) hover:text-(--accent)' : 'opacity-50 cursor-not-allowed'}`}
                  >
                    <ImageIcon className="size-3.5" /> {t('srv.overview.uploadImage')}
                  </label>
                  {iconUrl && isAdmin && (
                    <button
                      onClick={() => { setIconUrl(null); updateServer.mutate({ iconUrl: null }) }}
                      className="text-xs text-(--text-3) hover:text-(--danger) text-left transition-colors cursor-pointer"
                    >{t('srv.overview.removeIcon')}</button>
                  )}
                  <p className="text-[11px] text-(--text-3) m-0">{t('srv.overview.iconHint')}</p>
                </div>
              </div>
            </section>

            <Separator className="my-5" />

            <section className="space-y-3">
              <span className="ed-label">{t('srv.overview.banner')}</span>
              <p className="text-xs text-(--text-3) m-0 max-w-md">
                {t('srv.overview.bannerDesc')}
              </p>
              <div className="flex flex-col gap-3 max-w-md">
                <div className="relative w-full h-28 border border-(--border) overflow-hidden">
                  {bannerUrl
                    ? <img src={bannerUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover" />
                    : <ConstellationBanner name={server.name} stars={members.length || undefined} className="w-full h-full" />}
                </div>
                <div className="flex flex-col gap-2">
                  <input
                    id="bannerUpload"
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    className="hidden"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) handleBannerFile(f) }}
                    disabled={!isAdmin}
                  />
                  <label
                    htmlFor="bannerUpload"
                    className={`inline-flex items-center gap-2 px-4 h-10 border border-(--border) text-sm cursor-pointer transition-colors self-start ${isAdmin ? 'hover:border-(--accent) hover:text-(--accent)' : 'opacity-50 cursor-not-allowed'}`}
                  >
                    <ImageIcon className="size-3.5" /> {t('srv.overview.uploadBanner')}
                  </label>
                  {bannerUrl && isAdmin && (
                    <button
                      onClick={() => { setBannerUrl(null); updateServer.mutate({ bannerUrl: null }) }}
                      className="text-xs text-(--text-3) hover:text-(--danger) text-left transition-colors cursor-pointer"
                    >{t('srv.overview.removeBanner')}</button>
                  )}
                  <p className="text-[11px] text-(--text-3) m-0">{t('srv.overview.bannerHint')}</p>
                </div>
              </div>
            </section>

            {!server.isGroup && (
              <>
                <Separator className="my-5" />
                <section className="space-y-3">
                  <span className="ed-label">{t('srv.overview.discovery')}</span>
                  <p className="text-xs text-(--text-3) m-0 max-w-md">
                    {t('srv.overview.discoveryDesc')}
                  </p>
                  <button
                    onClick={() => { if (!isAdmin) return; const next = !isPublic; setIsPublic(next); updateServer.mutate({ isPublic: next }) }}
                    disabled={!isAdmin}
                    className={`self-start inline-flex items-center gap-2.5 px-4 h-10 border text-sm transition-colors ${isAdmin ? 'cursor-pointer' : 'opacity-50 cursor-not-allowed'} ${isPublic ? 'border-(--accent) bg-(--accent-dim) text-(--accent)' : 'border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent)'}`}
                  >
                    <span className={`size-3.5 rounded-full border shrink-0 transition-colors ${isPublic ? 'bg-(--accent) border-(--accent)' : 'border-(--border-mid)'}`} />
                    {isPublic ? t('srv.overview.listed') : t('srv.overview.list')}
                  </button>
                  {isPublic && (
                    <div className="flex flex-col gap-2 max-w-md">
                      <Label htmlFor="srvDesc">{t('srv.overview.dirDescription')}</Label>
                      <textarea
                        id="srvDesc"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        onBlur={() => { if (isAdmin && description !== (server.description ?? '')) updateServer.mutate({ description: description.trim() || null }) }}
                        disabled={!isAdmin}
                        maxLength={200}
                        rows={2}
                        placeholder={t('srv.overview.dirPlaceholder')}
                        className="w-full px-3 py-2 border border-(--border) bg-(--raised) text-sm text-foreground resize-none focus:border-(--accent) outline-none"
                      />
                      <p className="text-[11px] text-(--text-3) m-0">{t('srv.overview.dirCounter', { n: description.length })}</p>
                    </div>
                  )}
                </section>
              </>
            )}

            <Separator className="my-5" />

            <section className="space-y-3">
              <span className="ed-label">{t('srv.overview.name')}</span>
              <div className="flex flex-col gap-2 max-w-md">
                <Label htmlFor="srvName">{t('srv.overview.nameLabel', { kind: server.isGroup ? t('srv.common.groupLower') : t('srv.common.serverLower') })}</Label>
                <Input
                  id="srvName"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  onBlur={() => { if (name.trim() && name !== server.name) updateServer.mutate({ name: name.trim() }) }}
                  disabled={!isAdmin}
                  maxLength={100}
                />
                <p className="text-[11px] text-(--text-3) m-0">{t('srv.overview.nameSavedHint')}</p>
              </div>
            </section>

            {!server.isGroup && (
              <>
                <Separator className="my-5" />
                <section className="space-y-3">
                  <div className="flex items-center gap-2">
                    <LinkIcon className="size-3.5 text-(--text-3)" />
                    <span className="ed-label">{t('srv.overview.invite')}</span>
                  </div>
                  <p className="text-sm text-(--text-2) m-0 max-w-prose">
                    {t('srv.overview.inviteDesc')}
                  </p>
                  <div className="flex items-center gap-2 max-w-xl flex-wrap">
                    <Input
                      readOnly
                      value={`${window.location.origin}/invite/${server.inviteCode}`}
                      className="font-mono text-[12px] flex-1 min-w-64"
                      onFocus={(e) => e.currentTarget.select()}
                    />
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button variant="secondary" onClick={copyInvite} className="gap-1.5">
                          <Copy className="size-3.5" /> {t('srv.overview.copy')}
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>{t('srv.overview.copyTooltip')}</TooltipContent>
                    </Tooltip>
                    {isAdmin && (
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Button
                            variant="outline"
                            onClick={handleRegenerateInvite}
                            disabled={regenerateInvite.isPending}
                            className="gap-1.5"
                          >
                            <RefreshCw className={`size-3.5 ${regenerateInvite.isPending ? 'animate-spin' : ''}`} />
                            {regenerateInvite.isPending ? t('srv.overview.regenerating') : t('srv.overview.regenerate')}
                          </Button>
                        </TooltipTrigger>
                        <TooltipContent>{t('srv.overview.regenTooltip')}</TooltipContent>
                      </Tooltip>
                    )}
                  </div>
                  {!isAdmin && (
                    <p className="text-[11px] text-(--text-3) italic m-0">
                      {t('srv.overview.regenAdminOnly')}
                    </p>
                  )}
                </section>
              </>
            )}

            <Separator className="my-5" />

            <section className="space-y-3">
              <div className="flex items-center gap-2">
                <Clock className="size-3.5 text-(--text-3)" />
                <span className="ed-label">{t('srv.overview.retention')}</span>
              </div>
              <p className="text-sm text-(--text-2) m-0 max-w-prose">
                {t('srv.overview.retentionDesc')}
              </p>
              {!isAdmin && (
                <p className="text-xs text-(--text-3) italic">{t('srv.overview.retentionAdminOnly')}</p>
              )}
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-w-2xl">
                {retentionPresets.map((p) => {
                  const active = retentionDays === p.v
                  return (
                    <button
                      key={p.v}
                      disabled={!isAdmin}
                      onClick={() => { setRetentionDays(p.v); updateServer.mutate({ messageRetentionDays: p.v }) }}
                      className={`text-left p-3 border transition-colors cursor-pointer ${
                        active
                          ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                          : 'border-(--border) hover:border-(--accent) hover:text-(--accent)'
                      } ${!isAdmin ? 'opacity-60 cursor-not-allowed' : ''}`}
                    >
                      <p className="m-0 text-sm font-medium" style={{ fontFamily: 'var(--font-display)' }}>{t(`srv.retention.${p.key}.label`)}</p>
                      <p className="m-0 mt-1 text-[11px] text-(--text-3)">{t(`srv.retention.${p.key}.hint`)}</p>
                    </button>
                  )
                })}
              </div>
              {retentionDays > 0 && (
                <p className="ed-marg">
                  {t('srv.overview.retentionWorker', { days: retentionDays })}
                </p>
              )}
            </section>

            {error && <p className="text-xs text-(--danger)">{error}</p>}

            {/* ── Zona de perigo (só dono) ─────────────────── */}
            {isOwner && (
              <>
                <Separator className="my-5" />
                <section className="space-y-3">
                  <div className="flex items-center gap-2">
                    <Trash2 className="size-3.5 text-(--danger)" />
                    <span className="ed-label text-(--danger)!">{t('srv.overview.dangerZone')}</span>
                  </div>
                  <div className="border border-(--danger)/40 bg-(--danger)/5 p-5">
                    <h3 className="text-base m-0 mb-1 text-(--danger)" style={{ fontFamily: 'var(--font-display)' }}>
                      {t('srv.overview.deleteTitle', { kind: server.isGroup ? t('srv.common.groupLower') : t('srv.common.serverLower') })}
                    </h3>
                    <p className="text-sm text-(--text-2) m-0 mb-3">
                      {t('srv.overview.deleteDesc')}
                    </p>
                    <Button variant="destructive" onClick={() => setShowDelete(true)}>
                      <Trash2 className="size-3.5 mr-2" /> {t('srv.overview.deleteBtn')}
                    </Button>
                  </div>
                </section>
              </>
            )}
                </div>
              )}

              {section === 'members' && (
                <div className="space-y-2">
            <div className="flex items-center gap-2 mb-3">
              <span className="ed-label">{t('srv.members.title')}</span>
              <div className="flex-1 h-px bg-(--border)" />
              <span className="ed-marg">{members.length}</span>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setShowInviteFriends(true)}
                className="gap-1.5 h-7"
              >
                <UserPlus className="size-3.5" /> {t('srv.members.invite')}
              </Button>
            </div>
            <div className="flex flex-col gap-1.5" role="list">
              {members.map((m, i) => (
                <motion.div
                  key={m.id}
                  role="listitem"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.3, delay: Math.min(i * 0.025, 0.3), ease: [0.16, 1, 0.3, 1] }}
                >
                  <MemberRow
                    member={m}
                    serverId={serverId!}
                    currentUserId={currentUser?.id}
                    isOwnerSelf={isOwner}
                    isAdminSelf={isAdmin}
                    canBan={perms.has('BAN_MEMBERS')}
                    onSetRole={(role) => setMemberRole.mutate({ memberId: m.id, role })}
                    onKick={() => setKickTarget(m)}
                    onBan={async () => {
                      const reason = await prompt({
                        title: t('srv.members.banTitle', { name: m.user.displayName }),
                        description: t('srv.members.banDesc'),
                        label: t('srv.members.banReasonLabel'),
                        placeholder: t('srv.members.banReasonPlaceholder'),
                        confirmLabel: t('srv.members.ban'),
                      })
                      if (reason === null) return
                      banMember.mutate({ userId: m.userId, reason: reason || null }, {
                        onSuccess: () => toast.success(t('srv.members.banned', { name: m.user.displayName })),
                      })
                    }}
                  />
                </motion.div>
              ))}
            </div>
                </div>
              )}

              {section === 'roles'    && <RolesSection serverId={serverId!} />}
              {section === 'channels' && <ChannelsVisibilitySection serverId={serverId!} channels={server.channels ?? []} />}
              {section === 'emojis'   && <EmojisSection serverId={serverId!} />}
              {section === 'badges'   && <BadgesSection serverId={serverId!} members={members} />}
              {section === 'bans'     && <BansSection serverId={serverId!} />}
            </>
          )
          return (
            <>
              {/* Mobile: seção entra deslizando quando aberta; cards (acima)
                  somem ao abrir, então não há flash da seção anterior. */}
              <div className="md:hidden">
                <AnimatePresence mode="wait">
                  {mobileOpen !== null && (
                    <motion.div
                      key={mobileOpen}
                      initial={{ opacity: 0, x: 26 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{    opacity: 0, x: 26 }}
                      transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
                      className="max-w-3xl mx-auto px-4 sm:px-8 py-6 pb-safe"
                    >
                      {sectionContent}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
              {/* Desktop: seção fixa, troca com slide horizontal. */}
              <div className="hidden md:block max-w-3xl mx-auto px-8 py-10 pb-safe">
                <AnimatePresence mode="wait">
                  <motion.div
                    key={section}
                    initial={{ opacity: 0, x: 26 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{    opacity: 0, x: -18 }}
                    transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
                  >
                    {sectionContent}
                  </motion.div>
                </AnimatePresence>
              </div>
            </>
          )
        })()}
      </section>

      {/* Confirm kick */}
      <Dialog open={!!kickTarget} onOpenChange={(o: boolean) => !o && setKickTarget(null)}>
        <DialogContent className="max-w-95!">
          <DialogHeader>
            <DialogTitle>{t('srv.members.kickTitle', { name: kickTarget?.user.displayName })}</DialogTitle>
            <DialogDescription>{t('srv.members.kickDesc')}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setKickTarget(null)}>{t('srv.common.cancel')}</Button>
            <Button variant="destructive" onClick={() => kickTarget && kickMember.mutate(kickTarget.id)} disabled={kickMember.isPending}>
              {kickMember.isPending ? t('srv.members.kicking') : t('srv.members.kick')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Convite por amigo — dialog com lista filtrada */}
      <InviteFriendsDialog
        open={showInviteFriends}
        onClose={() => setShowInviteFriends(false)}
        serverId={serverId!}
        serverName={server.name}
        currentMemberUserIds={new Set(members.map((m) => m.userId))}
      />

      {/* Confirm delete server */}
      <Dialog open={showDelete} onOpenChange={setShowDelete}>
        <DialogContent className="max-w-95! text-center">
          <div className="flex justify-center mb-1">
            <div className="size-12 rounded-full bg-(--danger)/10 flex items-center justify-center">
              <Trash2 className="size-6 text-(--danger)" />
            </div>
          </div>
          <DialogHeader className="text-center! items-center!">
            <DialogTitle>{t('srv.deleteServer.title', { name: server.name })}</DialogTitle>
            <DialogDescription>{t('srv.deleteServer.desc')}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="sm:justify-center">
            <Button variant="secondary" className="flex-1" onClick={() => setShowDelete(false)}>{t('srv.common.cancel')}</Button>
            <Button variant="destructive" className="flex-1" onClick={() => deleteServer.mutate()} disabled={deleteServer.isPending}>
              {deleteServer.isPending ? t('srv.deleteServer.deleting') : t('srv.deleteServer.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}

function MemberRow({ member, serverId, currentUserId, isOwnerSelf, isAdminSelf, canBan, onSetRole, onKick, onBan }: {
  member: Member
  serverId: string
  currentUserId?: string
  isOwnerSelf: boolean
  isAdminSelf: boolean
  canBan: boolean
  onSetRole: (role: 'ADMIN'|'MEMBER') => void
  onKick: () => void
  onBan: () => void
}) {
  const { t } = useTranslation()
  const [rolesOpen, setRolesOpen] = useState(false)
  const isSelf  = member.userId === currentUserId
  const isOwner = member.role === 'OWNER'
  const canChangeRole = isOwnerSelf && !isOwner && !isSelf
  const canKick = isAdminSelf && !isOwner && !isSelf && !(member.role === 'ADMIN' && !isOwnerSelf)

  return (
    <div className="flex items-center gap-3 px-3 py-2 border border-transparent hover:border-(--border) hover:bg-(--raised)/40 transition-colors">
      <Avatar className="size-9 shrink-0">
        {member.user.avatarUrl && <AvatarImage src={member.user.avatarUrl} referrerPolicy="no-referrer" />}
        <AvatarFallback className="text-xs">{member.user.displayName.slice(0,1).toUpperCase()}</AvatarFallback>
      </Avatar>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium text-foreground truncate" style={{ fontFamily: 'var(--font-display)' }}>
            {member.user.displayName}
          </span>
          {isOwner && (
            <Badge variant="default"><Crown className="size-2.5" /> {t('srv.members.owner')}</Badge>
          )}
          {member.role === 'ADMIN' && (
            <Badge variant="secondary"><Shield className="size-2.5" /> {t('srv.members.admin')}</Badge>
          )}
          {member.roles?.map((r) => (
            <span
              key={r.id}
              className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider px-1.5 py-0.5 border"
              style={{
                color:        r.color ?? 'var(--text-2)',
                borderColor:  (r.color ?? 'var(--border)') + '66',
                background:   (r.color ?? 'var(--raised)') + '15',
              }}
            >
              {r.name}
            </span>
          ))}
        </div>
        <p className="text-[11px] font-mono text-(--text-3) m-0 truncate">@{member.user.username}</p>
      </div>

      {/* OWNER row: nunca tem ações disponíveis — esconde o trigger pra não mostrar dropdown vazio */}
      {!isOwner && (canChangeRole || canKick || isOwnerSelf) && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="size-8 flex items-center justify-center border border-(--border) text-(--text-3) hover:border-(--accent) hover:text-(--accent) transition-colors cursor-pointer">
              <ChevronDown className="size-3.5" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {isOwnerSelf && !isOwner && (
              <DropdownMenuItem onSelect={(e) => { e.preventDefault(); setRolesOpen((v) => !v) }}>
                <Tag className="size-3.5" /> {t('srv.members.editRoles')}
              </DropdownMenuItem>
            )}
            {canChangeRole && member.role !== 'ADMIN' && (
              <DropdownMenuItem onSelect={() => onSetRole('ADMIN')}>
                <Shield className="size-3.5" /> {t('srv.members.promote')}
              </DropdownMenuItem>
            )}
            {canChangeRole && member.role === 'ADMIN' && (
              <DropdownMenuItem onSelect={() => onSetRole('MEMBER')}>
                <Shield className="size-3.5" /> {t('srv.members.demote')}
              </DropdownMenuItem>
            )}
            {canKick && (
              <DropdownMenuItem destructive onSelect={onKick}>
                <UserMinus className="size-3.5" /> {t('srv.members.kickFromServer')}
              </DropdownMenuItem>
            )}
            {canBan && !isOwner && !isSelf && (
              <DropdownMenuItem destructive onSelect={onBan}>
                <Ban className="size-3.5" /> {t('srv.members.ban')}
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      )}

      {rolesOpen && (
        <MemberRolesPopover
          serverId={serverId}
          memberId={member.id}
          currentRoleIds={(member.roles ?? []).map((r) => r.id)}
          onClose={() => setRolesOpen(false)}
        />
      )}
    </div>
  )
}

function RolesSection({ serverId }: { serverId: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const { data: roles = [], isLoading } = useQuery<Role[]>({
    queryKey: ['roles', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/roles`)).data.data,
  })

  const [editing, setEditing] = useState<Role | 'new' | null>(null)

  const create = useMutation({
    mutationFn: async (body: Partial<Role>) => (await api.post(`/api/servers/${serverId}/roles`, body)).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles', serverId] })
      queryClient.invalidateQueries({ queryKey: ['perms', serverId] })
      setEditing(null)
    },
  })
  const update = useMutation({
    mutationFn: async ({ id, ...body }: Partial<Role> & { id: string }) =>
      (await api.patch(`/api/servers/${serverId}/roles/${id}`, body)).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles', serverId] })
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      queryClient.invalidateQueries({ queryKey: ['perms', serverId] })
      setEditing(null)
    },
  })
  const remove = useMutation({
    mutationFn: async (id: string) => api.delete(`/api/servers/${serverId}/roles/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles', serverId] })
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      queryClient.invalidateQueries({ queryKey: ['perms', serverId] })
    },
  })

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 mb-2">
        <span className="ed-label">{t('srv.roles.title')}</span>
        <div className="flex-1 h-px bg-(--border)" />
        <Button size="sm" onClick={() => setEditing('new')} className="gap-2">
          <Plus className="size-3.5" /> {t('srv.roles.new')}
        </Button>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sm text-(--text-3)"><Spinner size={12} /> {t('srv.common.loading')}</div>
      )}

      {!isLoading && roles.length === 0 && (
        <p className="text-sm text-(--text-3) italic">{t('srv.roles.empty')}</p>
      )}

      <ul className="flex flex-col gap-1">
        {roles.map((r) => (
          <li
            key={r.id}
            className="flex items-center gap-3 px-3 py-2 border border-(--border) hover:bg-(--raised)/40 transition-colors"
          >
            <span
              className="size-4 rounded-full border shrink-0"
              style={{ background: r.color ?? 'transparent', borderColor: r.color ?? 'var(--border)' }}
            />
            <span className="flex-1 text-sm" style={{ fontFamily: 'var(--font-display)', color: r.color ?? undefined }}>
              {r.name}
            </span>
            <span className="text-[10px] font-mono text-(--text-3)">
              {r.permissions.length > 0 ? t('srv.roles.permCount', { n: r.permissions.length }) : '—'}
            </span>
            <button
              onClick={() => setEditing(r)}
              className="size-7 flex items-center justify-center border border-(--border) text-(--text-3) hover:border-(--accent) hover:text-(--accent) transition-colors cursor-pointer"
              title={t('srv.roles.edit')}
            >
              <Pencil className="size-3" />
            </button>
            <button
              onClick={async () => {
                const ok = await confirm({
                  title: t('srv.roles.deleteTitle', { name: r.name }),
                  description: t('srv.roles.deleteDesc'),
                  confirmLabel: t('srv.roles.deleteConfirm'),
                  destructive: true,
                })
                if (ok) {
                  remove.mutate(r.id, {
                    onSuccess: () => toast.success(t('srv.roles.deleted', { name: r.name })),
                  })
                }
              }}
              className="size-7 flex items-center justify-center border border-(--border) text-(--text-3) hover:border-(--danger) hover:text-(--danger) transition-colors cursor-pointer"
              title={t('srv.common.delete')}
            >
              <Trash2 className="size-3" />
            </button>
          </li>
        ))}
      </ul>

      {editing && (
        <RoleEditor
          role={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSave={(body) => {
            if (editing === 'new') create.mutate(body)
            else update.mutate({ ...body, id: editing.id })
          }}
          saving={create.isPending || update.isPending}
        />
      )}
    </div>
  )
}

function RoleEditor({ role, onClose, onSave, saving }: {
  role: Role | null
  onClose: () => void
  onSave: (body: Partial<Role>) => void
  saving: boolean
}) {
  const { t } = useTranslation()
  const [name, setName]   = useState(role?.name ?? '')
  const [color, setColor] = useState<string>(role?.color ?? '#c9a96e')
  const [hoist, setHoist] = useState<boolean>(role?.hoist ?? false)
  const [hasColor, setHasColor] = useState<boolean>(role?.color != null)
  const [perms, setPerms] = useState<string[]>(role?.permissions ?? [])

  const togglePerm = (key: string) =>
    setPerms((p) => p.includes(key) ? p.filter((x) => x !== key) : [...p, key])

  return (
    <Dialog open onOpenChange={(o: boolean) => !o && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{role ? t('srv.roles.editTitle', { name: role.name }) : t('srv.roles.newTitle')}</DialogTitle>
          <DialogDescription>{t('srv.roles.editDesc')}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="roleName">{t('srv.roles.name')}</Label>
            <Input id="roleName" value={name} onChange={(e) => setName(e.target.value)} maxLength={50} autoFocus />
          </div>

          <div className="flex flex-col gap-2">
            <Label className="flex items-center gap-2 cursor-pointer">
              <Checkbox checked={hasColor} onCheckedChange={(v) => setHasColor(!!v)} />
              {t('srv.roles.customColor')}
            </Label>
            {hasColor && (
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  className="size-10 cursor-pointer bg-transparent border border-(--border-mid) rounded-lg"
                />
                <Input value={color} onChange={(e) => setColor(e.target.value)} placeholder="#RRGGBB" className="w-32 font-mono" />
                <span
                  className="px-3 py-1.5 border rounded-lg text-sm flex-1"
                  style={{ color, borderColor: color, background: color + '15', fontFamily: 'var(--font-display)' }}
                >
                  {name || t('srv.roles.rolePreview')}
                </span>
              </div>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <Label className="flex items-center gap-2 cursor-pointer">
              <Checkbox checked={hoist} onCheckedChange={(v) => setHoist(!!v)} />
              {t('srv.roles.hoist')}
            </Label>
          </div>

          <div className="flex flex-col gap-2">
            <Label>{t('srv.roles.perms')}</Label>
            <ul className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
              {PERM_OPTIONS.map((key) => {
                const active = perms.includes(key)
                return (
                  <li key={key}>
                    <button
                      type="button"
                      onClick={() => togglePerm(key)}
                      className={`w-full text-left p-2 border transition-colors cursor-pointer ${
                        active
                          ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                          : 'border-(--border) hover:border-(--accent) hover:text-(--accent)'
                      }`}
                    >
                      <p className="text-xs m-0 font-medium" style={{ fontFamily: 'var(--font-display)' }}>{t(`srv.perms.${key}.label`)}</p>
                      <p className="text-[10px] m-0 text-(--text-3) mt-0.5">{t(`srv.perms.${key}.desc`)}</p>
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        </div>

        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>{t('srv.common.cancel')}</Button>
          <Button
            onClick={() => onSave({ name: name.trim(), color: hasColor ? color : null, hoist, permissions: perms })}
            disabled={!name.trim() || saving}
          >
            {saving ? t('srv.common.saving') : (role ? t('srv.common.save') : t('srv.common.create'))}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function MemberRolesPopover({ serverId, memberId, currentRoleIds, onClose }: {
  serverId: string
  memberId: string
  currentRoleIds: string[]
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: roles = [] } = useQuery<Role[]>({
    queryKey: ['roles', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/roles`)).data.data,
  })

  const assign = useMutation({
    mutationFn: async (roleId: string) => api.post(`/api/servers/${serverId}/members/${memberId}/roles/${roleId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      queryClient.invalidateQueries({ queryKey: ['perms', serverId] })
    },
  })
  const unassign = useMutation({
    mutationFn: async (roleId: string) => api.delete(`/api/servers/${serverId}/members/${memberId}/roles/${roleId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      queryClient.invalidateQueries({ queryKey: ['perms', serverId] })
    },
  })

  const toggle = (roleId: string) => {
    if (currentRoleIds.includes(roleId)) unassign.mutate(roleId)
    else assign.mutate(roleId)
  }

  return (
    <Dialog open onOpenChange={(o: boolean) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('srv.roles.memberRolesTitle')}</DialogTitle>
          <DialogDescription>{t('srv.roles.memberRolesDesc')}</DialogDescription>
        </DialogHeader>
        <ul className="flex flex-col gap-1 max-h-80 overflow-y-auto">
          {roles.length === 0 && <p className="text-sm text-(--text-3) italic">{t('srv.roles.noRolesYet')}</p>}
          {roles.map((r) => {
            const active = currentRoleIds.includes(r.id)
            return (
              <li key={r.id}>
                <button
                  onClick={() => toggle(r.id)}
                  className="w-full flex items-center gap-2.5 px-3 py-2 border border-transparent hover:border-(--border) hover:bg-(--raised)/40 transition-colors cursor-pointer"
                >
                  <span
                    className="size-3 rounded-full border shrink-0"
                    style={{ background: r.color ?? 'transparent', borderColor: r.color ?? 'var(--border)' }}
                  />
                  <span className="flex-1 text-left text-sm">{r.name}</span>
                  {active && <Check className="size-3.5 text-(--accent)" />}
                </button>
              </li>
            )
          })}
        </ul>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>{t('srv.common.close')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ─── EmojisSection ────────────────────────────────────────────
function EmojisSection({ serverId }: { serverId: string }) {
  const { t } = useTranslation()
  const { data: emojis = [], isLoading } = useServerEmojis(serverId)
  const upload  = useUploadEmoji(serverId)
  const remove  = useDeleteEmoji(serverId)
  const [name, setName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [err,  setErr]  = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const submit = async () => {
    setErr(null)
    if (!file) { setErr(t('srv.emojis.selectFile')); return }
    if (!/^[a-z0-9_]{2,32}$/i.test(name)) { setErr(t('srv.emojis.nameRule')); return }
    try {
      await upload.mutateAsync({ file, name })
      setName(''); setFile(null)
      if (inputRef.current) inputRef.current.value = ''
    } catch (e: any) {
      setErr(e?.response?.data?.error ?? t('srv.emojis.uploadFail'))
    }
  }

  return (
    <section>
      <h3 className="text-sm font-medium text-foreground mb-1" style={{ fontFamily: 'var(--font-display)' }}>
        {t('srv.emojis.title')}
      </h3>
      <p className="text-marg text-(--text-3) m-0 mb-4">
        {t('srv.emojis.desc')}<code>:nome:</code>.
      </p>

      <div className="border border-(--border) bg-(--raised)/30 p-4 mb-4">
        <p className="text-xs text-(--text-2) mb-3">{t('srv.emojis.add')}</p>
        <div className="flex flex-col sm:flex-row gap-2 items-stretch sm:items-end">
          <div className="flex-1 flex flex-col gap-1">
            <Label htmlFor="emoji-name" className="text-[10px] uppercase tracking-wider text-(--text-3)">{t('srv.emojis.name')}</Label>
            <Input
              id="emoji-name"
              value={name}
              onChange={(e) => setName(e.target.value.replace(/[^a-z0-9_]/gi, '').toLowerCase())}
              placeholder="party_parrot"
              maxLength={32}
            />
          </div>
          <div className="flex-1 flex flex-col gap-1">
            <Label htmlFor="emoji-file" className="text-[10px] uppercase tracking-wider text-(--text-3)">{t('srv.emojis.file')}</Label>
            <input
              ref={inputRef}
              id="emoji-file"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/avif,image/gif"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="text-xs text-(--text-2)"
            />
          </div>
          <Button onClick={submit} disabled={upload.isPending || !file || !name} className="gap-2">
            {upload.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <Plus className="size-3.5" />}
            {t('srv.emojis.addBtn')}
          </Button>
        </div>
        {err && <p className="text-xs text-(--danger) mt-2 m-0">{err}</p>}
        <p className="text-[10px] text-(--text-3) mt-2 m-0">{t('srv.emojis.used', { n: emojis.length })}</p>
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 text-sm text-(--text-3)">
          <Loader2 className="size-3.5 animate-spin" /> {t('srv.common.loading')}
        </div>
      ) : emojis.length === 0 ? (
        <p className="text-sm text-(--text-3) italic m-0">{t('srv.emojis.empty')}</p>
      ) : (
        <ul className="grid grid-cols-[repeat(auto-fill,minmax(110px,1fr))] gap-2">
          {emojis.map((e) => (
            <li key={e.id} className="border border-(--border) bg-(--raised)/30 p-3 flex flex-col items-center gap-2 group relative">
              <img
                src={resolveApiUrl(e.url)}
                alt={`:${e.name}:`}
                width={48}
                height={48}
                className="size-12 object-contain"
              />
              <p className="text-xs text-(--text-2) m-0 truncate w-full text-center">:{e.name}:</p>
              <button
                onClick={() => {
                  if (confirm(t('srv.emojis.deleteConfirm', { name: e.name }))) remove.mutate(e.id)
                }}
                className="absolute top-1 right-1 size-6 grid place-items-center text-(--text-3) hover:text-(--danger) opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                aria-label={t('srv.common.delete')}
              >
                <X className="size-3.5" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// ─── BansSection ──────────────────────────────────────────────
interface BanRow {
  id: string; userId: string; bannedById: string; reason: string|null; createdAt: string
  user: { id: string; username: string; displayName: string; avatarUrl: string|null }
}
function BansSection({ serverId }: { serverId: string }) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const { data: bans = [], isLoading } = useQuery<BanRow[]>({
    queryKey: ['bans', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/bans`)).data.data,
  })
  const unban = useMutation({
    mutationFn: async (userId: string) => (await api.delete(`/api/servers/${serverId}/bans/${userId}`)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bans', serverId] }),
  })

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 mb-2">
        <span className="ed-label">{t('srv.bans.title')}</span>
        <div className="flex-1 h-px bg-(--border)" />
        <span className="ed-marg">{bans.length}</span>
      </div>
      {isLoading && (
        <div className="flex items-center gap-2 text-sm text-(--text-3)"><Spinner size={12} /> {t('srv.common.loading')}</div>
      )}
      {!isLoading && bans.length === 0 && (
        <p className="text-sm text-(--text-3) italic">{t('srv.bans.empty')}</p>
      )}
      <ul className="flex flex-col gap-1.5">
        {bans.map((b) => (
          <li key={b.id} className="flex items-center gap-3 px-3 py-2 border border-(--border) bg-(--raised)/30">
            <Avatar className="size-8">
              <AvatarImage src={b.user.avatarUrl ?? undefined} />
              <AvatarFallback>{b.user.displayName.slice(0,2).toUpperCase()}</AvatarFallback>
            </Avatar>
            <div className="flex-1 min-w-0">
              <p className="m-0 text-sm" style={{ fontFamily: 'var(--font-display)' }}>{b.user.displayName}</p>
              <p className="m-0 text-[11px] text-(--text-3) truncate">
                @{b.user.username}{b.reason ? ` · ${b.reason}` : ''}
              </p>
            </div>
            <Button variant="secondary" size="sm" onClick={() => unban.mutate(b.userId)} disabled={unban.isPending}>
              {t('srv.bans.unban')}
            </Button>
          </li>
        ))}
      </ul>
    </div>
  )
}

// ─── ChannelsVisibilitySection ───────────────────────────────
function ChannelsVisibilitySection({ serverId, channels }: { serverId: string; channels: Array<{ id: string; name: string; type?: string }> }) {
  const { t } = useTranslation()
  const [selectedId, setSelectedId]   = useState<string | null>(channels[0]?.id ?? null)
  const [createOpen,  setCreateOpen]  = useState(false)
  const [newName,     setNewName]     = useState('')
  const [newType,     setNewType]     = useState<'TEXT' | 'VOICE'>('TEXT')
  const [createErr,   setCreateErr]   = useState('')
  const qc = useQueryClient()
  const confirm = useConfirm()

  const { data: roles = [] } = useQuery<Array<{ id: string; name: string; color: string | null }>>({
    queryKey: ['roles', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/roles`)).data.data,
  })

  const { data: vis, isLoading } = useQuery<{ isPrivate: boolean; roleIds: string[] }>({
    queryKey: ['channel-vis', selectedId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/channels/${selectedId}/visibility`)).data.data,
    enabled:  !!selectedId,
  })

  const mut = useMutation({
    mutationFn: async (patch: { isPrivate: boolean; roleIds: string[] }) => {
      await api.patch(`/api/servers/${serverId}/channels/${selectedId}/visibility`, patch)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['channel-vis', selectedId] })
      qc.invalidateQueries({ queryKey: ['servers'] })
    },
  })

  const createChannel = useMutation({
    mutationFn: async ({ name, type }: { name: string; type: 'TEXT' | 'VOICE' }) =>
      (await api.post(`/api/servers/${serverId}/channels`, { name, type })).data.data,
    onSuccess: (ch: { id: string }) => {
      qc.invalidateQueries({ queryKey: ['servers'] })
      setSelectedId(ch.id)
      setCreateOpen(false); setNewName(''); setNewType('TEXT'); setCreateErr('')
    },
    onError: (e: any) => setCreateErr(e?.response?.data?.error ?? t('srv.channels.createError')),
  })

  const deleteChannel = useMutation({
    mutationFn: async (channelId: string) => api.delete(`/api/servers/${serverId}/channels/${channelId}`),
    onSuccess: (_d, channelId) => {
      qc.invalidateQueries({ queryKey: ['servers'] })
      if (selectedId === channelId) setSelectedId(null)
    },
  })

  const isPrivate      = vis?.isPrivate ?? false
  const allowedRoleIds = vis?.roleIds ?? []

  const toggleRole = (roleId: string) => {
    const next = allowedRoleIds.includes(roleId)
      ? allowedRoleIds.filter((id) => id !== roleId)
      : [...allowedRoleIds, roleId]
    mut.mutate({ isPrivate: true, roleIds: next })
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 mb-2">
        <span className="ed-label">{t('srv.channels.title')}</span>
        <div className="flex-1 h-px bg-(--border)" />
        <Button size="sm" onClick={() => { setCreateOpen(true); setCreateErr('') }} className="gap-2">
          <Plus className="size-3.5" /> {t('srv.channels.new')}
        </Button>
      </div>

      <div className="grid sm:grid-cols-[200px_1fr] gap-5">
      <ul className="border border-(--border) max-h-100 overflow-y-auto">
        {channels.length === 0 && (<li className="p-4 text-xs text-(--text-3) italic">{t('srv.channels.empty')}</li>)}
        {channels.map((c) => {
          const isVoice = c.type === 'VOICE'
          const active  = c.id === selectedId
          return (
            <li key={c.id} className="group flex items-center">
              <button
                onClick={() => setSelectedId(c.id)}
                className={`flex-1 px-3 py-2 text-left text-sm flex items-center gap-2 transition-colors ${
                  active ? 'bg-(--accent)/10 text-(--accent)' : 'text-(--text-2) hover:bg-(--raised)/40'
                }`}
              >
                {isVoice
                  ? <span className="text-[10px] font-mono uppercase shrink-0 text-(--text-3)">{t('srv.channels.voice')}</span>
                  : <Hash className="size-3 shrink-0" />}
                <span className="truncate">{c.name}</span>
              </button>
              <button
                onClick={async () => {
                  const ok = await confirm({
                    title: t('srv.channels.deleteTitle', { name: c.name }),
                    description: t('srv.channels.deleteDesc'),
                    confirmLabel: t('srv.channels.deleteConfirm'),
                    destructive: true,
                  })
                  if (ok) {
                    deleteChannel.mutate(c.id, {
                      onSuccess: () => toast.success(t('srv.channels.deleted', { name: c.name })),
                    })
                  }
                }}
                className="size-8 grid place-items-center text-(--text-3) hover:text-(--danger) opacity-0 group-hover:opacity-100 transition-opacity"
                title={t('srv.channels.deleteChannel')}
              >
                <Trash2 className="size-3" />
              </button>
            </li>
          )
        })}
      </ul>

      <div className="space-y-4">
        {!selectedId ? (
          <p className="text-sm text-(--text-3)">{t('srv.channels.selectToEdit')}</p>
        ) : isLoading ? (
          <div className="flex items-center gap-2 text-sm text-(--text-3)"><Spinner size={12} /> {t('srv.common.loading')}</div>
        ) : (
          <>
            <header>
              <h3 className="text-base m-0 mb-1 font-(family-name:--font-display)">{t('srv.channels.visibility')}</h3>
              <p className="text-xs text-(--text-3) m-0">
                {t('srv.channels.visibilityDesc')}
              </p>
            </header>

            <div className="flex gap-2 flex-wrap">
              <button
                onClick={() => mut.mutate({ isPrivate: false, roleIds: [] })}
                className={`flex items-center gap-2 px-3 h-9 border text-sm transition-colors ${
                  !isPrivate ? 'border-(--accent) bg-(--accent-dim) text-(--accent)' : 'border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent)'
                }`}
              >
                <Eye className="size-3.5" /> {t('srv.channels.public')}
              </button>
              <button
                onClick={() => mut.mutate({ isPrivate: true, roleIds: allowedRoleIds })}
                className={`flex items-center gap-2 px-3 h-9 border text-sm transition-colors ${
                  isPrivate ? 'border-(--accent) bg-(--accent-dim) text-(--accent)' : 'border-(--border) text-(--text-2) hover:border-(--accent) hover:text-(--accent)'
                }`}
              >
                <EyeOff className="size-3.5" /> {t('srv.channels.private')}
              </button>
            </div>

            {isPrivate && (
              <section className="space-y-2">
                <p className="text-xs text-(--text-3) m-0">{t('srv.channels.rolesCanSee')}</p>
                {roles.length === 0 && (
                  <p className="text-xs text-(--text-3) italic">{t('srv.channels.noRoles')}</p>
                )}
                <div className="flex gap-2 flex-wrap">
                  {roles.map((r) => {
                    const on = allowedRoleIds.includes(r.id)
                    return (
                      <button
                        key={r.id}
                        onClick={() => toggleRole(r.id)}
                        className={`px-3 h-8 border text-xs transition-colors flex items-center gap-2 ${
                          on ? 'border-(--accent) bg-(--accent)/10 text-foreground' : 'border-(--border) text-(--text-2) hover:border-(--accent)'
                        }`}
                      >
                        {r.color && <span className="size-2 rounded-full" style={{ background: r.color }} />}
                        {r.name}
                        {on && <Check className="size-3" />}
                      </button>
                    )
                  })}
                </div>
              </section>
            )}
          </>
        )}
      </div>
      </div>

      {/* Dialog criar canal */}
      <Dialog open={createOpen} onOpenChange={(o: boolean) => { if (!o) { setCreateOpen(false); setNewName(''); setCreateErr('') } }}>
        <DialogContent className="max-w-95!">
          <DialogHeader>
            <DialogTitle>{t('srv.channels.newTitle')}</DialogTitle>
            <DialogDescription>{t('srv.channels.newDesc')}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="newChanName">{t('srv.channels.nameLabel')}</Label>
              <Input
                id="newChanName"
                autoFocus
                value={newName}
                onChange={(e) => { setNewName(e.target.value); setCreateErr('') }}
                onKeyDown={(e) => e.key === 'Enter' && newName.trim() && createChannel.mutate({ name: newName.trim(), type: newType })}
                placeholder={t('srv.channels.namePlaceholder')}
                maxLength={50}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>{t('srv.channels.type')}</Label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setNewType('TEXT')}
                  className={`p-3 border text-left transition-colors cursor-pointer ${
                    newType === 'TEXT'
                      ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                      : 'border-(--border) hover:border-(--accent)'
                  }`}
                >
                  <p className="m-0 text-sm font-medium" style={{ fontFamily: 'var(--font-display)' }}>{t('srv.channels.typeText')}</p>
                  <p className="m-0 mt-0.5 text-[11px] text-(--text-3)">{t('srv.channels.typeTextDesc')}</p>
                </button>
                <button
                  type="button"
                  onClick={() => setNewType('VOICE')}
                  className={`p-3 border text-left transition-colors cursor-pointer ${
                    newType === 'VOICE'
                      ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                      : 'border-(--border) hover:border-(--accent)'
                  }`}
                >
                  <p className="m-0 text-sm font-medium" style={{ fontFamily: 'var(--font-display)' }}>{t('srv.channels.typeVoice')}</p>
                  <p className="m-0 mt-0.5 text-[11px] text-(--text-3)">{t('srv.channels.typeVoiceDesc')}</p>
                </button>
              </div>
            </div>
            {createErr && <p className="text-xs text-(--danger)">{createErr}</p>}
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={() => { setCreateOpen(false); setNewName(''); setCreateErr('') }}>{t('srv.common.cancel')}</Button>
            <Button
              onClick={() => newName.trim() && createChannel.mutate({ name: newName.trim(), type: newType })}
              disabled={createChannel.isPending || !newName.trim()}
            >
              {createChannel.isPending ? t('srv.channels.creating') : t('srv.channels.createBtn')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ─── BadgesSection ────────────────────────────────────────────
// Gerenciador de insígnias: dono cria badge (emoji+nome+cor+desc) e
// concede a membros. As concessões aparecem no perfil de cada um.
function BadgesSection({ serverId, members }: { serverId: string; members: Member[] }) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const confirm = useConfirm()
  const { data: badges = [], isLoading } = useQuery<ServerBadge[]>({
    queryKey: ['server-badges', serverId],
    queryFn:  async () => (await api.get(`/api/servers/${serverId}/badges`)).data.data,
  })

  const [icon,  setIcon]  = useState('✦')
  const [name,  setName]  = useState('')
  const [color, setColor] = useState('#c9a96e')
  const [desc,  setDesc]  = useState('')
  const [err,   setErr]   = useState('')

  const create = useMutation({
    mutationFn: async () => (await api.post(`/api/servers/${serverId}/badges`, {
      name: name.trim(), icon: icon.trim() || '✦', color, description: desc.trim() || null,
    })).data.data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['server-badges', serverId] })
      setName(''); setDesc(''); setIcon('✦'); setErr('')
      toast.success(t('srv.badges.created'))
    },
    onError: (e: any) => setErr(e.response?.data?.error ?? t('srv.badges.createError')),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => api.delete(`/api/servers/${serverId}/badges/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['server-badges', serverId] }),
  })

  const [granting, setGranting] = useState<ServerBadge | null>(null)
  const chip = (c: string | null) => ({
    borderColor: `color-mix(in srgb, ${c ?? 'var(--accent)'} 45%, transparent)`,
    background:  `color-mix(in srgb, ${c ?? 'var(--accent)'} 12%, transparent)`,
    color:        c ?? 'var(--accent)',
  })

  return (
    <section className="space-y-6">
      {/* Criar */}
      <div className="border border-(--border) bg-(--raised)/30 rounded-2xl p-4 space-y-3">
        <p className="ed-label">{t('srv.badges.create')}</p>
        <div className="flex gap-2 items-center flex-wrap">
          <input
            value={icon}
            onChange={(e) => setIcon(e.target.value.slice(0, 4))}
            className="w-14 h-10 text-center text-lg border border-(--border) bg-(--base) rounded-lg focus:border-(--accent) outline-none"
            aria-label={t('srv.badges.emojiAria')}
          />
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('srv.badges.namePlaceholder')} maxLength={40} className="flex-1 min-w-40" />
          <input type="color" value={color} onChange={(e) => setColor(e.target.value)} className="size-10 cursor-pointer bg-transparent border border-(--border-mid) rounded-lg shrink-0" aria-label={t('srv.badges.colorAria')} />
        </div>
        <Input value={desc} onChange={(e) => setDesc(e.target.value)} placeholder={t('srv.badges.descPlaceholder')} maxLength={120} />
        {name.trim() && (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs" style={chip(color)}>
            <span className="text-sm leading-none">{icon || '✦'}</span>
            <span className="font-medium" style={{ fontFamily: 'var(--font-display)' }}>{name}</span>
          </span>
        )}
        {err && <p className="text-xs text-(--danger) m-0">{err}</p>}
        <Button onClick={() => create.mutate()} disabled={!name.trim() || create.isPending} className="gap-2">
          <Plus className="size-3.5" /> {create.isPending ? t('srv.badges.creating') : t('srv.badges.createBtn')}
        </Button>
      </div>

      {/* Existentes */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="ed-label">{t('srv.badges.serverBadges')}</span>
          <div className="flex-1 h-px bg-(--border)" />
          <span className="ed-marg">{badges.length}</span>
        </div>
        {isLoading && <div className="flex items-center gap-2 text-sm text-(--text-3)"><Spinner size={12} /> {t('srv.common.loading')}</div>}
        {!isLoading && badges.length === 0 && <p className="text-sm text-(--text-3) italic m-0">{t('srv.badges.empty')}</p>}
        <ul className="flex flex-col gap-1.5">
          {badges.map((b) => (
            <li key={b.id} className="flex items-center gap-2.5 px-3 py-2.5 border border-(--border) rounded-lg bg-(--raised)/20">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs shrink-0" style={chip(b.color)}>
                <span className="text-sm leading-none">{b.icon}</span>
                <span className="font-medium" style={{ fontFamily: 'var(--font-display)' }}>{b.name}</span>
              </span>
              <span className="flex-1 min-w-0 text-[11px] text-(--text-3) truncate">
                {t('srv.badges.memberCount', { count: b.grantedUserIds.length })}{b.description ? ` · ${b.description}` : ''}
              </span>
              <Button size="sm" variant="secondary" className="gap-1.5 h-8 shrink-0" onClick={() => setGranting(b)}>
                <UserPlus className="size-3.5" /> {t('srv.badges.grant')}
              </Button>
              <button
                onClick={async () => {
                  const ok = await confirm({ title: t('srv.badges.deleteTitle', { name: b.name }), description: t('srv.badges.deleteDesc'), confirmLabel: t('srv.badges.deleteConfirm'), destructive: true })
                  if (ok) remove.mutate(b.id, { onSuccess: () => toast.success(t('srv.badges.deleted')) })
                }}
                className="size-8 grid place-items-center border border-(--border) rounded-lg text-(--text-3) hover:border-(--danger) hover:text-(--danger) transition-colors cursor-pointer shrink-0"
                title={t('srv.common.delete')}
              >
                <Trash2 className="size-3.5" />
              </button>
            </li>
          ))}
        </ul>
      </div>

      {granting && (
        <GrantBadgeDialog serverId={serverId} badge={granting} members={members} onClose={() => setGranting(null)} />
      )}
    </section>
  )
}

// Dialog de concessão: lista de membros com toggle (concede/revoga na hora).
function GrantBadgeDialog({ serverId, badge, members, onClose }: {
  serverId: string; badge: ServerBadge; members: Member[]; onClose: () => void
}) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [granted, setGranted] = useState<Set<string>>(new Set(badge.grantedUserIds))

  const grant = useMutation({
    mutationFn: async (userId: string) => api.post(`/api/servers/${serverId}/badges/${badge.id}/grants`, { userId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['server-badges', serverId] }),
  })
  const revoke = useMutation({
    mutationFn: async (userId: string) => api.delete(`/api/servers/${serverId}/badges/${badge.id}/grants/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['server-badges', serverId] }),
  })

  const toggle = (userId: string) => {
    setGranted((prev) => {
      const next = new Set(prev)
      if (next.has(userId)) { next.delete(userId); revoke.mutate(userId) }
      else { next.add(userId); grant.mutate(userId) }
      return next
    })
  }

  const q = search.trim().toLowerCase()
  const list = members.filter((m) => !q || m.user.displayName.toLowerCase().includes(q) || m.user.username.toLowerCase().includes(q))

  return (
    <Dialog open onOpenChange={(o: boolean) => !o && onClose()}>
      <DialogContent className="max-w-95! sm:max-w-md! p-0 overflow-hidden">
        <DialogHeader className="px-6 pt-6 pb-3">
          <DialogTitle className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs" style={{
              borderColor: `color-mix(in srgb, ${badge.color ?? 'var(--accent)'} 45%, transparent)`,
              background:  `color-mix(in srgb, ${badge.color ?? 'var(--accent)'} 12%, transparent)`,
              color:        badge.color ?? 'var(--accent)',
            }}>
              <span className="text-sm leading-none">{badge.icon}</span>
              <span className="font-medium" style={{ fontFamily: 'var(--font-display)' }}>{badge.name}</span>
            </span>
          </DialogTitle>
          <DialogDescription className="text-(--text-3)">{t('srv.badges.grantDesc')}</DialogDescription>
        </DialogHeader>
        <div className="px-6 pb-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-(--text-3) pointer-events-none" />
            <Input placeholder={t('srv.badges.searchMember')} value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" autoFocus />
          </div>
        </div>
        <div className="border-t border-(--border) max-h-80 overflow-y-auto">
          {list.map((m) => {
            const on = granted.has(m.userId)
            return (
              <button key={m.id} onClick={() => toggle(m.userId)} className="w-full flex items-center gap-3 px-6 py-2.5 border-b border-(--border) last:border-b-0 hover:bg-(--raised)/40 transition-colors text-left">
                <Avatar className="size-8 shrink-0">
                  {m.user.avatarUrl && <AvatarImage src={m.user.avatarUrl} referrerPolicy="no-referrer" />}
                  <AvatarFallback className="text-[10px]">{m.user.displayName.slice(0, 1).toUpperCase()}</AvatarFallback>
                </Avatar>
                <div className="flex-1 min-w-0">
                  <p className="text-sm m-0 truncate text-foreground" style={{ fontFamily: 'var(--font-display)' }}>{m.user.displayName}</p>
                  <p className="text-[10px] font-mono text-(--text-3) m-0 truncate">@{m.user.username}</p>
                </div>
                <span className={cn('size-5 rounded-full border grid place-items-center shrink-0 transition-colors', on ? 'bg-(--accent) border-(--accent) text-(--text-inv)' : 'border-(--border-mid)')}>
                  {on && <Check className="size-3" />}
                </span>
              </button>
            )
          })}
        </div>
        <DialogFooter className="px-6 py-4 border-t border-(--border)">
          <Button variant="secondary" onClick={onClose} className="ml-auto">{t('srv.common.close')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ──────────────────────────────────────────────────────────────
// InviteFriendsDialog — picker editorial com search + lista de amigos.
//
// Filtra amigos que JÁ são membros (não dá pra convidar quem já está).
// Mutation otimista: ao clicar adiciona, mostra "✓ Convidado" no item,
// invalida ['members', serverId] pra refletir.
// ──────────────────────────────────────────────────────────────
interface Friend {
  friendshipId: string
  user: { id: string; username: string; displayName: string; avatarUrl: string|null }
  presence: { status: string }
}

function InviteFriendsDialog({
  open, onClose, serverId, serverName, currentMemberUserIds,
}: {
  open: boolean
  onClose: () => void
  serverId: string
  serverName: string
  currentMemberUserIds: Set<string>
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [invitedNow, setInvitedNow] = useState<Set<string>>(new Set())

  const { data: friends = [], isLoading } = useQuery<Friend[]>({
    queryKey: ['friends'],
    queryFn:  async () => (await api.get('/api/friends')).data.data,
    enabled:  open,
    staleTime: 30_000,
  })

  const addFriend = useMutation({
    mutationFn: async (friendUserId: string) =>
      (await api.post(`/api/servers/${serverId}/add-friend`, { friendUserId })).data.data,
    onSuccess: (_data, friendUserId) => {
      setInvitedNow((prev) => new Set(prev).add(friendUserId))
      queryClient.invalidateQueries({ queryKey: ['members', serverId] })
      toast.success(t('srv.inviteFriends.added'))
    },
    onError: (e: any) => toast.error(e.response?.data?.error ?? t('srv.inviteFriends.addError')),
  })

  const eligible = friends.filter((f) => {
    if (currentMemberUserIds.has(f.user.id)) return false
    if (!search.trim()) return true
    const q = search.toLowerCase()
    return f.user.displayName.toLowerCase().includes(q) || f.user.username.toLowerCase().includes(q)
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-95! sm:max-w-md! p-0 overflow-hidden">
        <DialogHeader className="px-6 pt-6 pb-3">
          <div className="size-10 bg-(--accent-dim) border border-(--accent)/40 rounded-xl flex items-center justify-center mb-2">
            <UserPlus className="size-5 text-(--accent)" />
          </div>
          <DialogTitle>{t('srv.inviteFriends.title')}</DialogTitle>
          <DialogDescription className="text-(--text-3)">
            {t('srv.inviteFriends.descPrefix')}<span className="text-foreground">{serverName}</span>{t('srv.inviteFriends.descSuffix')}
          </DialogDescription>
        </DialogHeader>

        <div className="px-6 pb-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-(--text-3) pointer-events-none" />
            <Input
              placeholder={t('srv.inviteFriends.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
              autoFocus
            />
          </div>
        </div>

        <div className="border-t border-(--border) max-h-80 overflow-y-auto">
          {isLoading ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-(--text-3)">
              <Spinner size={14} /> {t('srv.inviteFriends.loading')}
            </div>
          ) : eligible.length === 0 ? (
            <div className="py-10 px-6 text-center">
              <p className="text-sm text-(--text-2) m-0">
                {friends.length === 0
                  ? t('srv.inviteFriends.noFriends')
                  : currentMemberUserIds.size > 0 && friends.length === currentMemberUserIds.size
                    ? t('srv.inviteFriends.allMembers')
                    : t('srv.inviteFriends.noMatch')}
              </p>
            </div>
          ) : (
            <AnimatePresence initial={false}>
              {eligible.map((f, i) => {
                const isInvited = invitedNow.has(f.user.id)
                return (
                  <motion.div
                    key={f.user.id}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{    opacity: 0, x: -8 }}
                    transition={{ duration: 0.25, delay: Math.min(i * 0.025, 0.2), ease: [0.16, 1, 0.3, 1] }}
                    className="flex items-center gap-3 px-6 py-2.5 border-b border-(--border) last:border-b-0 hover:bg-(--raised)/40 transition-colors"
                  >
                    <Avatar className="size-8 shrink-0 border border-(--border-mid)">
                      {f.user.avatarUrl && <AvatarImage src={f.user.avatarUrl} referrerPolicy="no-referrer" />}
                      <AvatarFallback className="text-[10px] font-(family-name:--font-display)">
                        {f.user.displayName.slice(0, 1).toUpperCase()}
                      </AvatarFallback>
                    </Avatar>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm m-0 truncate text-foreground" style={{ fontFamily: 'var(--font-display)' }}>
                        {f.user.displayName}
                      </p>
                      <p className="text-[10px] font-mono text-(--text-3) m-0 truncate">@{f.user.username}</p>
                    </div>
                    <Button
                      size="sm"
                      variant={isInvited ? 'secondary' : 'default'}
                      disabled={isInvited || addFriend.isPending}
                      onClick={() => addFriend.mutate(f.user.id)}
                      className="gap-1.5 shrink-0"
                    >
                      {isInvited ? <><Check className="size-3.5" /> {t('srv.inviteFriends.invited')}</> : <><Plus className="size-3.5" /> {t('srv.inviteFriends.add')}</>}
                    </Button>
                  </motion.div>
                )
              })}
            </AnimatePresence>
          )}
        </div>

        <DialogFooter className="px-6 py-4 border-t border-(--border)">
          <Button variant="secondary" onClick={onClose} className="ml-auto">{t('srv.common.close')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
