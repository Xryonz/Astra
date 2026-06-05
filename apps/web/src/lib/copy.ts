/**
 * Astra — vocabulário central do app.
 *
 * Strings expostas pro usuário ficam centralizadas aqui. Facilita:
 *  - Rebranding (mexe num lugar só)
 *  - i18n no futuro
 *  - Consistência de tom (espacial/editorial)
 */

export const BRAND = 'Astra'

// ── Nomes de entidades ────────────────────────────────────────
export const NOUN = {
  app:        'Astra',
  server:     'constelação',
  serverCap:  'Constelação',
  serverPl:   'constelações',
  person:     'estrela',
  personCap:  'Estrela',
  personPl:   'estrelas',
  group:      'aglomerado',
  groupCap:   'Aglomerado',
  channel:    'canal', // mantido por UX
  dm:         'mensagem direta',
  coordinate: 'coordenada',
} as const

// ── CTAs e ações ──────────────────────────────────────────────
export const ACTION = {
  createServer:  'Crie uma constelação',
  joinServer:    'Entrar em constelação',
  leaveServer:   'Deixar constelação',
  inviteServer:  'Convidar pra constelação',
  createGroup:   'Crie um aglomerado',
  addStar:       'Adicionar estrela',
  findStar:      'Procurar uma estrela',
  logout:        'Sair da Astra',
} as const

// ── Empty states ──────────────────────────────────────────────
export const EMPTY = {
  noServers:  { title: 'Seu céu ainda está vazio', hint: 'Crie ou entre numa constelação.' },
  noDMs:      { title: 'Nenhuma estrela à vista', hint: 'Convide alguém pra começar.' },
  noFriends:  { title: 'Sozinho no céu', hint: 'Adicione estrelas por username ou coordenada.' },
  noMessages: { title: 'Silêncio cósmico', hint: 'Seja o primeiro a transmitir aqui.' },
} as const

// ── Status display ────────────────────────────────────────────
export const STATUS_LABEL: Record<string, string> = {
  ONLINE:    'Brilhando',
  IDLE:      'Distante',
  DND:       'Eclipse',
  INVISIBLE: 'Oculta',
  OFFLINE:   'Apagada',
}

// ── Toasts (sucesso e erro) ───────────────────────────────────
export const TOAST = {
  serverCreated: 'Constelação acesa.',
  serverDeleted: 'Constelação extinta.',
  channelDeleted: 'Canal eclipsado.',
  friendAdded:   'Estrela alinhada.',
  networkLost:   'Sinal perdido — tentando reconectar.',
  copySuccess:   'Coordenada copiada.',
} as const
