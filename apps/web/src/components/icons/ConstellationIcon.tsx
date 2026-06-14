/** Glyph custom: 4 pontos conectados — "constelação". Lucide não tem. */
export function ConstellationIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="20" height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <line x1="6" y1="7" x2="13" y2="11" />
      <line x1="13" y1="11" x2="18" y2="6" />
      <line x1="13" y1="11" x2="10" y2="18" />
      <circle cx="6"  cy="7"  r="1.6" fill="currentColor" />
      <circle cx="13" cy="11" r="1.8" fill="currentColor" />
      <circle cx="18" cy="6"  r="1.4" fill="currentColor" />
      <circle cx="10" cy="18" r="1.4" fill="currentColor" />
    </svg>
  )
}
