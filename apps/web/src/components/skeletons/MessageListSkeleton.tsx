/**
 * MessageListSkeleton — mensagens fake com avatar + nome + 1-3 linhas.
 * Larguras variam pra parecer mais natural (não enfileira retângulos iguais).
 * Estrutura column-reverse pra empilhar de baixo pra cima (como chat real).
 */
import { Skeleton } from '@/components/ui/skeleton'

interface MessageListSkeletonProps {
  count?: number
}

const LINE_PATTERNS: Array<string[]> = [
  ['w-2/3'],
  ['w-4/5', 'w-1/2'],
  ['w-2/5'],
  ['w-3/4', 'w-3/5', 'w-1/3'],
  ['w-3/5'],
  ['w-11/12', 'w-2/5'],
  ['w-1/2'],
  ['w-2/3', 'w-1/3'],
]

const NAME_WIDTHS = ['w-24', 'w-28', 'w-20', 'w-32', 'w-22', 'w-26']

export function MessageListSkeleton({ count = 8 }: MessageListSkeletonProps) {
  return (
    <div className="flex-1 overflow-hidden flex flex-col-reverse px-3 pb-2">
      {Array.from({ length: count }).map((_, i) => {
        const lineWidths = LINE_PATTERNS[i % LINE_PATTERNS.length]
        return (
          <div key={i} className="flex gap-3 px-3 pt-2 pb-0.5 mt-1.5">
            <Skeleton className="size-9 rounded-full border-0 shrink-0" />
            <div className="flex-1 flex flex-col gap-1.5 min-w-0">
              <div className="flex items-center gap-2">
                <Skeleton className={`h-3 rounded-sm border-0 ${NAME_WIDTHS[i % NAME_WIDTHS.length]}`} />
                <Skeleton className="h-2 w-12 rounded-sm border-0" />
              </div>
              {lineWidths.map((w, j) => (
                <Skeleton key={j} className={`h-2.5 rounded-sm border-0 ${w}`} />
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}
