import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Users, UserPlus, Settings as SettingsIcon, Pencil, LogOut, Trash2, PanelLeftClose, PanelLeftOpen, Mic, Copy, Eye } from 'lucide-react'
import { EditorialContextMenu, type EditorialMenuItem } from '@/components/EditorialContextMenu'
import { useLongPress } from '@/hooks/useLongPress'
import { useConfirm, usePrompt } from '@/hooks/useConfirm'
import { toast } from '@/components/ui/sonner'
import { useVoiceCall, useVoiceConfig, parseRoomName } from '@/hooks/useVoiceCall'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'
import { usePresenceStore } from '@/store/presenceStore'
import { useAuth } from '@/hooks/useAuth'
import { useUnread } from '@/hooks/useUnread'
import { useMyPerms } from '@/hooks/useMyPerms'
import ProfileCard from '@/components/ProfileCard'
import StatusDot, { STATUS_META } from '@/components/StatusDot'
import UmbraLogo from '@/components/UmbraLogo'
import ServerContextMenu, { type ContextMenuItem } from '@/components/ServerContextMenu'
import { SidebarSkeleton } from '@/components/skeletons/SidebarSkeleton'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import { Empty, EmptyIcon, EmptyLabel, EmptyTitle, EmptyDescription } from '@/components/ui/empty'
import { cn } from '@/lib/utils'
import type { ServerWithChannels, ChannelInfo } from '@umbra/types'

interface SidebarProps {
  activeChannelId: string | null
  onSelectChannel: (channelId: string, channelName: string, serverId: string) => void
}

const PALETTE = ['#c9a96e','#7c6fc4','#6fa8c9','#c97c6e','#6ec98a']
function userColor(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return PALETTE[h % PALETTE.length]
}

interface CtxMenu { x: number; y: number; server: ServerWithChannels; isOwner: boolean }

