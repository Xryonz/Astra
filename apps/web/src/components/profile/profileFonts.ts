/**
 * Map de fonts custom pro displayName do perfil.
 * 8 opções editorial-friendly. Cada font cai pra próxima fonte do stack
 * se a primeira não estiver instalada — funciona em qualquer OS.
 */
export type DisplayFont =
  | 'serif' | 'sans' | 'mono' | 'rounded'
  | 'condensed' | 'handwriting' | 'gothic' | 'modern'

export const FONT_FAMILY: Record<DisplayFont, string> = {
  serif:       'var(--font-display)',
  sans:        '-apple-system, ui-sans-serif, system-ui',
  mono:        'var(--font-mono)',
  rounded:     'ui-rounded, "SF Pro Rounded", system-ui',
  condensed:   '"Helvetica Neue Condensed", Impact, "Arial Narrow", sans-serif',
  handwriting: '"Brush Script MT", cursive',
  gothic:      'UnifrakturCook, "Times New Roman", serif',
  modern:      'Futura, "Avenir Next", "Trebuchet MS", sans-serif',
}

export const FONT_LABELS: Record<DisplayFont, string> = {
  serif:       'Serif',
  sans:        'Sans',
  mono:        'Mono',
  rounded:     'Rounded',
  condensed:   'Condensed',
  handwriting: 'Manuscrita',
  gothic:      'Gótica',
  modern:      'Moderna',
}
