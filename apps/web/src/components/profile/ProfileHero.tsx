import { useTranslation } from 'react-i18next'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import StatusDot, { STATUS_LABEL_KEY, type UserStatus } from '@/components/StatusDot'
import { FONT_FAMILY, type DisplayFont } from './profileFonts'

interface Props {
  avatarUrl?:        string | null
  displayName:       string
  username:          string

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
  const { t } = useTranslation()
  const fontFamily = FONT_FAMILY[displayFont]

  return (
    <>
      {}
      <div className="relative inline-block -mt-12 mb-3">
        {coordinate && <OrbitRing color={accentColor} />}
        <Avatar
          className="size-24 rounded-full border-4 relative"
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
          <span className="absolute bottom-0.5 right-0.5 z-10">
            <StatusDot status={effectiveStatus} size={18} bordered borderColor="var(--overlay)" />
          </span>
        )}
      </div>

      {}
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

      {}
      <div className="flex items-center gap-2 flex-wrap text-xs">
        <span className="font-mono text-(--text-3) tracking-wide">@{username}</span>
        {effectiveStatus && !isBot && (
          <>
            <span className="text-(--text-3)">·</span>
            <span className="inline-flex items-center gap-1.5 text-(--text-2)">
              <StatusDot status={effectiveStatus} size={7} />
              {t(STATUS_LABEL_KEY[effectiveStatus])}
            </span>
          </>
        )}
      </div>

      {}
      {coordinate && <CoordinateChip coord={coordinate} accentColor={accentColor} />}
    </>
  )
}

function OrbitRing({ color }: { color: string }) {

  const size = 116
  const r    = (size - 4) / 2
  return (
    <svg
      aria-hidden
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      className="absolute inset-0 m-auto astra-orbit pointer-events-none"
      style={{ left: -10, top: -10 }}
    >
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={color}
        strokeWidth={1}
        strokeDasharray="2 6"
        opacity={0.55}
      />
    </svg>
  )
}

function CoordinateChip({ coord, accentColor }: { coord: string; accentColor: string }) {
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(coord)
      const { toast } = await import('@/components/ui/sonner')
      toast.success('Coordenada copiada.')
    } catch { }
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
