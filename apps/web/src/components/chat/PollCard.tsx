import { useState, useMemo, useEffect } from 'react'
import { Check, Lock, Clock } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useConfirm } from '@/hooks/useConfirm'
import { toast } from '@/components/ui/sonner'
import { cn } from '@/lib/utils'

export interface PollOption { id: string; text: string; votes: string[] }
export interface PollData {
  question:      string
  options:       PollOption[]
  allowMultiple: boolean
  expiresAt:     string | null
  closed:        boolean
}

interface PollCardProps {
  channelId: string
  messageId: string
  poll:      PollData
  /** Se for o autor da mensagem, pode encerrar */
  canClose:  boolean
}

/**
 * Card de poll inline na mensagem. Vote toggle → POST /vote.
 * Atualiza optimisticamente; socket vai confirmar via `poll_updated`.
 */
export default function PollCard({ channelId, messageId, poll, canClose }: PollCardProps) {
  const me = useAuthStore((s) => s.user)
  const confirm = useConfirm()
  const [local, setLocal] = useState<PollData>(poll)
  const [voting, setVoting] = useState<string | null>(null)

  // Sync com props quando socket emite update
  useEffect(() => { setLocal(poll) }, [poll])

  const totalVotes = useMemo(
    () => local.options.reduce((sum, o) => sum + o.votes.length, 0),
    [local.options],
  )

  const expired = local.expiresAt ? new Date(local.expiresAt).getTime() < Date.now() : false
  const closed  = local.closed || expired
  const myVotes = new Set(local.options.filter((o) => me && o.votes.includes(me.id)).map((o) => o.id))

  const vote = async (optionId: string) => {
    if (closed || voting || !me) return
    setVoting(optionId)

    // Optimistic update
    setLocal((prev) => {
      const isVoting = !prev.options.find((o) => o.id === optionId)!.votes.includes(me.id)
      return {
        ...prev,
        options: prev.options.map((o) => {
          // Single-vote: tira voto de outras opções primeiro
          if (!prev.allowMultiple && o.id !== optionId) {
            return { ...o, votes: o.votes.filter((u) => u !== me.id) }
          }
          if (o.id === optionId) {
            return {
              ...o,
              votes: isVoting
                ? [...o.votes, me.id]
                : o.votes.filter((u) => u !== me.id),
            }
          }
          return o
        }),
      }
    })

    try {
      await api.post(`/api/channels/${channelId}/polls/${messageId}/vote`, { optionId })
    } catch {
      // Reverte ao receber socket de updated, ou ignora silenciosamente
    } finally {
      setVoting(null)
    }
  }

  const close = async () => {
    if (!canClose || closed) return
    const ok = await confirm({
      title: 'Encerrar enquete?',
      description: 'Votos atuais ficam, mas ninguém pode votar mais. Ação não-reversível.',
      confirmLabel: 'Encerrar',
      destructive: true,
    })
    if (!ok) return
    try {
      await api.post(`/api/channels/${channelId}/polls/${messageId}/close`)
      toast.success('Enquete encerrada')
    } catch {
      toast.error('Erro ao encerrar enquete')
    }
  }

  return (
    <div className="border border-(--border) bg-(--raised)/40 px-3 py-2.5 my-1 max-w-md">
      <div className="flex items-start gap-2 mb-2">
        <p
          className="m-0 text-sm font-medium text-foreground flex-1 leading-snug"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          {local.question}
        </p>
        {closed && (
          <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-wider text-(--text-3) px-1.5 py-0.5 border border-(--border)">
            <Lock className="size-2.5" /> Encerrada
          </span>
        )}
      </div>

      <ul className="flex flex-col gap-1 mb-2">
        {local.options.map((opt) => {
          const count = opt.votes.length
          const pct   = totalVotes > 0 ? (count / totalVotes) * 100 : 0
          const voted = myVotes.has(opt.id)
          return (
            <li key={opt.id}>
              <button
                onClick={() => vote(opt.id)}
                disabled={closed || voting !== null}
                className={cn(
                  'w-full relative overflow-hidden text-left px-2.5 py-1.5 border transition-colors cursor-pointer',
                  voted ? 'border-(--accent)' : 'border-(--border) hover:border-(--accent)/60',
                  (closed || voting !== null) && 'cursor-default opacity-90',
                )}
              >
                {/* fill bar */}
                <div
                  className={cn(
                    'absolute inset-y-0 left-0 transition-all duration-300 ease-out',
                    voted ? 'bg-(--accent)/15' : 'bg-(--accent)/5',
                  )}
                  style={{ width: `${pct}%` }}
                />
                <div className="relative flex items-center gap-2">
                  {voted && <Check className="size-3.5 text-(--accent) shrink-0" />}
                  <span className="text-sm flex-1 truncate">{opt.text}</span>
                  <span className="text-[11px] font-mono text-(--text-3) shrink-0">
                    {count} ({pct.toFixed(0)}%)
                  </span>
                </div>
              </button>
            </li>
          )
        })}
      </ul>

      <div className="flex items-center justify-between text-[10px] font-mono text-(--text-3)">
        <span>
          {totalVotes} voto{totalVotes === 1 ? '' : 's'}
          {local.allowMultiple && ' · múltipla escolha'}
        </span>
        <div className="flex items-center gap-2">
          {local.expiresAt && (
            <span className="inline-flex items-center gap-1">
              <Clock className="size-2.5" />
              {formatExpiry(local.expiresAt, expired)}
            </span>
          )}
          {canClose && !closed && (
            <button
              onClick={close}
              className="text-(--text-3) hover:text-(--danger) cursor-pointer uppercase tracking-wider"
            >
              Encerrar
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function formatExpiry(iso: string, expired: boolean): string {
  if (expired) return 'expirou'
  const diffMs = new Date(iso).getTime() - Date.now()
  const hours  = Math.floor(diffMs / 3600_000)
  const mins   = Math.floor((diffMs % 3600_000) / 60_000)
  if (hours >= 24) return `${Math.floor(hours / 24)}d`
  if (hours >= 1)  return `${hours}h`
  return `${mins}m`
}
