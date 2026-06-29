
import client from 'prom-client'

export const registry = new client.Registry()
registry.setDefaultLabels({ service: 'astra-api' })
client.collectDefaultMetrics({ register: registry })

export const httpRequestsTotal = new client.Counter({
  name: 'http_requests_total',
  help: 'Total HTTP requests',
  labelNames: ['method', 'route', 'status'] as const,
  registers: [registry],
})

export const httpDurationMs = new client.Histogram({
  name: 'http_duration_ms',
  help: 'HTTP request duration in ms',
  labelNames: ['method', 'route', 'status'] as const,

  buckets: [5, 25, 50, 100, 250, 500, 1000, 2500, 5000],
  registers: [registry],
})

export const socketConnections = new client.Gauge({
  name: 'socket_active_connections',
  help: 'Sockets atualmente conectados',
  registers: [registry],
})

export const socketEventsTotal = new client.Counter({
  name: 'socket_events_total',
  help: 'Eventos socket recebidos/enviados',
  labelNames: ['event', 'direction'] as const,
  registers: [registry],
})

export const messagesSentTotal = new client.Counter({
  name: 'messages_sent_total',
  help: 'Mensagens criadas (channel + DM)',
  labelNames: ['kind'] as const,
  registers: [registry],
})

export const botInvocationsTotal = new client.Counter({
  name: 'bot_invocations_total',
  help: 'Chamadas ao bot Claude',
  labelNames: ['status'] as const,
  registers: [registry],
})

export const botTokensTotal = new client.Counter({
  name: 'bot_tokens_total',
  help: 'Tokens consumidos pelo bot',
  labelNames: ['kind'] as const,
  registers: [registry],
})

export const dbQueryDurationMs = new client.Histogram({
  name: 'db_query_duration_ms',
  help: 'Duração de queries DB críticas',
  labelNames: ['op'] as const,
  buckets: [1, 5, 10, 25, 50, 100, 250, 500, 1000],
  registers: [registry],
})

export async function timed<T>(op: string, fn: () => Promise<T>): Promise<T> {
  const end = dbQueryDurationMs.startTimer({ op })
  try { return await fn() }
  finally { end() }
}

export async function renderMetrics(): Promise<string> {
  return registry.metrics()
}

export function metricsContentType(): string {
  return registry.contentType
}
