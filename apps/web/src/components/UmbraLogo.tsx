interface UmbraLogoProps {
  size?: number
  style?: React.CSSProperties
  animated?: boolean
}

/**
 * Logo do Umbra — usa /favicon.svg (asset gerado pelo RealFaviconGenerator).
 *
 * Truque pra tirar o fundo branco do SVG no app:
 * `mix-blend-mode: lighten` sobre BG dark — pixels brancos do logo viram
 * "noop" (já são brancos) E o fundo branco "desaparece" porque é overridden
 * pelos pixels mais claros do logo (lighten = max).
 *
 * Não é 100% perfeito se o logo tiver pixels muito escuros (eles também
 * tendem a sumir). Se ficar ruim, melhor pedir SVG sem rect background.
 *
 * No favicon (tab do browser) o SVG é usado raw — com fundo, como o user
 * pediu.
 */
export default function UmbraLogo({ size = 40, style, animated = true }: UmbraLogoProps) {
  const inner = Math.round(size * 0.95)
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
        filter:         `drop-shadow(0 0 ${Math.max(4, size * 0.15)}px var(--accent-glow))`,
        ...style,
      }}
      aria-label="Umbra"
    >
      <img
        src="/favicon.svg"
        alt=""
        width={inner}
        height={inner}
        style={{
          display:        'block',
          mixBlendMode:   'lighten',
          pointerEvents:  'none',
        }}
      />
      {animated && (
        <style>{`@keyframes umbraFlk{0%,100%{opacity:1}45%{opacity:.82}70%{opacity:.91}}.umbra-br{animation:umbraFlk 2.4s ease-in-out infinite}`}</style>
      )}
    </div>
  )
}
