import { Moon } from 'lucide-react'

interface UmbraLogoProps {
  size?: number
  style?: React.CSSProperties
  animated?: boolean
}

/**
 * Logo do Umbra — Moon (Lucide) tematizada via `var(--accent)`.
 * Acompanha automaticamente a cor de acento escolhida em Settings.
 *
 * `color: var(--accent)` no wrapper define `currentColor`, e Lucide
 * usa `currentColor` por padrão no stroke. Glow via filter (CSS vars
 * funcionam em filter, ao contrário de stroke= em SVG).
 */
export default function UmbraLogo({ size = 40, style, animated = true }: UmbraLogoProps) {
  return (
    <div
      className={animated ? 'umbra-br' : undefined}
      style={{
        display:        'inline-flex',
        alignItems:     'center',
        justifyContent: 'center',
        width:          size,
        height:         size,
        flexShrink:     0,
        color:          'var(--accent)',
        filter:         `drop-shadow(0 0 ${Math.max(4, size * 0.15)}px var(--accent-glow))`,
        ...style,
      }}
      aria-label="Umbra"
    >
      <Moon
        size={Math.round(size * 0.85)}
        strokeWidth={1.6}
        absoluteStrokeWidth
      />
      {animated && (
        <style>{`@keyframes umbraFlk{0%,100%{opacity:1}45%{opacity:.82}70%{opacity:.91}}.umbra-br{animation:umbraFlk 2.4s ease-in-out infinite}`}</style>
      )}
    </div>
  )
}
