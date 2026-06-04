interface UmbraLogoProps {
  size?: number
  style?: React.CSSProperties
  animated?: boolean
}

/**
 * Logo do Umbra — disco accent (lua cheia minimal).
 *
 * Trivial: 1 elemento <circle>. Bulletproof — não tem geometria
 * complexa que possa renderizar errado.
 *
 * `fill="currentColor"` herda --accent via `color` no wrapper.
 * Glow via drop-shadow do --accent-glow.
 */
export default function UmbraLogo({ size = 40, style, animated = true }: UmbraLogoProps) {
  const inner = Math.round(size * 0.85)
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
      <svg
        width={inner}
        height={inner}
        viewBox="0 0 32 32"
        fill="currentColor"
        aria-hidden="true"
      >
        <circle cx="16" cy="16" r="12" />
      </svg>
      {animated && (
        <style>{`@keyframes umbraFlk{0%,100%{opacity:1}45%{opacity:.82}70%{opacity:.91}}.umbra-br{animation:umbraFlk 2.4s ease-in-out infinite}`}</style>
      )}
    </div>
  )
}
