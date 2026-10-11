import { describe, it, expect } from 'vitest'
import { encodeCursor, parseCursor } from './cursorDeMensagens'

describe('cursor de mensagens', () => {
  it('volta a data e o id que foram gravados', () => {
    const quando = new Date('2026-10-09T16:37:12.345Z')
    const lido = parseCursor(encodeCursor(quando, 'cAbC123'))

    expect(lido?.id).toBe('cAbC123')
    expect(lido?.createdAt.toISOString()).toBe(quando.toISOString())
  })

  it('cursor ausente ou vazio vale como primeira página', () => {
    expect(parseCursor(undefined)).toBeNull()
    expect(parseCursor('')).toBeNull()
  })

  it('id cru do formato antigo dos sussurros vira primeira página, sem erro', () => {
    expect(parseCursor('cX9fK2mQ7aB4dE1gH5jL8nP0')).toBeNull()
  })

  it('cursor sem data, sem id ou com data inválida é ignorado', () => {
    const semData = Buffer.from(JSON.stringify({ id: 'c1' })).toString('base64url')
    const semId = Buffer.from(JSON.stringify({ createdAt: '2026-10-09T00:00:00.000Z' })).toString('base64url')
    const dataRuim = Buffer.from(JSON.stringify({ createdAt: 'ontem', id: 'c1' })).toString('base64url')

    expect(parseCursor(semData)).toBeNull()
    expect(parseCursor(semId)).toBeNull()
    expect(parseCursor(dataRuim)).toBeNull()
  })
})
