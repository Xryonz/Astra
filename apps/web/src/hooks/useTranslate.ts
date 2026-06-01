/**
 * Tradução de mensagens via Claude Haiku.
 * Cache local na Query cache: chave = ['translate', messageId, targetLang].
 * Server-side já cacheia em Redis 24h, então hits são baratos.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export const TRANSLATE_LANGS = [
  { code: 'pt', name: 'Português' },
  { code: 'en', name: 'English' },
  { code: 'es', name: 'Español' },
  { code: 'fr', name: 'Français' },
  { code: 'de', name: 'Deutsch' },
  { code: 'it', name: 'Italiano' },
  { code: 'ja', name: '日本語' },
  { code: 'zh', name: '中文' },
] as const

export type TranslateLang = typeof TRANSLATE_LANGS[number]['code']

export function useTranslateMessage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ messageId, text, targetLang }: { messageId: string; text: string; targetLang: TranslateLang }) => {
      const res = await api.post('/api/translate', { text, targetLang })
      const translation = res.data.data.translation as string
      qc.setQueryData(['translate', messageId, targetLang], translation)
      return translation
    },
  })
}

export function useTranslationCache(messageId: string, targetLang: TranslateLang | null): string | null {
  const qc = useQueryClient()
  if (!targetLang) return null
  return qc.getQueryData<string>(['translate', messageId, targetLang]) ?? null
}
