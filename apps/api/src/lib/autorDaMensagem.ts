type Autor = { displayName?: string | null; avatarUrl?: string | null } | null | undefined

export function autorComoEra<T extends Autor>(
  autor: T,
  nome: string | null | undefined,
  foto: string | null | undefined,
): T {
  if (!autor) return autor
  if (!nome && !foto) return autor
  return { ...autor, displayName: nome ?? autor.displayName, avatarUrl: foto ?? autor.avatarUrl }
}

export function identidadeParaGuardar(autor: {
  displayName?: string | null
  username?: string | null
  avatarUrl?: string | null
} | null | undefined) {
  return {
    authorName: autor?.displayName ?? autor?.username ?? null,
    authorAvatarUrl: autor?.avatarUrl ?? null,
  }
}
