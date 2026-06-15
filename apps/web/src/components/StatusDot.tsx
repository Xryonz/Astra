import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'

export type UserStatus = 'ONLINE' | 'IDLE' | 'DND' | 'INVISIBLE' | 'OFFLINE'

export const STATUS_META: Record<UserStatus, { label: string; color: string; description: string }> = {
  ONLINE:    { label: 'Online',       color: '#22c55e', description: 'Disponível para conversar' },
  IDLE:      { label: 'Ausente',      color: '#f59e0b', description: 'Talvez não responda agora' },
  DND:       { label: 'Não perturbe', color: '#ef4444', description: 'Não mostra notificações' },
  INVISIBLE: { label: 'Invisível',    color: '#6b7280', description: 'Aparece offline pros outros' },
  OFFLINE:   { label: 'Offline',      color: '#6b7280', description: 'Sem conexão' },
}

/** i18n key por status — label traduzido (STATUS_META mantém só a cor canônica). */
export const STATUS_LABEL_KEY: Record<UserStatus, string> = {
  ONLINE:    'status.online',
  IDLE:      'status.idle',
  DND:       'status.dnd',
  INVISIBLE: 'status.invisible',
  OFFLINE:   'status.offline',
}

interface StatusDotProps {
  status:       UserStatus
  size?:        number
  cutoutColor?: string
  bordered?:    boolean
  borderColor?: string
  className?:   string
}

/**
 * Indicador de status — SVG inline puro.
 *  ONLINE  → bolinha cheia verde
 *  IDLE    → lua crescente (amarelo com recorte da cor do fundo)
 *  DND     → vermelho com traço no meio
 *  INVISIBLE/OFFLINE → anel oco cinza
 */
export default function StatusDot({
  status,
  size = 12,
  cutoutColor,
  bordered = false,
  borderColor = '#0a0a0a',
  className,
}: StatusDotProps) {
  const { t } = useTranslation()
  const meta = STATUS_META[status]
  const label = t(STATUS_LABEL_KEY[status])
  const cutout = cutoutColor ?? (bordered ? borderColor : '#0a0a0a')
  const ringPx = Math.max(1.5, size * 0.22)

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={label}
      className={cn('inline-block shrink-0 align-middle', className)}
      style={{
        display: 'inline-block',
        boxShadow: bordered ? `0 0 0 ${ringPx}px ${borderColor}` : undefined,
        borderRadius: '50%',
      }}
    >
      <title>{label}</title>

      {status === 'ONLINE' && (
        <circle cx="8" cy="8" r="8" fill={meta.color} />
      )}

      {status === 'IDLE' && (
        <>
          <circle cx="8" cy="8" r="8" fill={meta.color} />
          <circle cx="5.2" cy="5.2" r="5.2" fill={cutout} />
        </>
      )}

      {status === 'DND' && (
        <>
          <circle cx="8" cy="8" r="8" fill={meta.color} />
          <rect x="2.8" y="6.4" width="10.4" height="3.2" rx="0.6" fill={cutout} />
        </>
      )}

      {(status === 'OFFLINE' || status === 'INVISIBLE') && (
        <circle
          cx="8"
          cy="8"
          r="6.2"
          fill="none"
          stroke={meta.color}
          strokeWidth="2.6"
        />
      )}
    </svg>
  )
}
