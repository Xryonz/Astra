
export function ProfileCardSkeleton() {
  return (
    <div className="flex flex-col animate-pulse">
      {}
      <div
        className="h-48 shrink-0"
        style={{
          background: 'linear-gradient(135deg, var(--raised), var(--overlay), var(--raised))',
        }}
      />

      <div className="px-6 sm:px-7 pb-8">
        {}
        <div
          className="size-24 rounded-full -mt-12 mb-3 border-4 bg-(--raised)"
          style={{ borderColor: 'var(--overlay)' }}
        />

        {}
        <div className="h-7 w-48 rounded bg-(--raised) mb-2" />

        {}
        <div className="h-3 w-32 rounded bg-(--raised) mb-6" />

        {}
        <div className="h-2.5 w-16 rounded bg-(--raised) mb-2" />

        {}
        <div className="space-y-2 mb-6">
          <div className="h-3 w-full rounded bg-(--raised)" />
          <div className="h-3 w-[90%] rounded bg-(--raised)" />
          <div className="h-3 w-[60%] rounded bg-(--raised)" />
        </div>

        {}
        <div className="h-2.5 w-24 rounded bg-(--raised) mb-1.5" />
        <div className="h-3 w-44 rounded bg-(--raised)" />
      </div>
    </div>
  )
}
