
export class HttpError extends Error {
  readonly status: number
  readonly code?: string
  readonly meta?: Record<string, unknown>

  constructor(status: number, message: string, code?: string, meta?: Record<string, unknown>) {
    super(message)
    this.name   = 'HttpError'
    this.status = status
    this.code   = code
    this.meta   = meta
  }
}

export const badRequest   = (msg: string, code?: string) => new HttpError(400, msg, code)
export const unauthorized = (msg = 'Não autenticado',  code?: string) => new HttpError(401, msg, code)
export const forbidden    = (msg = 'Sem permissão',    code?: string) => new HttpError(403, msg, code)
export const notFound     = (msg = 'Não encontrado',   code?: string) => new HttpError(404, msg, code)
export const conflict     = (msg: string,              code?: string) => new HttpError(409, msg, code)
export const tooLarge     = (msg: string,              code?: string) => new HttpError(413, msg, code)
export const unprocessable= (msg: string,              code?: string) => new HttpError(422, msg, code)
export const rateLimited  = (msg = 'Muitas requisições', code?: string) => new HttpError(429, msg, code)
