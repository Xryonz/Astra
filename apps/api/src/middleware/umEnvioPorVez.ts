import type { RequestHandler } from 'express'

let ocupado = false
const fila: Array<() => void> = []

function esperarAVez(): Promise<void> {
  if (!ocupado) {
    ocupado = true
    return Promise.resolve()
  }
  return new Promise((vez) => fila.push(vez))
}

function passarAVez(): void {
  const proximo = fila.shift()
  if (proximo) proximo()
  else ocupado = false
}

export const umEnvioPorVez: RequestHandler = (req, res, next) => {
  void esperarAVez().then(() => {
    if (req.destroyed || res.destroyed || res.writableEnded) return passarAVez()
    let passou = false
    const soltar = () => {
      if (passou) return
      passou = true
      passarAVez()
    }
    res.once('finish', soltar)
    res.once('close', soltar)
    next()
  })
}
