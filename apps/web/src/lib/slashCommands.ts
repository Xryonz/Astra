
import i18n from '@/i18n'

interface SlashCommand {
  name: string
  description: string
  transform: (args: string) => string
}

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

export function applySlashCommand(text: string): string | null {
  const m = text.match(/^\/([a-z]+)(?:\s+(.*))?$/i)
  if (!m) return null
  const [, name, args = ''] = m
  const cmd = COMMANDS_MAP.get(name.toLowerCase())
  if (!cmd) return null
  return cmd.transform(args)
}
