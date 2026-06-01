import { useState } from 'react'
import { Plus, X, BarChart3 } from 'lucide-react'
import { api } from '@/lib/api'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'

interface PollComposerProps {
  open:       boolean
  onClose:    () => void
  channelId:  string
}

const DURATION_OPTIONS = [
  { label: 'Sem expiração', value: null  },
  { label: '1 hora',        value: 1    },
  { label: '6 horas',       value: 6    },
  { label: '24 horas',      value: 24   },
  { label: '3 dias',        value: 72   },
  { label: '7 dias',        value: 168  },
]

/**
 * Dialog pra criar enquete. POST /api/channels/:id/polls cria a msg-poll;
 * socket entrega ao MessageList via new_message normal.
 */
export default function PollComposer({ open, onClose, channelId }: PollComposerProps) {
  const [question, setQuestion]   = useState('')
  const [options, setOptions]     = useState<string[]>(['', ''])
  const [allowMultiple, setAllow] = useState(false)
  const [duration, setDuration]   = useState<number | null>(24)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError]         = useState('')

  const reset = () => {
    setQuestion(''); setOptions(['', '']); setAllow(false); setDuration(24); setError('')
  }

  const addOption    = () => setOptions((o) => o.length < 8 ? [...o, ''] : o)
  const removeOption = (i: number) => setOptions((o) => o.length > 2 ? o.filter((_, idx) => idx !== i) : o)
  const setOption    = (i: number, v: string) =>
    setOptions((o) => o.map((opt, idx) => (idx === i ? v : opt)))

  const canSubmit =
    question.trim().length >= 3 &&
    options.filter((o) => o.trim().length > 0).length >= 2 &&
    !submitting

  const submit = async () => {
    if (!canSubmit) return
    setSubmitting(true)
    setError('')
    try {
      await api.post(`/api/channels/${channelId}/polls`, {
        question:      question.trim(),
        options:       options.map((o) => o.trim()).filter((o) => o.length > 0),
        allowMultiple,
        durationHours: duration,
      })
      reset()
      onClose()
    } catch (e: any) {
      setError(e.response?.data?.error ?? 'Erro ao criar enquete')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o: boolean) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <BarChart3 className="size-4 text-(--accent)" />
            Nova enquete
          </DialogTitle>
          <DialogDescription>
            Crie uma pergunta com até 8 opções. Membros do canal podem votar.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div>
            <label className="text-xs uppercase tracking-wider text-(--text-3) font-medium block mb-1.5">
              Pergunta
            </label>
            <Input
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              maxLength={300}
              placeholder="O que você quer perguntar?"
              autoFocus
            />
          </div>

          <div>
            <label className="text-xs uppercase tracking-wider text-(--text-3) font-medium block mb-1.5">
              Opções
            </label>
            <div className="flex flex-col gap-1.5">
              {options.map((opt, i) => (
                <div key={i} className="flex items-center gap-1.5">
                  <span className="text-[10px] font-mono text-(--text-3) w-4 text-right">{i + 1}.</span>
                  <Input
                    value={opt}
                    onChange={(e) => setOption(i, e.target.value)}
                    maxLength={80}
                    placeholder={`Opção ${i + 1}`}
                    className="flex-1"
                  />
                  {options.length > 2 && (
                    <button
                      onClick={() => removeOption(i)}
                      className="size-7 flex items-center justify-center text-(--text-3) hover:text-(--danger) cursor-pointer"
                      aria-label="Remover opção"
                    >
                      <X className="size-3.5" />
                    </button>
                  )}
                </div>
              ))}
              {options.length < 8 && (
                <button
                  onClick={addOption}
                  className="self-start text-xs text-(--text-3) hover:text-(--accent) flex items-center gap-1 mt-1 cursor-pointer"
                >
                  <Plus className="size-3" /> Adicionar opção
                </button>
              )}
            </div>
          </div>

          <div>
            <label className="text-xs uppercase tracking-wider text-(--text-3) font-medium block mb-1.5">
              Duração
            </label>
            <select
              value={duration === null ? 'null' : String(duration)}
              onChange={(e) => setDuration(e.target.value === 'null' ? null : Number(e.target.value))}
              className="w-full bg-(--raised)/40 border border-(--border) px-2.5 h-9 text-sm outline-none focus:border-(--accent) cursor-pointer"
            >
              {DURATION_OPTIONS.map((d) => (
                <option key={String(d.value)} value={d.value === null ? 'null' : String(d.value)}>
                  {d.label}
                </option>
              ))}
            </select>
          </div>

          <label className="flex items-center gap-2 text-sm text-(--text-2) cursor-pointer">
            <Checkbox checked={allowMultiple} onCheckedChange={(v) => setAllow(!!v)} />
            Permitir múltipla escolha
          </label>

          {error && <p className="text-xs text-(--danger) m-0">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button onClick={submit} disabled={!canSubmit}>
            {submitting ? 'Criando…' : 'Criar enquete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
