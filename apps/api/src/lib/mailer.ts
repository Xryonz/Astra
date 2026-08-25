import { env } from './env'

const BREVO_ENDPOINT = 'https://api.brevo.com/v3/smtp/email'

export function initMailer() {
  if (isMailEnabled()) {
    console.log('[Mail] Brevo configurado')
  } else {
    console.warn('[Mail] BREVO_API_KEY/MAIL_FROM ausentes — verificação de email desabilitada')
  }
}

export function isMailEnabled() {
  return !!(env.BREVO_API_KEY && env.MAIL_FROM)
}

export async function sendVerificationCode(to: string, code: string) {
  if (!isMailEnabled()) return
  const res = await fetch(BREVO_ENDPOINT, {
    method: 'POST',
    headers: {
      'api-key':      env.BREVO_API_KEY!,
      'content-type': 'application/json',
      accept:         'application/json',
    },
    body: JSON.stringify({
      sender: { name: 'Astra', email: env.MAIL_FROM },
      to:     [{ email: to }],
      subject: `${code} é o seu código do Astra`,
      textContent: [
        `Seu código de verificação do Astra: ${code}`,
        '',
        'Ele expira em 15 minutos.',
        'Se você não criou uma conta no Astra, ignore este email.',
      ].join('\n'),
    }),
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(`Brevo ${res.status}: ${body.slice(0, 300)}`)
  }
  console.log('[Mail] enviado via Brevo ->', to)
}
