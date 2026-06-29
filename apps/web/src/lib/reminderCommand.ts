
const DURATION_PART_RE = /(\d+)\s*(d|h|m|min|s)/gi

export interface ReminderCmd {
  content:    string
  durationMs: number

}

export function parseReminderCommand(text: string): ReminderCmd | null {
  const m = text.match(/^\/lembre\s+(.+?)\s+em\s+([\dhmds\s]+)\s*$/i)
  if (!m) return null
  const content  = m[1].trim()
  const durRaw   = m[2].trim().toLowerCase()
  if (!content) return null

  let total = 0
  let matched = false
  let mm: RegExpExecArray | null
  while ((mm = DURATION_PART_RE.exec(durRaw)) !== null) {
    matched = true
    const n = parseInt(mm[1], 10)
    const u = mm[2]
    if (!Number.isFinite(n) || n < 0) return null
    if      (u === 'd')                total += n * 86_400_000
    else if (u === 'h')                total += n * 3_600_000
    else if (u === 'm' || u === 'min') total += n * 60_000
    else if (u === 's')                total += n * 1000
  }
  DURATION_PART_RE.lastIndex = 0
  if (!matched) return null

  if (total < 60_000 || total > 365 * 86_400_000) return null
  return { content, durationMs: total }
}