export default function Sidebar({ activeChannelId, onSelectChannel }: SidebarProps) {
  const user        = useAuthStore((s) => s.user)
  const { logout }  = useAuth()
  const navigate    = useNavigate()
  const queryClient = useQueryClient()
  const unread      = useUnread()

  const [activeServerId,  setActiveServerId]  = useState<string | null>(null)
  const [collapsed,       setCollapsed]       = useState<boolean>(() => {
    const stored = localStorage.getItem('umbra-sidebar-collapsed')
    if (stored !== null) return stored === '1'
    // Default colapsado em mobile (< 768px)
    return typeof window !== 'undefined' && window.innerWidth < 768
  })
  const mobileOpen    = useUIStore((s) => s.mobileSidebarOpen)
  const closeMobile   = useUIStore((s) => s.closeMobileSidebar)
  const myStatus      = usePresenceStore((s) => s.myStatus)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [createMode,      setCreateMode]      = useState<'server' | 'group'>('server')
  const [serverName,      setServerName]      = useState('')
  const [createError,     setCreateError]     = useState('')
  /** Posição do clique no botão "Criar…" — modal usa pra animar saindo dele. */
  const [popOrigin, setPopOrigin] = useState<{ x: number; y: number } | null>(null)
  const [ctxMenu,         setCtxMenu]         = useState<CtxMenu | null>(null)
  const [showAddMember,   setShowAddMember]   = useState(false)
  const [inviteUsername,  setInviteUsername]  = useState('')
  const [inviteError,     setInviteError]     = useState('')
  const [inviteSuccess,   setInviteSuccess]   = useState('')
  const [showEditModal,   setShowEditModal]   = useState(false)
  const [editServerId,    setEditServerId]    = useState<string | null>(null)
  const [editName,        setEditName]        = useState('')
  const [editError,       setEditError]       = useState('')
  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const [deleteServerId,  setDeleteServerId]  = useState<string | null>(null)
  const [deleteError,     setDeleteError]     = useState('')
  const [showOwnProfile,    setShowOwnProfile]    = useState(false)
  const [ownProfileAnchor,  setOwnProfileAnchor]  = useState<HTMLElement | null>(null)
  const [channelAreaCtx,    setChannelAreaCtx]    = useState<{ x: number; y: number } | null>(null)
  const [showCreateChannel, setShowCreateChannel] = useState(false)
  const [newChanName,       setNewChanName]       = useState('')
  const [newChanType,       setNewChanType]       = useState<'TEXT' | 'VOICE'>('TEXT')
  const [newChanErr,        setNewChanErr]        = useState('')

  const { data: servers = [], isLoading: serversLoading } = useQuery<ServerWithChannels[]>({
    queryKey: ['servers'],
    queryFn: async () => (await api.get('/api/servers')).data.data,
    // Servidores mudam só por ação explícita (criar/sair/renomear) → invalidação
    // manual cobre. 5min de staleTime corta refetch automático em background.
    staleTime: 5 * 60_000,
  })

  useEffect(() => {
    if (servers.length && !activeServerId) setActiveServerId(servers[0].id)
  }, [servers, activeServerId])

  const createServer = useMutation({
    mutationFn: async ({ name, isGroup }: { name: string; isGroup: boolean }) =>
      (await api.post('/api/servers', { name, isGroup })).data.data,
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setActiveServerId(s.id)
      setShowCreateModal(false); setServerName(''); setCreateError('')
    },
    onError: (e: any) => setCreateError(e.response?.data?.error ?? 'Erro ao criar'),
  })

  const inviteMember = useMutation({
    mutationFn: async ({ serverId, username }: { serverId: string; username: string }) =>
      (await api.post(`/api/servers/${serverId}/invite/${username}`)).data,
    onSuccess: (data) => {
      setInviteSuccess(data.message ?? 'Membro adicionado!')
      setInviteUsername(''); setInviteError('')
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setTimeout(() => setInviteSuccess(''), 3000)
    },
    onError: (e: any) => setInviteError(e.response?.data?.error ?? 'Erro'),
  })

  const renameServer = useMutation({
    mutationFn: async ({ id, name }: { id: string; name: string }) =>
      (await api.patch(`/api/servers/${id}`, { name })).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setShowEditModal(false); setEditName(''); setEditError('')
    },
    onError: (e: any) => setEditError(e.response?.data?.error ?? 'Erro'),
  })

  const deleteServer = useMutation({
    mutationFn: async (id: string) => api.delete(`/api/servers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setShowDeleteModal(false); setDeleteServerId(null); setDeleteError('')
      if (deleteServerId === activeServerId) setActiveServerId(null)
    },
    onError: (e: any) => setDeleteError(e.response?.data?.error ?? 'Erro'),
  })

  const leaveServer = useMutation({
    mutationFn: async (id: string) => api.delete(`/api/servers/${id}/leave`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      if (ctxMenu?.server.id === activeServerId) setActiveServerId(null)
    },
  })

  const createChannel = useMutation({
    mutationFn: async ({ name, type }: { name: string; type: 'TEXT' | 'VOICE' }) =>
      (await api.post(`/api/servers/${activeServerId}/channels`, { name, type })).data.data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] })
      setShowCreateChannel(false); setNewChanName(''); setNewChanType('TEXT'); setNewChanErr('')
    },
    onError: (e: any) => setNewChanErr(e?.response?.data?.error ?? 'Erro ao criar'),
  })

  const renameChannel = useMutation({
    mutationFn: async (p: { channelId: string; name: string }) =>
      (await api.patch(`/api/servers/${activeServerId}/channels/${p.channelId}`, { name: p.name })).data.data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['servers'] }),
  })

  const deleteChannel = useMutation({
    mutationFn: async (channelId: string) =>
      api.delete(`/api/servers/${activeServerId}/channels/${channelId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['servers'] }),
  })

  const activeServerPerms = useMyPerms(activeServerId ?? undefined)
  const canManageChannels = activeServerPerms.isOwner || activeServerPerms.has('MANAGE_CHANNELS')

  const handleChannelAreaContextMenu = useCallback((e: React.MouseEvent) => {
    if (!activeServerId || !canManageChannels) return
    // Só abre menu se clicou no fundo (não num botão de canal)
    const target = e.target as HTMLElement
    if (target.closest('button')) return
    e.preventDefault(); e.stopPropagation()
    setChannelAreaCtx({ x: e.clientX, y: e.clientY })
  }, [activeServerId, canManageChannels])

  const handleContextMenu = useCallback((e: React.MouseEvent, server: ServerWithChannels) => {
    e.preventDefault(); e.stopPropagation()
    setCtxMenu({ x: e.clientX, y: e.clientY, server, isOwner: server.ownerId === user?.id })
  }, [user?.id])

  const buildMenuItems = (menu: CtxMenu): ContextMenuItem[] => {
    const items: ContextMenuItem[] = []
    if (!menu.server.isGroup) {
      items.push({
        icon: '🔗', label: 'Copiar link de convite',
        onClick: () => navigator.clipboard.writeText(`${window.location.origin}/invite/${menu.server.inviteCode}`),
      })
    }
    items.push({
      icon: '⚙️', label: 'Configurações',
      onClick: () => navigate(`/app/servers/${menu.server.id}/settings`),
    })
    if (menu.isOwner) {
      items.push({
        icon: '✏️', label: `Renomear ${menu.server.isGroup ? 'grupo' : 'servidor'}`,
        onClick: () => { setEditServerId(menu.server.id); setEditName(menu.server.name); setEditError(''); setShowEditModal(true) },
      })
      if (menu.server.isGroup) {
        items.push({ icon: '👥', label: 'Adicionar membro', onClick: () => { setActiveServerId(menu.server.id); setShowAddMember(true); setInviteError(''); setInviteSuccess('') } })
      }
      items.push({ icon: '🗑️', label: `Excluir ${menu.server.isGroup ? 'grupo' : 'servidor'}`, danger: true, onClick: () => { setDeleteServerId(menu.server.id); setDeleteError(''); setShowDeleteModal(true) } })
    } else {
      items.push({ icon: '🚪', label: `Sair do ${menu.server.isGroup ? 'grupo' : 'servidor'}`, danger: true, onClick: () => leaveServer.mutate(menu.server.id) })
    }
    return items
  }

  const activeServer   = servers.find((s) => s.id === activeServerId)
  // Mostra TEXT + VOICE (ChannelButton já trata ícone por tipo)
  const channels       = activeServer?.channels ?? []
  const isGroup        = activeServer?.isGroup ?? false
  const regularServers = servers.filter((s) => !s.isGroup)
  const groups         = servers.filter((s) => s.isGroup)
  const editTarget     = servers.find((s) => s.id === editServerId)
  const deleteTarget   = servers.find((s) => s.id === deleteServerId)

  const accentColor = user?.id ? userColor(user.id) : 'var(--accent)'

  if (serversLoading) return <SidebarSkeleton />

  return (
    <>
      {/* Backdrop mobile — atrás do drawer */}
      {mobileOpen && (
        <div
          onClick={closeMobile}
          className="md:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm anim-fade-in"
        />
      )}

      <div
        className={cn(
          'flex h-full shrink-0 z-50 transition-transform duration-300 ease-(--ease-spring)',
          // Desktop: estático na grid normal
          'md:relative md:translate-x-0',
          // Mobile: fixed off-screen, slide-in
          'fixed top-0 left-0 bottom-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0',
        )}
      >

        {/* ── Server strip ─────────────────────────────────── */}
        <div className="w-16 h-full bg-background border-r border-border flex flex-col items-center py-3 gap-1.5 overflow-y-auto shrink-0">
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                onClick={() => { navigate('/app/dm'); closeMobile() }}
                className="size-11 mb-1 shrink-0 p-0 bg-transparent border-none rounded-xl flex items-center justify-center hover:scale-110 hover:brightness-110 transition-all cursor-pointer"
              >
                <UmbraLogo size={44} />
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">Mensagens diretas</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <button
                onClick={() => { navigate('/app/friends'); closeMobile() }}
                className="size-9 shrink-0 grid place-items-center text-(--text-3) hover:text-(--accent) transition-colors cursor-pointer"
                aria-label="Amigos"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
                  <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                </svg>
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">Amigos</TooltipContent>
          </Tooltip>

          <div className="w-7 h-px bg-border my-0.5" />

          {regularServers.map((s, i) => (
            <ServerIcon
              key={s.id}
              server={s}
              isActive={s.id === activeServerId}
              index={i}
              onClick={() => setActiveServerId(s.id)}
              onContextMenu={(e) => handleContextMenu(e, s)}
            />
          ))}

          {groups.length > 0 && (
            <>
              <div className="w-7 h-px bg-border my-0.5" />
              {groups.map((s, i) => (
                <ServerIcon
                  key={s.id}
                  server={s}
                  isActive={s.id === activeServerId}
                  index={regularServers.length + i}
                  isGroup
                  onClick={() => setActiveServerId(s.id)}
                  onContextMenu={(e) => handleContextMenu(e, s)}
                />
              ))}
            </>
          )}

          <div className="w-7 h-px bg-border my-0.5" />
          <StripButton
            title="Criar servidor"
            icon={<Plus className="size-5" />}
            onClick={(origin) => { setCreateMode('server'); setPopOrigin(origin); setShowCreateModal(true) }}
          />
          <StripButton
            title="Criar grupo"
            icon={<Users className="size-4" />}
            onClick={(origin) => { setCreateMode('group'); setPopOrigin(origin); setShowCreateModal(true) }}
          />

          {/* spacer empurra toggle pro fundo */}
          <div className="flex-1" />

          <StripButton
            title={collapsed ? 'Expandir painel' : 'Esconder painel'}
            icon={collapsed ? <PanelLeftOpen className="size-4" /> : <PanelLeftClose className="size-4" />}
            onClick={() => {
              setCollapsed((c) => {
                const next = !c
                localStorage.setItem('umbra-sidebar-collapsed', next ? '1' : '0')
                return next
              })
            }}
          />
        </div>

        {/* ── Channel panel ─────────────────────────────────── */}
        <div
          className={cn(
            'h-full bg-muted border-r border-border flex flex-col overflow-hidden transition-[width] duration-300 ease-(--ease-spring)',
            collapsed ? 'w-0' : 'w-55'
          )}
        >
          {activeServer && (
            <div className="h-16 px-4 flex items-center gap-2.5 border-b border-(--border) shrink-0">
              {isGroup && <Users className="size-3.5 text-(--text-3)" />}
              <h2
                className="text-lg m-0 flex-1 truncate text-foreground font-normal tracking-tight"
                style={{ fontFamily: 'var(--font-display)' }}
              >
                {activeServer.name}
              </h2>
              {isGroup && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <button
                      onClick={() => { setShowAddMember(true); setInviteError(''); setInviteSuccess('') }}
                      className="bg-transparent border-none cursor-pointer text-muted-foreground hover:text-primary p-1 rounded-md flex items-center transition-colors"
                    >
                      <UserPlus className="size-4" />
                    </button>
                  </TooltipTrigger>
                  <TooltipContent side="bottom">Adicionar membro</TooltipContent>
                </Tooltip>
              )}
            </div>
          )}

          <div
            className="flex-1 overflow-y-auto px-2 py-2.5"
            onContextMenu={handleChannelAreaContextMenu}
          >
            {channels.length > 0 ? (
              <>
                <div className="px-3 mb-1.5">
                  <span className="text-[10px] uppercase tracking-wider text-(--text-3) font-medium">
                    {isGroup ? 'Canais do grupo' : 'Canais'}
                  </span>
                </div>
                {channels.map((ch, i) => (
                  <ChannelButton
                    key={ch.id}
                    channel={ch}
                    isActive={activeChannelId === ch.id}
                    hasUnread={activeChannelId !== ch.id && unread.hasUnread(ch.id, (ch as any).lastMessageAt)}
                    onClick={() => { onSelectChannel(ch.id, ch.name, activeServer!.id); closeMobile() }}
                    index={i}
                    canManage={canManageChannels}
                    onRename={(newName) => renameChannel.mutate({ channelId: ch.id, name: newName })}
                    onDelete={() => deleteChannel.mutate(ch.id)}
                    onMarkRead={() => unread.markRead(ch.id)}
                  />
                ))}
              </>
            ) : !activeServer ? (
              <Empty className="h-full py-8">
                <EmptyIcon><Plus className="size-5 text-(--accent)" /></EmptyIcon>
                <EmptyLabel>— {servers.length === 0 ? 'Cap. ∅' : '— · —'}</EmptyLabel>
                <EmptyTitle className="text-base">
                  {servers.length === 0 ? 'Nenhum servidor' : 'Selecione um'}
                </EmptyTitle>
                <EmptyDescription>
                  {servers.length === 0
                    ? 'Use o + na barra esquerda pra criar seu primeiro espaço.'
                    : 'Clique num ícone à esquerda pra abrir os canais.'}
                </EmptyDescription>
              </Empty>
            ) : null}
          </div>

          {/* User footer */}
          <div className="h-14 px-2 bg-background border-t border-border flex items-center gap-1.5 shrink-0">
            <button
              onClick={(e) => { setOwnProfileAnchor(e.currentTarget); setShowOwnProfile(true) }}
              className="relative size-8 rounded-full overflow-hidden shrink-0 flex items-center justify-center cursor-pointer p-0 border-2 transition-colors"
              style={{ background: accentColor + '33', borderColor: accentColor + '66' }}
              onMouseEnter={(e) => (e.currentTarget.style.borderColor = accentColor)}
              onMouseLeave={(e) => (e.currentTarget.style.borderColor = accentColor + '66')}
              title="Ver meu perfil"
            >
              {user?.avatarUrl
                ? <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover" />
                : <span className="text-xs font-bold" style={{ color: accentColor }}>{user?.displayName?.slice(0,1).toUpperCase()}</span>
              }
              <span className="absolute -bottom-0.5 -right-0.5">
                <StatusDot status={myStatus} size={11} bordered borderColor="var(--background)" />
              </span>
            </button>

            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold m-0 truncate text-foreground">{user?.displayName}</p>
              <p className="text-[10px] m-0 truncate text-muted-foreground flex items-center gap-1">
                <StatusDot status={myStatus} size={7} />
                <span className="truncate">{STATUS_META[myStatus].label}</span>
              </p>
            </div>

            <FooterBtn title="Editar perfil" onClick={() => { navigate('/app/profile'); closeMobile() }}>
              <Pencil className="size-3.5" />
            </FooterBtn>
            <FooterBtn title="Configurações" onClick={() => { navigate('/app/settings'); closeMobile() }}>
              <SettingsIcon className="size-3.5" />
            </FooterBtn>
            <FooterBtn title="Sair" onClick={logout} danger>
              <LogOut className="size-3.5" />
            </FooterBtn>
          </div>
        </div>
      </div>

      {ctxMenu && (
        <ServerContextMenu x={ctxMenu.x} y={ctxMenu.y} items={buildMenuItems(ctxMenu)} onClose={() => setCtxMenu(null)} />
      )}

      {channelAreaCtx && (
        <ServerContextMenu
          x={channelAreaCtx.x}
          y={channelAreaCtx.y}
          onClose={() => setChannelAreaCtx(null)}
          items={[
            {
              icon: '＋', label: 'Criar canal',
              onClick: () => { setShowCreateChannel(true); setNewChanErr('') },
            },
            {
              icon: '⚙', label: 'Configurações do servidor',
              onClick: () => activeServerId && navigate(`/app/servers/${activeServerId}/settings`),
            },
          ]}
        />
      )}

      {showOwnProfile && user && (
        <ProfileCard userId={user.id} anchorEl={ownProfileAnchor} onClose={() => { setShowOwnProfile(false); setOwnProfileAnchor(null) }} />
      )}

      {/* ── Create server/group ─────────────────────────────── */}
      <Dialog
        open={showCreateModal}
        onOpenChange={(o: boolean) => { if (!o) { setShowCreateModal(false); setServerName(''); setCreateError(''); setPopOrigin(null) } }}
      >
        <DialogContent
          className="max-w-95! data-[state=open]:animate-none! anim-pop-open"
          style={popOrigin ? {
            // tx/ty = vetor do centro da viewport até o botão (negativo se botão à esquerda/acima)
            // O keyframe começa nesse offset com scale 0.18 → "sai" do botão e cresce até o centro
            ['--pop-tx' as any]: `${popOrigin.x - window.innerWidth  / 2}px`,
            ['--pop-ty' as any]: `${popOrigin.y - window.innerHeight / 2}px`,
          } : undefined}
        >
          <DialogHeader className="gap-1.5">
            <div className="size-10 bg-primary/10 border border-border rounded-xl flex items-center justify-center mb-2">
              {createMode === 'group' ? <Users className="size-5 text-primary" /> : <Plus className="size-5 text-primary" />}
            </div>
            <DialogTitle>{createMode === 'group' ? 'Novo grupo' : 'Novo servidor'}</DialogTitle>
            <DialogDescription>
              {createMode === 'group'
                ? 'Grupos são privados — adicione membros manualmente.'
                : 'Servidores podem ser acessados por link de convite.'}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="serverName">Nome</Label>
            <Input
              id="serverName"
              autoFocus
              value={serverName}
              onChange={(e) => { setServerName(e.target.value); setCreateError('') }}
              onKeyDown={(e) => e.key === 'Enter' && serverName.trim() && createServer.mutate({ name: serverName.trim(), isGroup: createMode === 'group' })}
              placeholder={createMode === 'group' ? 'Ex: Amigos da faculdade' : 'Ex: Meu Servidor'}
            />
            {createError && <p className="text-xs text-destructive">{createError}</p>}
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={() => { setShowCreateModal(false); setServerName(''); setCreateError('') }}>
              Cancelar
            </Button>
            <Button
              onClick={() => serverName.trim() && createServer.mutate({ name: serverName.trim(), isGroup: createMode === 'group' })}
              disabled={createServer.isPending || !serverName.trim()}
            >
              {createServer.isPending ? 'Criando…' : createMode === 'group' ? 'Criar grupo' : 'Criar servidor'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ── Add member ─────────────────────────────── */}
      <Dialog
        open={showAddMember && !!activeServer}
        onOpenChange={(o: boolean) => { if (!o) { setShowAddMember(false); setInviteUsername(''); setInviteError(''); setInviteSuccess('') } }}
      >
        <DialogContent className="max-w-95!">
          <DialogHeader>
            <DialogTitle>Adicionar membro</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="inviteUsername">Username</Label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm pointer-events-none">@</span>
              <Input
                id="inviteUsername"
                autoFocus
                value={inviteUsername}
                onChange={(e) => { setInviteUsername(e.target.value.toLowerCase()); setInviteError('') }}
                onKeyDown={(e) => e.key === 'Enter' && inviteUsername.trim() && activeServer && inviteMember.mutate({ serverId: activeServer.id, username: inviteUsername.trim() })}
                placeholder="nome_do_usuario"
                className="pl-7"
              />
            </div>
            {inviteError && <p className="text-xs text-destructive">{inviteError}</p>}
            {inviteSuccess && <p className="text-xs" style={{ color: 'var(--success)' }}>✓ {inviteSuccess}</p>}
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={() => { setShowAddMember(false); setInviteUsername(''); setInviteError(''); setInviteSuccess('') }}>
              Cancelar
            </Button>
            <Button
              onClick={() => activeServer && inviteUsername.trim() && inviteMember.mutate({ serverId: activeServer.id, username: inviteUsername.trim() })}
              disabled={inviteMember.isPending || !inviteUsername.trim()}
            >
              {inviteMember.isPending ? 'Adicionando…' : 'Adicionar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ── Rename ─────────────────────────────── */}
      <Dialog
        open={showEditModal && !!editTarget}
        onOpenChange={(o: boolean) => { if (!o) setShowEditModal(false) }}
      >
        <DialogContent className="max-w-95!">
          <DialogHeader>
            <DialogTitle>Renomear</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="editName">Novo nome</Label>
            <Input
              id="editName"
              autoFocus
              value={editName}
              onChange={(e) => { setEditName(e.target.value); setEditError('') }}
              onKeyDown={(e) => e.key === 'Enter' && editTarget && editName.trim() && renameServer.mutate({ id: editTarget.id, name: editName.trim() })}
              placeholder={editTarget?.name}
              maxLength={100}
            />
            {editError && <p className="text-xs text-destructive">{editError}</p>}
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setShowEditModal(false)}>Cancelar</Button>
            <Button
              onClick={() => editTarget && editName.trim() && renameServer.mutate({ id: editTarget.id, name: editName.trim() })}
              disabled={renameServer.isPending || !editName.trim()}
            >
              {renameServer.isPending ? 'Salvando…' : 'Salvar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ── Create channel ─────────────────────────────── */}
      <Dialog
        open={showCreateChannel && !!activeServerId}
        onOpenChange={(o: boolean) => { if (!o) { setShowCreateChannel(false); setNewChanName(''); setNewChanErr('') } }}
      >
        <DialogContent className="max-w-95!">
          <DialogHeader>
            <DialogTitle>Novo canal</DialogTitle>
            <DialogDescription>Escolha nome e tipo. Texto pra chat, voz pra chamadas.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="sbNewChanName">Nome</Label>
              <Input
                id="sbNewChanName"
                autoFocus
                value={newChanName}
                onChange={(e) => { setNewChanName(e.target.value); setNewChanErr('') }}
                onKeyDown={(e) => e.key === 'Enter' && newChanName.trim() && createChannel.mutate({ name: newChanName.trim(), type: newChanType })}
                placeholder="Ex: geral"
                maxLength={50}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Tipo</Label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setNewChanType('TEXT')}
                  className={cn(
                    'p-3 border text-left transition-colors cursor-pointer',
                    newChanType === 'TEXT'
                      ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                      : 'border-(--border) hover:border-(--accent)',
                  )}
                >
                  <p className="m-0 text-sm font-medium" style={{ fontFamily: 'var(--font-display)' }}># Texto</p>
                  <p className="m-0 mt-0.5 text-[11px] text-(--text-3)">Chat, anexos, threads</p>
                </button>
                <button
                  type="button"
                  onClick={() => setNewChanType('VOICE')}
                  className={cn(
                    'p-3 border text-left transition-colors cursor-pointer',
                    newChanType === 'VOICE'
                      ? 'border-(--accent) bg-(--accent-dim) text-(--accent)'
                      : 'border-(--border) hover:border-(--accent)',
                  )}
                >
                  <p className="m-0 text-sm font-medium" style={{ fontFamily: 'var(--font-display)' }}>Voz</p>
                  <p className="m-0 mt-0.5 text-[11px] text-(--text-3)">Chamada e tela</p>
                </button>
              </div>
            </div>
            {newChanErr && <p className="text-xs text-destructive">{newChanErr}</p>}
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={() => { setShowCreateChannel(false); setNewChanName(''); setNewChanErr('') }}>Cancelar</Button>
            <Button
              onClick={() => newChanName.trim() && createChannel.mutate({ name: newChanName.trim(), type: newChanType })}
              disabled={createChannel.isPending || !newChanName.trim()}
            >
              {createChannel.isPending ? 'Criando…' : 'Criar canal'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ── Delete confirm ─────────────────────────────── */}
      <Dialog
        open={showDeleteModal && !!deleteTarget}
        onOpenChange={(o: boolean) => { if (!o) setShowDeleteModal(false) }}
      >
        <DialogContent className="max-w-95! text-center">
          <div className="flex justify-center mb-1">
            <div className="size-12 rounded-full bg-destructive/10 flex items-center justify-center">
              <Trash2 className="size-6 text-destructive" />
            </div>
          </div>
          <DialogHeader className="text-center! items-center!">
            <DialogTitle>Excluir {deleteTarget?.name}?</DialogTitle>
            <DialogDescription>Esta ação é permanente e não pode ser desfeita.</DialogDescription>
          </DialogHeader>
          {deleteError && <p className="text-xs text-destructive">{deleteError}</p>}
          <DialogFooter className="sm:justify-center">
            <Button variant="secondary" onClick={() => setShowDeleteModal(false)} className="flex-1">Cancelar</Button>
            <Button
              variant="destructive"
              onClick={() => deleteTarget && deleteServer.mutate(deleteTarget.id)}
              disabled={deleteServer.isPending}
              className="flex-1"
            >
              {deleteServer.isPending ? 'Excluindo…' : 'Sim, excluir'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

// ─── Small reusable components ────────────────────────────────

function ServerIcon({ server, isActive, index, isGroup = false, onClick, onContextMenu }: {
  server: ServerWithChannels
  isActive: boolean
  index: number
  isGroup?: boolean
  onClick: () => void
  onContextMenu: (e: React.MouseEvent) => void
}) {
  // Long-press abre o menu (mobile). Sintetiza coords da posição do toque
  // pra ServerContextMenu (posicionada manualmente em x,y) cair certo.
  const longPress = useLongPress((e) => {
    const point = 'touches' in e
      ? { x: e.changedTouches?.[0]?.clientX ?? e.touches?.[0]?.clientX ?? 0,
          y: e.changedTouches?.[0]?.clientY ?? e.touches?.[0]?.clientY ?? 0 }
      : { x: (e as React.MouseEvent).clientX, y: (e as React.MouseEvent).clientY }
    onContextMenu({ ...(e as any), clientX: point.x, clientY: point.y, preventDefault: () => {}, stopPropagation: () => {} })
  })

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="relative shrink-0 group/server">
          {/* Rail indicator à esquerda — cresce no active, encolhe no hover */}
          <span
            aria-hidden
            className={cn(
              'absolute -left-3 top-1/2 -translate-y-1/2 w-0.5 bg-(--accent) transition-all duration-300 ease-(--ease-spring)',
              isActive
                ? 'h-7 opacity-100'
                : 'h-1 opacity-0 group-hover/server:opacity-100 group-hover/server:h-4',
            )}
          />
          <button
            onClick={(e) => { if (longPress.didFire()) { e.preventDefault(); return } onClick() }}
            onContextMenu={onContextMenu}
            onTouchStart={longPress.onTouchStart}
            onTouchMove={longPress.onTouchMove}
            onTouchEnd={longPress.onTouchEnd}
            onTouchCancel={longPress.onTouchCancel}
            className={cn(
              'relative size-10 shrink-0 p-0 cursor-pointer overflow-hidden flex items-center justify-center font-(family-name:--font-display) transition-all duration-300 ease-(--ease-spring)',
              'border outline-none rounded-2xl',
              isActive
                ? 'bg-(--accent) text-(--text-inv) border-(--accent) shadow-[0_4px_16px_var(--accent-glow)] scale-105'
                : 'bg-(--raised) text-(--text-2) border-(--border) hover:border-(--accent) hover:text-(--accent) hover:scale-105 hover:-translate-y-0.5',
              isGroup ? 'text-base' : 'text-sm'
            )}
            style={{ animation: `fadeUp 0.35s var(--ease-spring) ${index * 0.055}s both` }}
          >
            {server.iconUrl
              ? <img src={server.iconUrl} alt={server.name} referrerPolicy="no-referrer" className="w-full h-full object-cover" />
              : isGroup
                ? <Users className="size-4" />
                : server.name.slice(0, 2).toUpperCase()}
          </button>
        </div>
      </TooltipTrigger>
      <TooltipContent side="right">{server.name}</TooltipContent>
    </Tooltip>
  )
}

