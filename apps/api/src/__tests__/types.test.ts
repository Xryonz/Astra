import { describe, it, expect } from 'vitest'
import {
  AttachmentSchema, SendMessageSchema, EditMessageSchema,
  RegisterSchema, LoginSchema, MessageCursorSchema,
  ChangeEmailSchema, ChangeUsernameSchema,
} from '@astra/types'

describe('AttachmentSchema — URL safety', () => {
  it('aceita http://', () => {
    const r = AttachmentSchema.safeParse({
      url: 'http://example.com/foo.png', type: 'image/png', name: 'foo.png', size: 100,
    })
    expect(r.success).toBe(true)
  })

  it('aceita https://', () => {
    const r = AttachmentSchema.safeParse({
      url: 'https://cdn.example.com/foo.png', type: 'image/png', name: 'foo.png', size: 100,
    })
    expect(r.success).toBe(true)
  })

  it('aceita path relativo (/uploads/...)', () => {
    const r = AttachmentSchema.safeParse({
      url: '/uploads/abc123.png', type: 'image/png', name: 'abc.png', size: 100,
    })
    expect(r.success).toBe(true)
  })

  it('PRESERVA o blurhash (era descartado na validacao)', () => {
    const r = AttachmentSchema.safeParse({
      url: 'https://cdn.example.com/a.webp', type: 'image/webp', name: 'a.webp', size: 100,
      blurhash: 'LEHV6nWB2yk8pyo0adR*.7kCMdnj',
    })
    expect(r.success).toBe(true)
    expect(r.success && r.data.blurhash).toBe('LEHV6nWB2yk8pyo0adR*.7kCMdnj')
  })

  it('PRESERVA a marca de figurinha', () => {
    const r = AttachmentSchema.safeParse({
      url: 'https://cdn.example.com/fig.webp', type: 'image/webp', name: 'susto', size: 0,
      sticker: true,
    })
    expect(r.success).toBe(true)
    expect(r.success && r.data.sticker).toBe(true)
  })

  it('PRESERVA a thumbUrl, com a mesma protecao de URL do url', () => {
    const ok = AttachmentSchema.safeParse({
      url: 'https://cdn.example.com/a.webp', thumbUrl: 'https://cdn.example.com/a_t.webp',
      type: 'image/webp', name: 'a.webp', size: 100,
    })
    expect(ok.success && ok.data.thumbUrl).toBe('https://cdn.example.com/a_t.webp')

    const mau = AttachmentSchema.safeParse({
      url: 'https://cdn.example.com/a.webp', thumbUrl: 'javascript:alert(1)',
      type: 'image/webp', name: 'a.webp', size: 100,
    })
    expect(mau.success).toBe(false)
  })

  it('BLOQUEIA javascript: (XSS)', () => {
    const r = AttachmentSchema.safeParse({
      url: 'javascript:alert(1)', type: 'image/png', name: 'evil.png', size: 100,
    })
    expect(r.success).toBe(false)
  })

  it('BLOQUEIA data: (XSS via data URI)', () => {
    const r = AttachmentSchema.safeParse({
      url: 'data:text/html,<script>alert(1)</script>', type: 'image/png', name: 'x.png', size: 100,
    })
    expect(r.success).toBe(false)
  })

  it('BLOQUEIA file://', () => {
    const r = AttachmentSchema.safeParse({
      url: 'file:///etc/passwd', type: 'image/png', name: 'x.png', size: 100,
    })
    expect(r.success).toBe(false)
  })

  it('size > 50MB rejeitado', () => {
    const r = AttachmentSchema.safeParse({
      url: '/uploads/big.zip', type: 'application/zip', name: 'big.zip',
      size: 51 * 1024 * 1024,
    })
    expect(r.success).toBe(false)
  })

  it('size negativo rejeitado', () => {
    const r = AttachmentSchema.safeParse({
      url: '/x.png', type: 'image/png', name: 'x.png', size: -1,
    })
    expect(r.success).toBe(false)
  })

  it('width/height ridículos rejeitados (max 20k)', () => {
    const r = AttachmentSchema.safeParse({
      url: '/x.png', type: 'image/png', name: 'x.png', size: 100,
      width: 99_999, height: 50,
    })
    expect(r.success).toBe(false)
  })
})

describe('SendMessageSchema', () => {
  it('content só com texto passa', () => {
    const r = SendMessageSchema.safeParse({ content: 'olá' })
    expect(r.success).toBe(true)
  })

  it('só com attachments (sem texto) passa', () => {
    const r = SendMessageSchema.safeParse({
      content: '',
      attachments: [{ url: '/x.png', type: 'image/png', name: 'x.png', size: 100 }],
    })
    expect(r.success).toBe(true)
  })

  it('vazio em ambos rejeita', () => {
    const r = SendMessageSchema.safeParse({ content: '   ' })
    expect(r.success).toBe(false)
  })

  it('content > 4000 chars rejeita', () => {
    const r = SendMessageSchema.safeParse({ content: 'x'.repeat(4001) })
    expect(r.success).toBe(false)
  })

  it('clientNonce válido passa', () => {
    const r = SendMessageSchema.safeParse({ content: 'oi', clientNonce: 'opt-42' })
    expect(r.success).toBe(true)
  })

  it('clientNonce muito longo (>64) rejeita', () => {
    const r = SendMessageSchema.safeParse({ content: 'oi', clientNonce: 'a'.repeat(65) })
    expect(r.success).toBe(false)
  })

  it('attachment com URL javascript: propaga erro', () => {
    const r = SendMessageSchema.safeParse({
      content: 'oi',
      attachments: [{ url: 'javascript:alert(1)', type: 'image/png', name: 'x.png', size: 100 }],
    })
    expect(r.success).toBe(false)
  })
})

