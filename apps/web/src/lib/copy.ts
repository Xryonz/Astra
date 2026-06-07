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
  app:          'Astra',
  server:       'constelação',
  serverCap:    'Constelação',
  serverPl:     'constelações',
  person:       'estrela',
  personCap:    'Estrela',
  personPl:     'estrelas',
  group:        'aglomerado',
  groupCap:     'Aglomerado',
  channel:      'órbita',
  channelCap:   'Órbita',
  channelPl:    'órbitas',
  voiceChannel: 'órbita de voz',
  thread:       'cometa',
  threadCap:    'Cometa',
  threadPl:     'cometas',
  dm:           'sussurro',
  dmCap:        'Sussurro',
  dmPl:         'sussurros',
  coordinate:   'coordenada',
} as const

// ── CTAs e ações ──────────────────────────────────────────────
export const ACTION = {
  createServer:  'Forjar constelação',
  joinServer:    'Orbitar constelação',
  leaveServer:   'Desorbitar',
  inviteServer:  'Convidar pra constelação',
  createGroup:   'Forjar aglomerado',
  createChannel: 'Abrir órbita',
  startThread:   'Soltar cometa',
  startDM:       'Iniciar sussurro',
  addStar:       'Adicionar estrela',
  findStar:      'Procurar uma estrela',
  logout:        'Sair da Astra',
} as const

// ── Descrições explicativas (microcopy de "(servidor)" etc) ───
// Aparecem embaixo de CTAs primários e em tooltips secundários,
// pra usuário entender o termo cósmico = conceito Discord-like.
export const DESC = {
  constelacao: 'Servidor — espaço da sua comunidade',
  aglomerado:  'Grupo privado — sem convite público',
  estrela:     'Usuário',
  orbita:      'Canal de texto',
  orbitaVoz:   'Canal de voz/vídeo',
  cometa:      'Thread — conversa derivada de uma mensagem',
  sussurro:    'Mensagem privada 1-a-1',
} as const

// ── Empty states ──────────────────────────────────────────────
export const EMPTY = {
  noServers:     { title: 'Seu céu ainda está vazio', hint: 'Crie ou entre numa constelação.' },
  noDMs:         { title: 'Nenhuma estrela à vista', hint: 'Convide alguém pra começar.' },
  noFriends:     { title: 'Sozinho no céu', hint: 'Adicione estrelas por username ou coordenada.' },
  noMessages:    { title: 'Silêncio cósmico', hint: 'Seja o primeiro a transmitir aqui.' },
  noChannelMsgs: { title: 'Silêncio nesta órbita', hint: 'Envie a primeira transmissão.' },
  noThreads:     { title: 'Sem cometas por aqui', hint: 'Responda numa mensagem pra abrir um.' },
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
  serverCreated:  'Constelação acesa.',
  serverDeleted:  'Constelação extinta.',
  channelCreated: 'Órbita aberta.',
  channelDeleted: 'Órbita eclipsada.',
  threadCreated:  'Cometa solto.',
  friendAdded:    'Estrela alinhada.',
  networkLost:    'Sinal perdido — tentando reconectar.',
  copySuccess:    'Coordenada copiada.',
} as const
