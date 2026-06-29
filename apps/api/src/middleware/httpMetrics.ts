
import type { Request, Response, NextFunction } from 'express'
import { httpRequestsTotal, httpDurationMs } from '../lib/metrics'

const HEALTH_PATHS = new Set(['/health', '/live', '/ready', '/metrics'])

export function httpMetrics(req: Request, res: Response, next: NextFunction): void {

  if (HEALTH_PATHS.has(req.path)) return next()

  const start = process.hrtime.bigint()
  res.on('finish', () => {
    const durMs = Number(process.hrtime.bigint() - start) / 1e6
    const route = req.route?.path ?? req.baseUrl ?? 'unknown'
    const labels = { method: req.method, route, status: String(res.statusCode) }
    httpRequestsTotal.inc(labels)
    httpDurationMs.observe(labels, durMs)
  })
  next()
}
