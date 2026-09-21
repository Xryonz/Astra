type Autor = { displayName?: string | null; avatarUrl?: string | null } | null | undefined

const DISCO_APAGADO = '/uploads/'

export function fotoViva(foto: string | null | undefined): string | null {
  if (!foto || foto.startsWith(DISCO_APAGADO)) return null
  return foto
}

export function autorComoEra<T extends Autor>(
  autor: T,
  nome: string | null | undefined,
  foto: string | null | undefined,
): T {
  if (!autor) return autor
  const guardada = fotoViva(foto)
  if (!nome && !guardada) return autor
  return { ...autor, displayName: nome ?? autor.displayName, avatarUrl: guardada ?? autor.avatarUrl }
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
