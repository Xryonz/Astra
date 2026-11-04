import { z } from 'zod'

export const RegisterSchema = z.object({
  email: z.string().email('E-mail inválido'),
  username: z
    .string()
    .min(3, 'Mínimo 3 caracteres')
    .max(32, 'Máximo 32 caracteres')
    .regex(/^[a-z0-9_]+$/, 'Apenas letras minúsculas, números e underscore'),
  displayName: z.string().min(1, 'Nome obrigatório').max(64, 'Máximo 64 caracteres'),
  password: z
    .string()
    .min(8, 'Mínimo 8 caracteres')
    .regex(/[A-Z]/, 'Deve conter ao menos uma letra maiúscula')
    .regex(/[0-9]/, 'Deve conter ao menos um número'),
})

export const LoginSchema = z.object({
  email: z.string().email('E-mail inválido'),
  password: z.string().min(1, 'Senha obrigatória'),
})

const BANNER_COLOR_RE = /^(#[0-9a-fA-F]{6}|linear-gradient\(\s*-?\d{1,3}deg\s*,\s*#[0-9a-fA-F]{6}(?:\s*,\s*#[0-9a-fA-F]{6}){1,3}\s*\))$/

const COR_OU_NENHUMA = z
  .string()
  .refine((v) => v === '' || BANNER_COLOR_RE.test(v), 'Cor inválida')
  .transform((v) => (v === '' ? null : v))

const BANNER_BORDER_STYLES = [
  'none', 'aurora', 'pulse', 'ink',
  'marquee', 'glow', 'noise', 'shimmer',
] as const

const DISPLAY_FONTS = ['serif', 'sans', 'mono', 'rounded', 'condensed', 'handwriting', 'gothic', 'modern'] as const

const HEX_COLOR_RE = /^#[0-9a-fA-F]{6}$/

export const UpdateProfileSchema = z.object({
  displayName: z.string().min(1, 'Nome obrigatório').max(64).optional(),
  username: z
    .string()
    .min(3).max(32)
    .regex(/^[a-z0-9_]+$/, 'Apenas letras minúsculas, números e underscore')
    .optional(),
  bio:        z.string().max(300, 'Bio deve ter no máximo 300 caracteres').optional().nullable(),
  avatarUrl:  z.string().optional().nullable(),
  bannerUrl:  z.string().optional().nullable(),
  bannerColor:  COR_OU_NENHUMA.optional().nullable(),
  profileTheme: COR_OU_NENHUMA.optional().nullable(),
  bannerPositionY: z.number().int().min(0).max(100).optional(),
  bannerScale:     z.number().int().min(50).max(300).optional(),
  bannerBorder:    z.enum(BANNER_BORDER_STYLES).optional(),
  bannerTextColor: z.string().regex(HEX_COLOR_RE, 'Use hex #RRGGBB').optional().nullable(),
  pronouns:        z.string().max(32, 'Máx 32 caracteres').optional().nullable(),
  dmPrivacy:       z.enum(['all', 'shared', 'friends']).optional(),
  statusEmoji:     z.string().max(8, 'Apenas 1 emoji').optional().nullable(),
  displayFont:     z.enum(DISPLAY_FONTS).optional(),
})

export const RecortarFotoSchema = z.object({
  imagem: z.string().startsWith('data:'),
  recorte: z.object({
    x:    z.number().int().min(0),
    y:    z.number().int().min(0),
    lado: z.number().int().min(1).max(20_000),
  }),
})

export const ChangePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Senha atual obrigatória'),
  newPassword: z
    .string()
    .min(8, 'Mínimo 8 caracteres')
    .regex(/[A-Z]/, 'Deve conter ao menos uma letra maiúscula')
    .regex(/[0-9]/, 'Deve conter ao menos um número'),
})

export const SetPasswordSchema = z.object({
  newPassword: z
    .string()
    .min(8, 'Mínimo 8 caracteres')
    .regex(/[A-Z]/, 'Deve conter ao menos uma letra maiúscula')
    .regex(/[0-9]/, 'Deve conter ao menos um número'),
})

export const VerifyEmailSchema = z.object({
  code: z.string().length(6, 'Código de 6 dígitos'),
})

export const ChangeEmailSchema = z.object({
  newEmail: z.string().email('E-mail inválido'),
  currentPassword: z.string().min(1, 'Senha atual obrigatória'),
})

export const ChangeUsernameSchema = z.object({
  username: z
    .string()
    .min(3, 'Mínimo 3 caracteres')
    .max(32, 'Máximo 32 caracteres')
    .regex(/^[a-z0-9_]+$/, 'Apenas letras minúsculas, números e underscore'),
})

export const CreateServerSchema = z.object({
  name: z.string().min(1, 'Nome obrigatório').max(100, 'Máximo 100 caracteres'),
  iconUrl: z.string().url().optional(),
  isGroup: z.boolean().optional().default(false),
})

export const CreateChannelSchema = z.object({
  name: z
    .string()
    .min(1, 'Nome obrigatório')
    .max(50, 'Máximo 50 caracteres'),
  type: z.enum(['TEXT', 'VOICE']).default('TEXT'),
  categoryId: z.string().nullable().optional(),
})

const SafeUrlSchema = z.string().min(1).max(2048).refine(
  (s) => s.startsWith('/') || /^https?:\/\//i.test(s),
  { message: 'URL inválida — só http(s) ou /relativa' },
)

export const AttachmentSchema = z.object({
  url:    SafeUrlSchema,
  thumbUrl: SafeUrlSchema.optional(),
  type:   z.string().max(120),
  name:   z.string().max(255),
  size:   z.number().int().nonnegative().max(50 * 1024 * 1024),
  width:  z.number().int().positive().max(20_000).optional(),
  height: z.number().int().positive().max(20_000).optional(),

  blurhash: z.string().max(200).optional(),

  duration: z.number().nonnegative().max(3600).optional(),

  sticker: z.boolean().optional(),
})

export const SendMessageSchema = z.object({
  content:      z.string().max(4000, 'Máximo 4000 caracteres').default(''),
  replyToId:    z.string().optional(),
  attachments:  z.array(AttachmentSchema).max(10).optional(),

  clientNonce:  z.string().min(1).max(64).optional(),

  ttlSeconds:   z.number().int().min(60).max(7 * 86_400).optional(),
}).refine((d) => (d.content && d.content.trim().length > 0) || (d.attachments && d.attachments.length > 0), {
  message: 'Mensagem precisa de texto ou anexo',
})

export const EditMessageSchema = z.object({
  content: z.string().min(1).max(4000),
})

export const MessageCursorSchema = z.object({
  cursor: z.string().optional(),
  limit: z.coerce.number().min(1).max(50).default(30),
})
