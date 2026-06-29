
import { Skeleton } from '@/components/ui/skeleton'
import { SidebarSkeleton } from './SidebarSkeleton'
import { MessageListSkeleton } from './MessageListSkeleton'

export function AppShellSkeleton() {
  return (
    <div className="flex h-screen-safe overflow-hidden font-(family-name:--font-body)">
      <SidebarSkeleton />

      <div className="flex-1 flex flex-col min-w-0">
        {}
        <div className="h-13 border-b border-(--border) px-5 flex items-center gap-2.5 bg-(--base) shrink-0">
          <Skeleton className="size-7 rounded-md border-0" />
          <Skeleton className="h-3.5 w-36 rounded-sm border-0" />
        </div>

        <MessageListSkeleton />

        {}
        <div className="px-4 pb-3.5 pt-2">
          <Skeleton className="h-13 w-full rounded-2xl border-0" />
        </div>
      </div>
    </div>
  )
}
