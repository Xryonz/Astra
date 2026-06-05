/**
 * RecordingDisplay — UI no lugar do textarea durante gravação de áudio.
 * Mostra waveform + timer. Sem botões (controles ficam no composer row).
 * Reaproveitado por MessageInput e DMInput.
 */
import { cn } from '@/lib/utils'

interface Props {
  state:     'idle' | 'recording' | 'paused' | 'uploading'
  bars:      number[]
  elapsedMs: number
  error:     string | null
}

export function RecordingDisplay({ state, bars, elapsedMs, error }: Props) {
  const secs = Math.floor(elapsedMs / 1000)
  const time = `${String(Math.floor(secs / 60)).padStart(2, '0')}:${String(secs % 60).padStart(2, '0')}`

  if (state === 'uploading') {
    return (
      <div className="flex-1 flex items-center gap-2 text-xs text-(--text-3) px-2">
        <span className="size-3 border-2 border-(--border-mid) border-t-(--accent) rounded-full animate-spin" />
        Enviando áudio…
      </div>
    )
  }

  return (
    <div className="flex-1 flex items-center gap-2 px-1 min-w-0">
      <span
        className={cn(
          'size-2 rounded-full shrink-0',
          state === 'paused' ? 'bg-(--text-3)' : 'bg-(--danger) animate-pulse',
        )}
        aria-hidden
      />
      <div className="flex items-center gap-px h-4 flex-1 max-w-50">
        {bars.map((v, i) => (
          <div
            key={i}
            className="w-0.5 bg-(--accent) transition-all"
            style={{ height: `${Math.max(15, v * 100)}%`, opacity: state === 'paused' ? 0.4 : 1 }}
          />
        ))}
      </div>
      <span className="text-xs tabular-nums text-(--accent) shrink-0">{time}</span>
      {error && <span className="text-[10px] text-(--danger) shrink-0 truncate">{error}</span>}
    </div>
  )
}
