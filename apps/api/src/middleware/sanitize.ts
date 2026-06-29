import { Request, Response, NextFunction } from 'express'

function sanitizeShallowString(value: unknown): unknown {
  if (typeof value === 'string') {
    return value
      .replace(/<[^>]*>/g, '')
      .replace(/on\w+\s*=/gi, '')
      .replace(/javascript\s*:/gi, '')
      .trim()
  }
  if (Array.isArray(value)) return value.map(sanitizeShallowString)
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, sanitizeShallowString(v)])
    )
  }
  return value
}

export function sanitizeInputs(req: Request, _res: Response, next: NextFunction) {
  if (req.query)  req.query  = sanitizeShallowString(req.query)  as Record<string, string>
  if (req.params) req.params = sanitizeShallowString(req.params) as Record<string, string>
  next()
}