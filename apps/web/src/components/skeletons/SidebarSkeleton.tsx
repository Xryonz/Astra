/**
 * SidebarSkeleton — coluna de servidores + coluna de canais.
 * 5 server bubbles + 4 channel rows como placeholder.
 */
import { Skeleton } from '@/components/ui/skeleton'

const channelWidths = ['w-28', 'w-24', 'w-20', 'w-16']
const channelOpacity = ['opacity-100', 'opacity-90', 'opacity-80', 'opacity-70']
const serverOpacity = ['opacity-100', 'opacity-90', 'opacity-75', 'opacity-60', 'opacity-50']

export function SidebarSkeleton() {
  return (
    <>
      {/* Coluna de servidores */}
      <div className="w-18 bg-(--base) border-r border-(--border) flex flex-col items-center py-3.5 gap-2.5 shrink-0">
        <Skeleton className="size-10 rounded-full border-0" />
        <div className="w-7 h-px bg-(--border) my-1" />
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton
            key={i}
            className={`size-11 rounded-2xl border-0 ${serverOpacity[i]}`}
          />
        ))}
      </div>

      {/* Coluna de canais */}
      <div className="w-58 bg-(--base) border-r border-(--border) flex flex-col shrink-0">
        {/* Cabeçalho do servidor ativo */}
        <div className="h-13 border-b border-(--border) px-4 flex items-center">
          <Skeleton className="h-3.5 w-30 rounded-sm border-0" />
        </div>

        {/* Lista de canais */}
        <div className="px-2.5 py-3.5 flex flex-col gap-2">
          <Skeleton className="h-2.5 w-17 rounded-sm border-0 mb-1 ml-2" />
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex items-center gap-2 px-2 py-1">
              <Skeleton className="size-3.5 rounded-sm border-0" />
              <Skeleton className={`h-3 rounded-sm border-0 ${channelWidths[i]} ${channelOpacity[i]}`} />
            </div>
          ))}
        </div>

        {/* Rodapé com o user */}
        <div className="mt-auto px-3.5 py-2.5 border-t border-(--border) flex items-center gap-2.5">
          <Skeleton className="size-8 rounded-full border-0" />
          <div className="flex-1 flex flex-col gap-1.5">
            <Skeleton className="h-2.5 w-3/5 rounded-sm border-0" />
            <Skeleton className="h-2 w-2/5 rounded-sm border-0" />
          </div>
        </div>
      </div>
    </>
  )
}
