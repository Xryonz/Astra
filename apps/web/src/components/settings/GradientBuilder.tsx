/**
 * GradientBuilder — pickas duas cores + ângulo, gera linear-gradient.
 *
 * Vibe Nitro: user escolhe início + fim, vê preview em tempo real.
 * Output: `linear-gradient(${angle}deg, ${c1}, ${c2})` — string CSS válida,
 * compatível com BANNER_COLOR_RE do schema (até 4 stops).
 *
 * Parsing: aceita string entrada, se for gradient extrai angle + stops;
 * se for hex puro, usa como c1 e duplica em c2.
 */
import { useEffect, useState } from 'react'
import { HexColorPicker } from 'react-colorful'
import { motion, AnimatePresence } from 'motion/react'
import { ChevronDown } from 'lucide-react'
import { Slider } from '@/components/ui/slider'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface Props {
  value:    string
  onChange: (v: string) => void
  /** Altura do preview em px. Default 56. */
  previewH?: number
}

interface ParsedGradient {
  angle: number
  c1:    string
  c2:    string
  c3?:   string
}

// Regex pra extrair angle + cores de "linear-gradient(135deg, #abc, #def)"
const GRADIENT_RE = /^linear-gradient\(\s*(-?\d{1,3})deg\s*,\s*(#[0-9a-fA-F]{6})\s*,\s*(#[0-9a-fA-F]{6})(?:\s*,\s*(#[0-9a-fA-F]{6}))?\s*\)$/

function parse(value: string): ParsedGradient {
  const m = value.match(GRADIENT_RE)
  if (m) {
    return {
      angle: parseInt(m[1], 10),
      c1:    m[2].toLowerCase(),
      c2:    m[3].toLowerCase(),
      c3:    m[4]?.toLowerCase(),
    }
  }
  // Hex puro → usa como cor única (duplica pra valer como gradient)
  if (/^#[0-9a-fA-F]{6}$/.test(value)) {
    return { angle: 135, c1: value.toLowerCase(), c2: value.toLowerCase() }
  }
  // Default
  return { angle: 135, c1: '#c9a96e', c2: '#7c6fc4' }
}

function build(p: ParsedGradient): string {
  const stops = p.c3 ? `${p.c1}, ${p.c2}, ${p.c3}` : `${p.c1}, ${p.c2}`
  return `linear-gradient(${p.angle}deg, ${stops})`
}

export function GradientBuilder({ value, onChange, previewH = 56 }: Props) {
  const [state,    setState]    = useState<ParsedGradient>(() => parse(value))
  const [activeStop, setActiveStop] = useState<'c1' | 'c2' | 'c3'>('c1')
  const [pickerOpen, setPickerOpen] = useState(false)

  // Re-parse quando value externo muda (ex: trocou preset)
  useEffect(() => {
    setState(parse(value))
  }, [value])

  const update = (patch: Partial<ParsedGradient>) => {
    const next = { ...state, ...patch }
    setState(next)
    onChange(build(next))
  }

  const activeColor = state[activeStop] ?? '#c9a96e'

  return (
    <div className="flex flex-col gap-3">
      {/* Preview */}
      <motion.div
        layout
        className="w-full rounded-xl border border-(--border-mid) shadow-inner"
        style={{ height: previewH, background: build(state) }}
      />

      {/* Color stops */}
      <div className="flex items-center gap-2 flex-wrap">
        <ColorStopButton
          label="Início"
          color={state.c1}
          active={activeStop === 'c1'}
          onClick={() => { setActiveStop('c1'); setPickerOpen(true) }}
        />
        <span className="text-(--text-3) font-mono">→</span>
        <ColorStopButton
          label="Fim"
          color={state.c2}
          active={activeStop === 'c2'}
          onClick={() => { setActiveStop('c2'); setPickerOpen(true) }}
        />
        {state.c3 ? (
          <>
            <span className="text-(--text-3) font-mono">→</span>
            <ColorStopButton
              label="Extra"
              color={state.c3}
              active={activeStop === 'c3'}
              onClick={() => { setActiveStop('c3'); setPickerOpen(true) }}
            />
            <button
              type="button"
              onClick={() => update({ c3: undefined })}
              className="text-[11px] text-(--text-3) hover:text-(--danger) underline cursor-pointer"
            >
              remover extra
            </button>
          </>
        ) : (
          <button
            type="button"
            onClick={() => update({ c3: '#a3422a' })}
            className="text-[11px] text-(--text-3) hover:text-(--accent) underline cursor-pointer"
          >
            + adicionar 3ª cor
          </button>
        )}
      </div>

      {/* Angle slider */}
      <div className="flex items-center gap-3">
        <span className="text-[11px] font-mono text-(--text-3) shrink-0 w-14">Ângulo</span>
        <Slider
          value={[state.angle]}
          onValueChange={([v]) => update({ angle: v })}
          min={0}
          max={360}
          step={1}
          className="flex-1"
        />
        <span className="text-[11px] font-mono text-(--text-2) tabular-nums shrink-0 w-10 text-right">{state.angle}°</span>
      </div>

      {/* Picker collapsible */}
      <AnimatePresence initial={false}>
        {pickerOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
            className="overflow-hidden"
          >
            <div className="p-3 rounded-xl border border-(--border-mid) bg-(--raised)/40 flex flex-col gap-3 mt-1">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-mono text-(--text-3) uppercase tracking-wider">
                  Editando: <span className="text-(--accent)">{activeStop === 'c1' ? 'Início' : activeStop === 'c2' ? 'Fim' : 'Extra'}</span>
                </span>
                <button
                  type="button"
                  onClick={() => setPickerOpen(false)}
                  className="text-(--text-3) hover:text-(--accent) transition-colors"
                  title="Fechar"
                >
                  <ChevronDown className="size-3.5" />
                </button>
              </div>
              <HexColorPicker
                color={activeColor}
                onChange={(c) => update({ [activeStop]: c.toLowerCase() } as Partial<ParsedGradient>)}
                style={{ width: '100%', maxWidth: 220 }}
              />
              <Input
                value={activeColor}
                onChange={(e) => {
                  const v = e.target.value
                  if (/^#[0-9a-fA-F]{6}$/.test(v)) update({ [activeStop]: v.toLowerCase() } as Partial<ParsedGradient>)
                }}
                placeholder="#RRGGBB"
                className="font-mono text-xs"
                maxLength={7}
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function ColorStopButton({ label, color, active, onClick }: {
  label: string; color: string; active: boolean; onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex items-center gap-2 rounded-lg border px-2 py-1.5 transition-all',
        active
          ? 'border-(--accent) bg-(--accent)/5 ring-2 ring-(--accent)/20'
          : 'border-(--border-mid) hover:border-(--accent)',
      )}
    >
      <span
        aria-hidden
        className="size-6 rounded-md border border-(--border) shrink-0"
        style={{ background: color }}
      />
      <div className="flex flex-col items-start leading-tight">
        <span className="text-[10px] font-mono text-(--text-3) uppercase tracking-wider">{label}</span>
        <span className="text-[11px] font-mono text-(--text-2)">{color}</span>
      </div>
    </button>
  )
}
