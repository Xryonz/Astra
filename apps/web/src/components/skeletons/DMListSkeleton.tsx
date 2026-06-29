
import { Skeleton } from '@/components/ui/skeleton'

const widthClasses = ['w-3/5', 'w-2/3', 'w-1/2', 'w-3/4', 'w-2/5', 'w-3/5']
const previewClasses = ['w-2/5', 'w-1/2', 'w-1/3', 'w-3/5', 'w-2/5', 'w-1/2']

export function DMListSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="flex flex-col">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="flex items-start gap-3 px-3 py-2.5 border-l-2 border-transparent"
          style={{ animation: `fadeLeft 0.28s var(--ease-spring) ${i * 0.04}s both` }}
        >
          <Skeleton className="size-9 rounded-full border-0" />
          <div className="flex-1 flex flex-col gap-1.5 min-w-0">
            <div className="flex items-baseline justify-between gap-2">
              <Skeleton className={`h-3 rounded-sm border-0 ${widthClasses[i % widthClasses.length]}`} />
              <Skeleton className="h-2 w-7 rounded-sm border-0" />
            </div>
            <Skeleton className={`h-2 rounded-sm border-0 ${previewClasses[i % previewClasses.length]}`} />
          </div>
        </div>
      ))}
    </div>
  )
}
