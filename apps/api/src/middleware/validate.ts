import { Request, Response, NextFunction } from 'express'
import { ZodSchema, ZodError } from 'zod'

type Source = 'body' | 'query' | 'params'

// Factory que retorna um middleware de validação para qualquer schema Zod
// Uso: router.post('/route', validate(MySchema), handler)
export function validate(schema: ZodSchema, source: Source = 'body') {
  return (req: Request, res: Response, next: NextFunction) => {
    try {
      const parsed = schema.parse(req[source])
      req[source] = parsed // Substitui com dados sanitizados
      next()
    } catch (error) {
      if (error instanceof ZodError) {
        const errors = error.errors.map((e) => ({
          field: e.path.join('.'),
          message: e.message,
        }))
        if (process.env.NODE_ENV !== 'production') {
          console.error('[Validate] 400 em', req.method, req.path, '→', JSON.stringify(errors))
        }
        return res.status(400).json({ error: 'Dados inválidos', details: errors })
      }
      next(error)
    }
  }
}
