/**
 * Skeleton editorial pro ProfileCard.
 * Reproduz a silhueta real (banner + avatar circular sobrepondo + linhas)
 * pra evitar layout shift quando o profile chega.
 * Shimmer via animate-pulse do tailwindcss-animate (já carregado em index.css).
 */
export function ProfileCardSkeleton() {
  return (
    <div className="flex flex-col animate-pulse">
      {/* Banner — gradient sutil em vez de cor sólida, mantém vibe editorial */}
      <div
        className="h-48 shrink-0"
        style={{
          background: 'linear-gradient(135deg, var(--raised), var(--overlay), var(--raised))',
        }}
      />

      <div className="px-6 sm:px-7 pb-8">
        {/* Avatar circle — sobrepõe banner mantendo a mesma proporção do Hero real */}
        <div
          className="size-24 rounded-full -mt-12 mb-3 border-4 bg-(--raised)"
          style={{ borderColor: 'var(--overlay)' }}
        />

        {/* Nome — 1 linha serif longa */}
        <div className="h-7 w-48 rounded bg-(--raised) mb-2" />

        {/* Handle + status — linha curta */}
        <div className="h-3 w-32 rounded bg-(--raised) mb-6" />

        {/* Label "Sobre" */}
        <div className="h-2.5 w-16 rounded bg-(--raised) mb-2" />

        {/* Bio — 3 linhas */}
        <div className="space-y-2 mb-6">
          <div className="h-3 w-full rounded bg-(--raised)" />
          <div className="h-3 w-[90%] rounded bg-(--raised)" />
          <div className="h-3 w-[60%] rounded bg-(--raised)" />
        </div>

        {/* Label + linha "Membro desde" */}
        <div className="h-2.5 w-24 rounded bg-(--raised) mb-1.5" />
        <div className="h-3 w-44 rounded bg-(--raised)" />
      </div>
    </div>
  )
}
