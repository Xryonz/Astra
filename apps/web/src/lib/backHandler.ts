/**
 * Registry de "voltar" pro back nativo (Android). Telas com navegação
 * interna (drill-down de seções, abas) registram um handler que consome
 * o back pra recuar UM nível — em vez de o back físico fazer history.back()
 * e jogar o user pra fora da tela inteira de uma vez.
 *
 * O native.ts (listener backButton) chama runBackHandlers() ANTES de
 * history.back(): se algum handler consome (true), o back para ali. Puro
 * estado em memória — sem mexer no history, sem desync de pushState.
 */
type BackHandler = () => boolean

const stack: BackHandler[] = []

/** Registra um handler. O último registrado tem prioridade. Retorna unregister. */
export function registerBackHandler(fn: BackHandler): () => void {
  stack.push(fn)
  return () => {
    const i = stack.indexOf(fn)
    if (i !== -1) stack.splice(i, 1)
  }
}

/** Roda o handler do topo. true = back consumido (não propaga pro history). */
export function runBackHandlers(): boolean {
  for (let i = stack.length - 1; i >= 0; i--) {
    if (stack[i]()) return true
  }
  return false
}
