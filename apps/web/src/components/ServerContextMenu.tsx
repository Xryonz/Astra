import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'

export interface ContextMenuItem {
  label:     string
  icon:      string
  danger?:   boolean
  disabled?: boolean
  onClick:   () => void
}

interface ServerContextMenuProps {
  x:       number
  y:       number
  items:   ContextMenuItem[]
  onClose: () => void
}

/**
 * Menu de contexto (right-click) ancorado em (x, y).
 *
 * Usa DropdownMenu do ShadCN com um trigger invisível posicionado no
 * ponto do clique. Radix cuida de:
 *   - posicionamento (flip se sair da tela)
 *   - fechar em outside click
 *   - fechar em Escape
 *   - foco/aria
 */
export default function ServerContextMenu({ x, y, items, onClose }: ServerContextMenuProps) {
  return (
    <DropdownMenu open onOpenChange={(o: boolean) => !o && onClose()} modal={false}>
      <DropdownMenuTrigger asChild>
        <button
          aria-hidden
          tabIndex={-1}
          style={{
            position:   'fixed',
            top:        y,
            left:       x,
            width:      0,
            height:     0,
            opacity:    0,
            pointerEvents: 'none',
          }}
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        side="bottom"
        sideOffset={2}
        onContextMenu={(e: React.MouseEvent) => e.preventDefault()}
      >
        {items.map((item, i) => (
          <DropdownMenuItem
            key={i}
            disabled={item.disabled}
            destructive={item.danger}
            onSelect={() => {
              if (!item.disabled) item.onClick()
            }}
          >
            <span className="text-base shrink-0">{item.icon}</span>
            <span>{item.label}</span>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
