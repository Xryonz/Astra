import { env } from './env'

// Envio via API HTTP do Brevo (nao SMTP): o Render bloqueia portas SMTP de
// saida (465/587) -> nodemailer dava "Connection timeout". HTTPS passa pelo
// firewall numa boa. Sem lib nova: fetch global (Node 20).
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
    // Surge o motivo real (401 key errada, 400 remetente nao verificado...)
    // pro .catch de quem chama logar. Corta o corpo pra nao poluir o log.
    const body = await res.text().catch(() => '')
    throw new Error(`Brevo ${res.status}: ${body.slice(0, 300)}`)
  }
  console.log('[Mail] enviado via Brevo ->', to)
}
