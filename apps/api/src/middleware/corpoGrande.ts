import express, { type RequestHandler } from 'express'
import { temCredencialValida } from './auth'

const corpoDeAte16mb = express.json({ limit: '16mb' })

export const corpoGrandeSoComLogin: RequestHandler = (req, res, next) =>
  temCredencialValida(req) ? corpoDeAte16mb(req, res, next) : next()
