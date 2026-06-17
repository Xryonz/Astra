/**
 * MobileAvatarTrigger — hamburguer no header mobile (estilo Discord).
 * Tap = abre a sidebar (drawer com servers + canais). Visível só em mobile
 * (md:hidden). A foto do user agora vive só na barra de perfil de baixo.
 */
import { useTranslation } from 'react-i18next'
import { Menu } from 'lucide-react'
import { useUIStore } from '@/store/uiStore'
import { cn } from '@/lib/utils'

interface Props {
  className?: string
}

export default function MobileAvatarTrigger({ className }: Props) {
  const { t }       = useTranslation()
  const openSidebar = useUIStore((s) => s.openMobileSidebar)
  return (
    <button
      type="button"
      onClick={openSidebar}
      aria-label={t('sidebar.openConstellations')}
      className={cn(
        'md:hidden shrink-0 size-11 grid place-items-center cursor-pointer rounded-full',
        'text-(--text-2) hover:text-(--accent) transition-[color,transform] active:scale-95',
        className,
      )}
    >
      <Menu className="size-5" />
    </button>
  )
}
