interface UmbraLogoProps {
  size?: number
  style?: React.CSSProperties
  animated?: boolean
}

/**
 * Logo do Umbra — usa /logo-transparent.svg.
 *
 * Esse arquivo é uma cópia de /favicon.svg com o <path fill="#FFFFFF">
 * wrapper REMOVIDO. Mas o PNG embedded ainda tem fundo PRETO próprio.
 *
 * Hack pra fundo preto sumir: `mix-blend-mode: lighten` sobre BG dark.
 * Pixel preto (#000) ∨ BG dark (#06060e) = #06060e = "transparente" visualmente.
 * Pixels claros do planeta sobrevivem (max retém eles).
 *
 * Drop-shadow REMOVIDO porque seguia o bounding box quadrado da img
 * (alpha channel completamente opaco). Sem PNG com alpha real, não dá
 * pra ter aura só ao redor do conteúdo. Logo fica limpo, sem aura.
 *
 * Favicon (tab) continua usando /favicon.svg raw — com fundo, como pedido.
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
        ...style,
      }}
      aria-label="Umbra"
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
        <style>{`@keyframes umbraFlk{0%,100%{opacity:1}45%{opacity:.82}70%{opacity:.91}}.umbra-br{animation:umbraFlk 2.4s ease-in-out infinite}`}</style>
      )}
    </div>
  )
}
