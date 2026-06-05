import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import StatusDot, { STATUS_META, type UserStatus } from '@/components/StatusDot'
import { FONT_FAMILY, type DisplayFont } from './profileFonts'

/**
 * Hero do perfil — avatar + nome + pronouns + emoji + handle + status.
 * Avatar overlapping no banner (-mt-12).
 * Tudo opcional exceto displayName/username.
 */
interface Props {
  avatarUrl?:        string | null
  displayName:       string
  username:          string
  /** Quando passada, renderiza a coord abaixo do handle. ProfileCard só passa pro próprio user. */
  coordinate?:       string
  pronouns?:         string | null
  statusEmoji?:      string | null
  displayFont?:      DisplayFont
  effectiveStatus?:  UserStatus
  isBot?:            boolean
  accentColor:       string
}

export function ProfileHero({
  avatarUrl, displayName, username, coordinate, pronouns, statusEmoji,
  displayFont = 'serif', effectiveStatus, isBot, accentColor,
}: Props) {
  const fontFamily = FONT_FAMILY[displayFont]

  return (
    <>
      {/* Avatar — sobrepõe o banner */}
      <div className="relative inline-block -mt-12 mb-3">
        <Avatar
          className="size-24 rounded-full border-4"
          style={{ borderColor: 'var(--overlay)', background: accentColor + '22' }}
        >
          {avatarUrl && (
            <AvatarImage src={avatarUrl} alt={displayName} referrerPolicy="no-referrer" />
          )}
          <AvatarFallback
            className="text-2xl bg-transparent"
            style={{ color: accentColor, fontFamily }}
          >
            {isBot ? '🤖' : displayName.slice(0, 1).toUpperCase()}
          </AvatarFallback>
        </Avatar>
        {effectiveStatus && !isBot && (
          <span className="absolute bottom-0.5 right-0.5">
            <StatusDot status={effectiveStatus} size={18} bordered borderColor="var(--overlay)" />
          </span>
        )}
      </div>

      {/* Name + pronouns + emoji */}
      <div className="flex items-baseline gap-2 flex-wrap mb-1">
        <h2
          className="text-2xl m-0 leading-tight wrap-break-word"
          style={{ fontFamily, color: accentColor }}
        >
          {displayName}
        </h2>
        {isBot && (
          <span
            className="text-[9px] font-bold uppercase tracking-widest px-2 py-0.5 rounded-full self-center"
            style={{ background: 'var(--accent)', color: 'var(--text-inv)' }}
          >
            BOT
          </span>
        )}
        {pronouns && (
          <span
            className="text-[10px] font-mono uppercase tracking-wider px-2 py-0.5 rounded-full border self-center"
            style={{
              borderColor: `color-mix(in srgb, ${accentColor} 40%, transparent)`,
              color:        accentColor,
              background:   `color-mix(in srgb, ${accentColor} 10%, transparent)`,
            }}
          >
            {pronouns}
          </span>
        )}
        {statusEmoji && <span className="text-xl leading-none self-center">{statusEmoji}</span>}
      </div>

      {/* Handle + status */}
      <div className="flex items-center gap-2 flex-wrap text-xs">
        <span className="font-mono text-(--text-3) tracking-wide">@{username}</span>
        {effectiveStatus && !isBot && (
          <>
            <span className="text-(--text-3)">·</span>
            <span className="inline-flex items-center gap-1.5 text-(--text-2)">
              <StatusDot status={effectiveStatus} size={7} />
              {STATUS_META[effectiveStatus].label}
            </span>
          </>
        )}
      </div>

      {/* Coordenada — só renderiza pra própria pessoa */}
      {coordinate && <CoordinateChip coord={coordinate} accentColor={accentColor} />}
    </>
  )
}

/**
 * Chip da coordenada Astra. Clica → copia pra clipboard + toast.
 */
function CoordinateChip({ coord, accentColor }: { coord: string; accentColor: string }) {
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(coord)
      const { toast } = await import('@/components/ui/sonner')
      toast.success('Coordenada copiada.')
    } catch { /* ignora — clipboard pode estar bloqueado em iframe */ }
  }
  return (
    <button
      type="button"
      onClick={copy}
      className="mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-marg font-mono tracking-wider cursor-pointer hover:bg-(--raised)/40 transition-colors"
      style={{
        borderColor: `color-mix(in srgb, ${accentColor} 30%, transparent)`,
        color:        accentColor,
      }}
      title="Clica pra copiar"
    >
      <span aria-hidden>✦</span>
      <span>{coord}</span>
    </button>
  )
}
