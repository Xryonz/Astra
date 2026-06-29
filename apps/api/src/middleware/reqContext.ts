
import type { Request, Response, NextFunction } from 'express'
import crypto from 'crypto'
import { runWithLogContext } from '../lib/logger'

declare global {
  namespace Express {
    interface Request {
      reqId?: string
    }
  }
}

export function reqContext(req: Request, res: Response, next: NextFunction): void {
  const incoming = req.header('x-request-id')

  const reqId = incoming && /^[a-zA-Z0-9-]{1,64}$/.test(incoming)
    ? incoming
    : crypto.randomBytes(8).toString('hex')

  req.reqId = reqId
  res.setHeader('x-request-id', reqId)

  runWithLogContext({ reqId, userId: req.userId }, () => next())
}
