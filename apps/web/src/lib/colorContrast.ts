/**
 * colorContrast — utilidades pra calcular cor de texto legível
 * em cima de qualquer fundo (hex, rgb, gradient).
 *
 * WCAG luminance simplificada (sem gamma correction completo):
 *   L = 0.2126 R + 0.7152 G + 0.0722 B   (normalizado 0..1)
 *
 * Threshold ~0.55 escolhido empíricamente pra match Discord-ish:
 *   bg claro → texto escuro
 *   bg médio/escuro → texto claro
 *
 * Pra gradients/css funcs, extrai o primeiro hex que encontra;
 * approximação ok pra UI text (não fotorrealismo).
 */

export interface ContrastResult {
  /** Cor de texto recomendada (#fff ou #1a1a1a). */
  text:        string
  /** true = bg é claro (precisa texto escuro). */
  isLightBg:   boolean
  /** Luminance 0..1 do bg amostrado. */
  luminance:   number
}

/** Parse hex `#rrggbb` → [r,g,b] 0..255. null se invalid. */
function parseHex(hex: string): [number, number, number] | null {
  const m = hex.match(/^#([0-9a-f]{6})$/i)
  if (!m) return null
  const n = parseInt(m[1], 16)
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff]
}

/** Tenta extrair RGB de qualquer string CSS (hex, rgb(), linear-gradient com hex). */
function sampleColor(input: string | null | undefined): [number, number, number] | null {
  if (!input) return null
  // Hex puro
  const direct = parseHex(input)
  if (direct) return direct
  // Primeiro hex dentro de gradient/etc
  const m = input.match(/#[0-9a-fA-F]{6}/)
  if (m) return parseHex(m[0])
  // rgb(r,g,b)
  const rgb = input.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/)
  if (rgb) return [+rgb[1], +rgb[2], +rgb[3]]
  return null
}

/** Luminance perceptual. */
function luminance(r: number, g: number, b: number): number {
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
}

/**
 * Computa contraste pra qualquer bg string.
 * Default returns texto claro (assume bg escuro) se não conseguir parse.
 */
export function getContrast(bg: string | null | undefined): ContrastResult {
  const rgb = sampleColor(bg)
  if (!rgb) {
    return { text: '#ffffff', isLightBg: false, luminance: 0 }
  }
  const lum = luminance(...rgb)
  const isLightBg = lum > 0.55
  return {
    text:      isLightBg ? '#1a1a1a' : '#ffffff',
    isLightBg,
    luminance: lum,
  }
}
