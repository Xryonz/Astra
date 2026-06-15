/**
 * Slash commands — transformações client-side aplicadas no `content` antes de mandar.
 * Retorna o content transformado ou null se não for command/sem match.
 */
import i18n from '@/i18n'

interface SlashCommand {
  name: string
  description: string
  transform: (args: string) => string
}

// description vem do i18n (slash.<name>) — resolvida em listSlashCommands().
const COMMANDS: { name: string; transform: (args: string) => string }[] = [
  { name: 'me',        transform: (args) => args.trim() ? `*${args.trim()}*` : '' },
  { name: 'shrug',     transform: (args) => `¯\\_(ツ)_/¯${args ? ' ' + args.trim() : ''}` },
  { name: 'tableflip', transform: (args) => `(╯°□°)╯︵ ┻━┻${args ? ' ' + args.trim() : ''}` },
  { name: 'unflip',    transform: (args) => `┬─┬ ノ( ゜-゜ノ)${args ? ' ' + args.trim() : ''}` },
  { name: 'flip',      transform: (args) => `(ノಠ益ಠ)ノ彡┻━┻${args ? ' ' + args.trim() : ''}` },
  { name: 'spoiler',   transform: (args) => args.trim() ? `||${args.trim()}||` : '' },
]

const COMMANDS_MAP = new Map(COMMANDS.map((c) => [c.name, c]))

export function listSlashCommands(): ReadonlyArray<SlashCommand> {
  return COMMANDS.map((c) => ({ ...c, description: i18n.t(`slash.${c.name}`) }))
}

/**
 * Se `text` for um slash command suportado, retorna o resultado transformado.
 * Senão retorna null (caller manda o texto raw).
 * `/astra` (e legado `/umbra`) é tratado em outro lugar (bot) — não conflita.
 */
export function applySlashCommand(text: string): string | null {
  const m = text.match(/^\/([a-z]+)(?:\s+(.*))?$/i)
  if (!m) return null
  const [, name, args = ''] = m
  const cmd = COMMANDS_MAP.get(name.toLowerCase())
  if (!cmd) return null
  return cmd.transform(args)
}
