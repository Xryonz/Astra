import type { Request as RequisicaoDoExpress } from 'express'

export type Request = RequisicaoDoExpress<Record<string, string>>
