interface AstraLogoProps {
  size?: number
  style?: React.CSSProperties
  animated?: boolean
}

/**
 * Logo da Astra — usa /logo-transparent.svg.
 *
 * Hack pra fundo preto sumir: `mix-blend-mode: lighten` sobre BG dark.
 * Pixel preto ∨ BG dark = BG dark ("transparente" visualmente).
 *
 * Favicon (tab) continua usando /favicon.svg raw — com fundo.
 */
export default function AstraLogo({ size = 40, style, animated = true }: AstraLogoProps) {
  const inner = Math.round(size * 0.95)
  return (
    <div
      className={animated ? 'astra-br' : undefined}
      style={{
        display:        'inline-flex',
        alignItems:     'center',
        justifyContent: 'center',
        width:          size,
        height:         size,
        flexShrink:     0,
        ...style,
      }}
      aria-label="Astra"
    >
      <img
        src="/logo-transparent.svg"
        alt=""
        width={inner}
        height={inner}
        style={{
          display:       'block',
          mixBlendMode:  'lighten',
          pointerEvents: 'none',
        }}
      />
      {animated && (
        <style>{`@keyframes astraFlk{0%,100%{opacity:1}45%{opacity:.82}70%{opacity:.91}}.astra-br{animation:astraFlk 2.4s ease-in-out infinite}`}</style>
      )}
    </div>
  )
}
