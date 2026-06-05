/**
 * VoiceMessage — player de áudio renderizado em MessageItem.
 *
 * O recorder (start/stop/pause) virou hook em `useAudioRecorder` + UI em
 * `RecordingDisplay`. Este arquivo agora hospeda só o player.
 *
 * Nome do arquivo mantido (VoiceRecorder.tsx) por convivência — os imports
 * existentes de `VoiceMessage` continuam funcionando.
 */
import { useRef, useState } from 'react'
import { resolveApiUrl } from '@/lib/api'
import { cn } from '@/lib/utils'

export function VoiceMessage({ url, duration }: { url: string; duration?: number }) {
  const [playing, setPlaying] = useState(false)
  const [pos,     setPos]     = useState(0)
  const [total,   setTotal]   = useState(duration ?? 0)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  // Barras decorativas determinísticas pela URL — parecem estáveis e
  // não rerenderizam a cada tick.
  const bars = (() => {
    let seed = 0
    for (const c of url) seed = (seed * 31 + c.charCodeAt(0)) >>> 0
    return Array.from({ length: 32 }, () => {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff
      return 0.3 + (seed % 70) / 100
    })
  })()

  const toggle = () => {
    if (!audioRef.current) return
    if (playing) audioRef.current.pause()
    else         audioRef.current.play().catch(() => {})
  }

  const fmt = (s: number) => {
    const m = Math.floor(s / 60)
    const r = Math.floor(s % 60)
    return `${m}:${String(r).padStart(2, '0')}`
  }

  const progressIdx = total > 0 ? Math.floor((pos / total) * bars.length) : 0

  return (
    <div className="my-1 inline-flex items-center gap-3 px-3 py-2 rounded-xl border border-(--border) bg-(--raised)/40 max-w-90">
      <button
        onClick={toggle}
        className="shrink-0 size-9 grid place-items-center rounded-full border border-(--accent) text-(--accent) hover:bg-(--accent) hover:text-(--accent-foreground) transition-colors"
        aria-label={playing ? 'Pausar' : 'Reproduzir'}
      >
        {playing ? (
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" /><rect x="14" y="4" width="4" height="16" /></svg>
        ) : (
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
        )}
      </button>

      <div className="flex items-end gap-px h-6 flex-1">
        {bars.map((v, i) => (
          <div
            key={i}
            className={cn('w-0.5 transition-colors', i < progressIdx ? 'bg-(--accent)' : 'bg-(--text-3)/40')}
            style={{ height: `${v * 100}%` }}
          />
        ))}
      </div>

      <span className="text-[11px] tabular-nums text-(--text-3) shrink-0">
        {fmt(playing || pos > 0 ? pos : total)}
      </span>

      <audio
        ref={audioRef}
        src={resolveApiUrl(url)}
        preload="metadata"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => { setPlaying(false); setPos(0) }}
        onLoadedMetadata={(e) => { if (!duration) setTotal((e.target as HTMLAudioElement).duration) }}
        onTimeUpdate={(e) => setPos((e.target as HTMLAudioElement).currentTime)}
      />
    </div>
  )
}