function StripButton({ title, icon, onClick }: {
  title:   string
  icon:    React.ReactNode
  /** Recebe coords do clique em viewport pra animação anchored (modal sai do botão). */
  onClick: (origin: { x: number; y: number }) => void
}) {
  const [bursts, setBursts] = useState<number[]>([])

  const handle = (e: React.MouseEvent<HTMLButtonElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const cx   = rect.left + rect.width  / 2
    const cy   = rect.top  + rect.height / 2
    const id   = Date.now() + Math.random()
    setBursts((b) => [...b, id])
    setTimeout(() => setBursts((b) => b.filter((x) => x !== id)), 650)
    onClick({ x: cx, y: cy })
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          onClick={handle}
          className="size-10 shrink-0 rounded-2xl border border-dashed border-(--border) bg-transparent text-(--text-3) cursor-pointer flex items-center justify-center transition-all duration-300 ease-(--ease-spring) hover:bg-(--accent-dim) hover:border-(--accent) hover:text-(--accent) hover:scale-105 active:scale-90 relative overflow-visible"
        >
          {icon}
          {/* Burst ring(s) — múltiplos cliques rápidos sobrepoem ondas */}
          {bursts.map((id) => (
            <span
              key={id}
              aria-hidden
              className="anim-pop-burst absolute inset-0 rounded-2xl border-2 border-(--accent) pointer-events-none"
            />
          ))}
        </button>
      </TooltipTrigger>
      <TooltipContent side="right">{title}</TooltipContent>
    </Tooltip>
  )
}

