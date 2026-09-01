import { create } from 'zustand'
import { useTranslation } from 'react-i18next'
import { Monitor, Smartphone, ArrowUpRight } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'

const RELEASES = 'https://github.com/Xryonz/Astra/releases/latest'

interface DownloadAppState {
  open:      boolean
  abrir:     () => void
  definir:   (v: boolean) => void
}

export const useDownloadApp = create<DownloadAppState>((set) => ({
  open:    false,
  abrir:   () => set({ open: true }),
  definir: (v) => set({ open: v }),
}))

function Plataforma({
  icon, nome, detalhe,
}: {
  icon:    React.ReactNode
  nome:    string
  detalhe: string
}) {
  return (
    <a
      href={RELEASES}
      target="_blank"
      rel="noreferrer noopener"
      className="group flex items-center gap-3 p-3 bg-(--raised) border border-(--border)
                 hover:bg-(--hover) hover:border-(--accent)/50
                 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-(--accent)
                 transition-colors"
    >
      <span className="text-(--text-2) group-hover:text-(--accent) transition-colors">{icon}</span>
      <span className="flex flex-col min-w-0">
        <span className="text-sm text-(--text-1)">{nome}</span>
        <span className="text-xs text-(--text-3)">{detalhe}</span>
      </span>
      <ArrowUpRight className="size-3.5 ml-auto shrink-0 text-(--text-3) group-hover:text-(--accent) transition-colors" />
    </a>
  )
}

export function DownloadAppDialog() {
  const { t }   = useTranslation()
  const open    = useDownloadApp((s) => s.open)
  const definir = useDownloadApp((s) => s.definir)

  return (
    <Dialog open={open} onOpenChange={definir}>
      <DialogContent className="max-w-105!">
        <DialogHeader>
          <DialogTitle>{t('download.title')}</DialogTitle>
          <DialogDescription>{t('download.desc')}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-2">
          <Plataforma
            icon={<Monitor className="size-4" />}
            nome={t('download.windows')}
            detalhe={t('download.windowsSub')}
          />
          <Plataforma
            icon={<Smartphone className="size-4" />}
            nome={t('download.android')}
            detalhe={t('download.androidSub')}
          />
        </div>

        <p className="mt-4 text-xs text-(--text-3)">{t('download.note')}</p>
      </DialogContent>
    </Dialog>
  )
}
