/**
 * Bio do perfil — texto plain, preserva line breaks via whitespace-pre-wrap.
 * Sem markdown (dropado no overhaul 2026-06-02 — bios antigas com markdown
 * mostram os caracteres literais, mas dados não são perdidos).
 */
interface Props {
  bio?:        string | null
  isSelf:      boolean
  fontFamily:  string
}

export function ProfileBio({ bio, isSelf, fontFamily }: Props) {
  return (
    <section className="mb-5">
      <span className="ed-label block mb-2">— Sobre</span>
      {bio ? (
        <p
          className="text-(--text-2) text-sm leading-relaxed m-0 wrap-break-word whitespace-pre-wrap"
          style={{ fontFamily }}
        >
          {bio}
        </p>
      ) : (
        <p className="text-(--text-3) text-sm italic m-0">
          {isSelf ? 'Você ainda não deixou suas palavras.' : 'Sem palavras ainda.'}
        </p>
      )}
    </section>
  )
}
