
type BackHandler = () => boolean

const stack: BackHandler[] = []

export function registerBackHandler(fn: BackHandler): () => void {
  stack.push(fn)
  return () => {
    const i = stack.indexOf(fn)
    if (i !== -1) stack.splice(i, 1)
  }
}

export function runBackHandlers(): boolean {
  for (let i = stack.length - 1; i >= 0; i--) {
    if (stack[i]()) return true
  }
  return false
}
