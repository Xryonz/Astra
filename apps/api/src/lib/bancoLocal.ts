const MAQUINA_LOCAL = new Set(['localhost', '127.0.0.1', '[::1]'])

export function ehBancoLocal(endereco: string): boolean {
  try {
    const url = new URL(endereco)
    if (url.searchParams.has('host')) return false
    return MAQUINA_LOCAL.has(url.hostname)
  } catch {
    return false
  }
}
