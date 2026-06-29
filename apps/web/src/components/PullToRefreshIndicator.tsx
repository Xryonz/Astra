
import { Sparkles } from 'lucide-react'

export function PullToRefreshIndicator({ pull, refreshing }: { pull: number; refreshing: boolean }) {
  if (pull <= 0 && !refreshing) return null
  const progress = Math.min(1, pull / 64)
  return (
    <div
      className="absolute top-0 inset-x-0 flex items-center justify-center pointer-events-none z-10"
      style={{ height: refreshing ? 40 : pull, opacity: refreshing ? 1 : progress }}
    >
      <Sparkles
        className={`size-5 text-(--accent) ${refreshing ? 'animate-spin' : ''}`}
        style={refreshing ? undefined : { transform: `rotate(${progress * 270}deg)` }}
      />
    </div>
  )
}