describe('EditMessageSchema', () => {
  it('texto válido passa', () => {
    expect(EditMessageSchema.safeParse({ content: 'editado' }).success).toBe(true)
  })

  it('vazio rejeita (edit precisa de conteúdo)', () => {
    expect(EditMessageSchema.safeParse({ content: '' }).success).toBe(false)
  })

  it('> 4000 chars rejeita', () => {
    expect(EditMessageSchema.safeParse({ content: 'x'.repeat(4001) }).success).toBe(false)
  })
})

describe('RegisterSchema', () => {
  const valid = {
    email: 'foo@bar.com', username: 'fulano',
    displayName: 'Fulano', password: 'SenhaForte123',
  }

  it('payload válido (com maiúscula e número) passa', () => {
    expect(RegisterSchema.safeParse(valid).success).toBe(true)
  })

  it('email inválido rejeita', () => {
    expect(RegisterSchema.safeParse({ ...valid, email: 'not-an-email' }).success).toBe(false)
  })

  it('password sem maiúscula rejeita', () => {
    expect(RegisterSchema.safeParse({ ...valid, password: 'senhaforte123' }).success).toBe(false)
  })

  it('password sem número rejeita', () => {
    expect(RegisterSchema.safeParse({ ...valid, password: 'SenhaForteABC' }).success).toBe(false)
  })

  it('password curta (<8) rejeita', () => {
    expect(RegisterSchema.safeParse({ ...valid, password: 'Ab1' }).success).toBe(false)
  })

  it('username com caractere ilegal rejeita', () => {
    expect(RegisterSchema.safeParse({ ...valid, username: 'Fulano!' }).success).toBe(false)
  })
})

describe('LoginSchema', () => {
  it('campos obrigatórios', () => {
    expect(LoginSchema.safeParse({ email: 'a@b.com' }).success).toBe(false)
    expect(LoginSchema.safeParse({ email: 'a@b.com', password: 'x' }).success).toBe(true)
  })
})

describe('MessageCursorSchema', () => {
  it('default limit = 30', () => {
    const r = MessageCursorSchema.safeParse({})
    expect(r.success).toBe(true)
    if (r.success) expect(r.data.limit).toBe(30)
  })

  it('limit > 50 rejeita', () => {
    const r = MessageCursorSchema.safeParse({ limit: 100 })
    expect(r.success).toBe(false)
  })

  it('limit coerce de string', () => {
    const r = MessageCursorSchema.safeParse({ limit: '20' })
    expect(r.success).toBe(true)
    if (r.success) expect(r.data.limit).toBe(20)
  })
})

describe('ChangeEmailSchema — a senha atual e obrigatoria', () => {
  it('aceita e-mail valido com senha', () => {
    const r = ChangeEmailSchema.safeParse({ newEmail: 'novo@exemplo.com', currentPassword: 'Senha123' })
    expect(r.success).toBe(true)
  })

  it('recusa sem a senha atual', () => {
    const r = ChangeEmailSchema.safeParse({ newEmail: 'novo@exemplo.com' })
    expect(r.success).toBe(false)
  })

  it('recusa senha em branco', () => {
    const r = ChangeEmailSchema.safeParse({ newEmail: 'novo@exemplo.com', currentPassword: '' })
    expect(r.success).toBe(false)
  })

  it('recusa e-mail sem formato', () => {
    const r = ChangeEmailSchema.safeParse({ newEmail: 'nao-e-email', currentPassword: 'Senha123' })
    expect(r.success).toBe(false)
  })
})

describe('ChangeUsernameSchema — mesmas regras do cadastro', () => {
  it('aceita minusculas, numeros e underscore', () => {
    expect(ChangeUsernameSchema.safeParse({ username: 'astra_2026' }).success).toBe(true)
  })

  it('recusa maiuscula', () => {
    expect(ChangeUsernameSchema.safeParse({ username: 'Astra' }).success).toBe(false)
  })

  it('recusa espaco e pontuacao', () => {
    expect(ChangeUsernameSchema.safeParse({ username: 'astra dev' }).success).toBe(false)
    expect(ChangeUsernameSchema.safeParse({ username: 'astra.dev' }).success).toBe(false)
  })

  it('recusa curto demais e longo demais', () => {
    expect(ChangeUsernameSchema.safeParse({ username: 'ab' }).success).toBe(false)
    expect(ChangeUsernameSchema.safeParse({ username: 'a'.repeat(33) }).success).toBe(false)
  })

  it('aceita exatamente o formato que o RegisterSchema aceita', () => {
    const nome = 'limite_de_32_caracteres_exatoss'
    expect(nome.length).toBeLessThanOrEqual(32)
    expect(ChangeUsernameSchema.safeParse({ username: nome }).success).toBe(
      RegisterSchema.safeParse({
        email: 'a@b.com', username: nome, displayName: 'A', password: 'Senha123',
      }).success,
    )
  })
})
