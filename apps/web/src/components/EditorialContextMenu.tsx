/**
 * EditorialContextMenu — wrapper de alto-nível sobre ContextMenu shadcn.
 *
 * Aceita um array de `items` (com suporte a separator/submenu/destructive)
 * e renderiza tudo. Trigger é qualquer children — o usuário right-click neles.
 *
 * Antes: tínhamos `ServerContextMenu` (manualmente posicionado em x,y).
 * Agora: Radix já trata posicionamento, flip, escape, outside-click.
 *
 * Uso:
 *   <EditorialContextMenu items={[
 *     { kind: 'item', icon: <Pin />, label: 'Fixar', onSelect: () => {} },
 *     { kind: 'separator' },
 *     { kind: 'item', icon: <Trash />, label: 'Excluir', destructive: true, onSelect: () => {} },
 *   ]}>
 *     <div>conteúdo clicável com botão direito</div>
 *   </EditorialContextMenu>
 */
import * as React from 'react'
import {
  ContextMenu,
  ContextMenuTrigger,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuLabel,
  ContextMenuSeparator,
  ContextMenuShortcut,
  ContextMenuSub,
  ContextMenuSubTrigger,
  ContextMenuSubContent,
} from '@/components/ui/context-menu'

const LONG_PRESS_MS    = 480
const MOVE_THRESHOLD   = 8

export type EditorialMenuItem =
  | {
      kind:        'item'
      icon?:       React.ReactNode
      label:       string
      shortcut?:   string
      destructive?: boolean
      disabled?:   boolean
      onSelect:    () => void
    }
  | {
      kind:  'separator'
    }
  | {
      kind:  'label'
      label: string
    }
  | {
      kind:  'sub'
      icon?: React.ReactNode
      label: string
      items: EditorialMenuItem[]
    }

interface Props {
  items:    EditorialMenuItem[]
  children: React.ReactNode
  /** Pra desabilitar o menu inteiro (ex: sem permissão) */
  disabled?: boolean
  /**
   * Long-press → contextmenu bridge pra mobile.
   * Default: true. Passe `false` quando o consumer já tem long-press próprio
   * (ex: MessageItem abre action sheet customizado) — evita disparar 2x.
   */
  mobileBridge?: boolean
}

function renderItems(items: EditorialMenuItem[]): React.ReactNode {
  return items.map((it, i) => {
    if (it.kind === 'separator') return <ContextMenuSeparator key={`sep-${i}`} />
    if (it.kind === 'label')     return <ContextMenuLabel    key={`lbl-${i}`}>{it.label}</ContextMenuLabel>
    if (it.kind === 'sub') {
      return (
        <ContextMenuSub key={`sub-${i}`}>
          <ContextMenuSubTrigger>
            {it.icon && <span className="shrink-0">{it.icon}</span>}
            <span>{it.label}</span>
          </ContextMenuSubTrigger>
          <ContextMenuSubContent>{renderItems(it.items)}</ContextMenuSubContent>
        </ContextMenuSub>
      )
    }
    return (
      <ContextMenuItem
        key={`it-${i}`}
        destructive={it.destructive}
        disabled={it.disabled}
        onSelect={(e) => {
          if (it.disabled) { e.preventDefault(); return }
          it.onSelect()
        }}
      >
        {it.icon && <span className="shrink-0">{it.icon}</span>}
        <span className="flex-1">{it.label}</span>
        {it.shortcut && <ContextMenuShortcut>{it.shortcut}</ContextMenuShortcut>}
      </ContextMenuItem>
    )
  })
}

export function EditorialContextMenu({ items, children, disabled, mobileBridge = true }: Props) {
  if (disabled || items.length === 0) return <>{children}</>
  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>
        {mobileBridge ? <TouchLongPressBridge>{children}</TouchLongPressBridge> : (children as React.ReactElement)}
      </ContextMenuTrigger>
      <ContextMenuContent>{renderItems(items)}</ContextMenuContent>
    </ContextMenu>
  )
}

/**
 * TouchLongPressBridge — segura touch por LONG_PRESS_MS e dispara um
 * `contextmenu` sintético na posição do toque. Radix ContextMenu (que ouve
 * `contextmenu` nativo) então abre o menu posicionado corretamente.
 *
 * Por que: Radix ContextMenu não suporta touch nativamente, e iOS/Android
 * só disparam `contextmenu` em alguns casos (não em <button>, p.ex.).
 * Esse bridge garante paridade desktop/mobile sem mexer em cada consumer.
 */
const TouchLongPressBridge = React.forwardRef<HTMLElement, { children: React.ReactNode }>(
  function TouchLongPressBridge({ children, ...rest }, ref) {
    const timer    = React.useRef<ReturnType<typeof setTimeout> | null>(null)
    const startPos = React.useRef<{ x: number; y: number } | null>(null)
    const fired    = React.useRef(false)

    const clear = React.useCallback(() => {
      if (timer.current) { clearTimeout(timer.current); timer.current = null }
      startPos.current = null
    }, [])

    const handleTouchStart = (e: React.TouchEvent) => {
      fired.current = false
      const t = e.touches[0]
      if (!t) return
      startPos.current = { x: t.clientX, y: t.clientY }
      timer.current = setTimeout(() => {
        fired.current = true
        if ('vibrate' in navigator) try { (navigator as any).vibrate?.(8) } catch {}
        const target = e.target as HTMLElement
        target.dispatchEvent(
          new MouseEvent('contextmenu', {
            bubbles: true, cancelable: true,
            clientX: startPos.current?.x ?? 0,
            clientY: startPos.current?.y ?? 0,
            button: 2,
          }),
        )
      }, LONG_PRESS_MS)
    }
    const handleTouchMove = (e: React.TouchEvent) => {
      if (!startPos.current) return
      const t = e.touches[0]
      if (!t) return
      const dx = Math.abs(t.clientX - startPos.current.x)
      const dy = Math.abs(t.clientY - startPos.current.y)
      if (dx > MOVE_THRESHOLD || dy > MOVE_THRESHOLD) clear()
    }
    const handleTouchEnd = () => clear()
    const handleContextMenu = (e: React.MouseEvent) => {
      // Se o long-press já abriu o menu (dispatch sintético), não deixar o nativo abrir de novo.
      if (fired.current) {
        e.preventDefault()
        e.stopPropagation()
      }
    }

    if (React.isValidElement(children)) {
      return React.cloneElement(children as React.ReactElement<any>, {
        ref,
        onTouchStart:  composeHandler((children.props as any).onTouchStart,  handleTouchStart),
        onTouchMove:   composeHandler((children.props as any).onTouchMove,   handleTouchMove),
        onTouchEnd:    composeHandler((children.props as any).onTouchEnd,    handleTouchEnd),
        onTouchCancel: composeHandler((children.props as any).onTouchCancel, handleTouchEnd),
        onContextMenu: composeHandler((children.props as any).onContextMenu, handleContextMenu),
        ...rest,
      })
    }
    return (
      <span
        ref={ref as React.Ref<HTMLSpanElement>}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={handleTouchEnd}
        onContextMenu={handleContextMenu}
      >
        {children}
      </span>
    )
  },
)

function composeHandler<E>(a: ((e: E) => void) | undefined, b: (e: E) => void) {
  return (e: E) => { try { a?.(e) } finally { b(e) } }
}
