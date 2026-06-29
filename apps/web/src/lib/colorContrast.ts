

export interface ContrastResult {

  text:        string

  isLightBg:   boolean

  luminance:   number
}

function parseHex(hex: string): [number, number, number] | null {
  const m = hex.match(/^#([0-9a-f]{6})$/i)
  if (!m) return null
  const n = parseInt(m[1], 16)
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff]
}

function sampleColor(input: string | null | undefined): [number, number, number] | null {
  if (!input) return null

  const direct = parseHex(input)
  if (direct) return direct

  const m = input.match(/#[0-9a-fA-F]{6}/)
  if (m) return parseHex(m[0])

  const rgb = input.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/)
  if (rgb) return [+rgb[1], +rgb[2], +rgb[3]]
  return null
}

function luminance(r: number, g: number, b: number): number {
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
}

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