function ChannelButton({
  channel, isActive, hasUnread, onClick, index,
  canManage, onRename, onDelete, onMarkRead,
}: {
  channel:    ChannelInfo
  isActive:   boolean
  hasUnread:  boolean
  onClick:    () => void
  index:      number
  canManage?: boolean
  onRename?:  (newName: string) => void
  onDelete?:  () => void
  onMarkRead?: () => void
}) {
  const isVoice  = channel.type === 'VOICE'
  const voice    = useVoiceCall()
  const cfg      = useVoiceConfig()
  const inThis   = parseRoomName(voice.roomName)?.id === channel.id
  const confirm  = useConfirm()
  const prompt   = usePrompt()

  const handleClick = () => {
    if (isVoice) {
      // Click em canal de voz: entrar na call. Não navega.
      if (!cfg.data?.enabled) return
      if (inThis) return // já está conectado
      voice.join('channel', channel.id)
    } else {
      onClick()
    }
  }

  // Itens do context menu (right-click) — varia por perm + tipo
  const menuItems: EditorialMenuItem[] = [
    { kind: 'label', label: `${isVoice ? 'Voz' : '#'} ${channel.name}` },
  ]
  if (!isVoice && onMarkRead) {
    menuItems.push({
      kind: 'item',
      icon: <Eye className="size-3.5" />,
      label: 'Marcar como lido',
      onSelect: onMarkRead,
    })
  }
  menuItems.push({
    kind: 'item',
    icon: <Copy className="size-3.5" />,
    label: 'Copiar ID',
    shortcut: '⌘C',
    onSelect: () => { void navigator.clipboard.writeText(channel.id) },
  })
  if (canManage && onRename) {
    menuItems.push({ kind: 'separator' })
    menuItems.push({
      kind: 'item',
      icon: <Pencil className="size-3.5" />,
      label: 'Renomear',
      onSelect: async () => {
        const newName = await prompt({
          title: `Renomear canal`,
          description: `Nome atual: #${channel.name}`,
          label: 'Novo nome',
          placeholder: 'ex: geral',
          defaultValue: channel.name,
          confirmLabel: 'Renomear',
          maxLength: 50,
        })
        if (newName && newName !== channel.name) {
          onRename(newName)
          toast.success(`Canal renomeado para #${newName}`)
        }
      },
    })
  }
  if (canManage && onDelete) {
    menuItems.push({
      kind: 'item',
      icon: <Trash2 className="size-3.5" />,
      label: 'Excluir canal',
      destructive: true,
      onSelect: async () => {
        const ok = await confirm({
          title: `Excluir #${channel.name}?`,
          description: 'Todas as mensagens deste canal serão perdidas. Ação permanente.',
          confirmLabel: 'Excluir',
          destructive: true,
        })
        if (ok) {
          onDelete()
          toast.success(`Canal #${channel.name} excluído`)
        }
      },
    })
  }

  return (
    <EditorialContextMenu items={menuItems}>
    <button
      onClick={handleClick}
      disabled={isVoice && !cfg.data?.enabled}
      title={isVoice && !cfg.data?.enabled ? 'Chamadas não configuradas no servidor' : undefined}
      className={cn(
        'group w-full flex items-center gap-2.5 px-3 py-1.5 border-l-2 rounded-r-lg cursor-pointer text-left relative transition-all duration-300 ease-(--ease-spring) disabled:opacity-50 disabled:cursor-not-allowed',
        isActive || inThis
          ? 'border-(--accent) bg-(--accent-dim)'
          : 'border-transparent bg-transparent hover:border-(--border-bright) hover:bg-(--raised)/40'
      )}
      style={{ animation: `fadeLeft 0.25s var(--ease-spring) ${index * 0.04}s both` }}
    >
      {isVoice ? (
        <Mic className={cn(
          'size-3 shrink-0 transition-colors',
          inThis ? 'text-(--accent)'
            : 'text-(--text-3) group-hover:text-(--text-2)',
        )} />
      ) : (
        <span
          className={cn(
            'font-mono text-[11px] shrink-0 transition-colors',
            isActive ? 'text-(--accent)'
              : hasUnread ? 'text-foreground'
              : 'text-(--text-3) group-hover:text-(--text-2)',
          )}
        >
          #
        </span>
      )}
      <span className={cn(
        'truncate transition-colors flex-1',
        isActive || inThis ? 'text-(--accent) text-[14px]'
          : hasUnread ? 'text-foreground text-[14px] font-medium'
          : 'text-(--text-2) text-[14px] group-hover:text-foreground',
      )}
      style={{ fontFamily: (isActive || hasUnread || inThis) ? 'var(--font-display)' : 'var(--font-body)' }}>
        {channel.name}
      </span>
      {inThis && (
        <span className="text-[10px] text-(--accent) shrink-0">conectado</span>
      )}
      {!isVoice && hasUnread && !isActive && (
        <span className="size-1.5 rounded-full bg-(--accent) shrink-0" aria-label="Não lido" />
      )}
    </button>
    </EditorialContextMenu>
  )
}

function FooterBtn({ title, onClick, danger, children }: {
  title: string
  onClick: () => void
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          onClick={onClick}
          className={cn(
            'bg-transparent border-none cursor-pointer p-1 rounded-md flex items-center transition-colors',
            danger ? 'text-muted-foreground hover:text-destructive' : 'text-muted-foreground hover:text-primary'
          )}
        >
          {children}
        </button>
      </TooltipTrigger>
      <TooltipContent side="top">{title}</TooltipContent>
    </Tooltip>
  )
}
