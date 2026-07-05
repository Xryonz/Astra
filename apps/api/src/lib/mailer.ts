import nodemailer, { Transporter } from 'nodemailer'
import { env } from './env'

let transporter: Transporter | null = null

export function initMailer() {
  if (!env.GMAIL_USER || !env.GMAIL_APP_PASSWORD) {
    console.warn('[Mail] GMAIL_USER/GMAIL_APP_PASSWORD ausentes — verificação de email desabilitada')
    return
  }
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: env.GMAIL_USER, pass: env.GMAIL_APP_PASSWORD },
  })
  console.log('[Mail] Gmail configurado')
}

export function isMailEnabled() { return transporter !== null }

export async function sendVerificationCode(to: string, code: string) {
  if (!transporter) return
  const info = await transporter.sendMail({
    from: `"Astra" <${env.GMAIL_USER}>`,
    to,
    subject: `${code} é o seu código do Astra`,
    text: [
      `Seu código de verificação do Astra: ${code}`,
      '',
      'Ele expira em 15 minutos.',
      'Se você não criou uma conta no Astra, ignore este email.',
    ].join('\n'),
  })
  // Diagnostico: prova o que o SMTP do Gmail respondeu (aceito/rejeitado/250).
  console.log('[Mail] enviado:', {
    accepted: info.accepted, rejected: info.rejected, response: info.response,
  })
}
