
import { useEffect, useRef } from 'react'
import i18n from '@/i18n'
import { getSocket } from '@/lib/socket'
import { useAuthStore } from '@/store/authStore'
import { useNotificationPrefs, type NotificationType } from '@/hooks/useNotifications'

const SOUND_BY_TYPE: Record<NotificationType, string> = {
  mention:  '/notification-mention.mp3',
  dm:       '/notification-dm.mp3',
  reaction: '/notification-soft.mp3',
  reply:    '/notification-soft.mp3',
}

type Synth = (ctx: AudioContext) => void

const SYNTH_BY_TYPE: Record<NotificationType, Synth> = {
  mention: (ctx) => {

    const playNote = (freq: number, start: number, dur: number) => {
      const osc  = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0,    ctx.currentTime + start)
      gain.gain.linearRampToValueAtTime(0.10, ctx.currentTime + start + 0.005)
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + dur)
      osc.connect(gain); gain.connect(ctx.destination)
      osc.start(ctx.currentTime + start)
      osc.stop(ctx.currentTime + start + dur + 0.02)
    }
    playNote(880, 0,    0.12)
    playNote(660, 0.10, 0.14)
  },
  dm: (ctx) => {

    const osc1 = ctx.createOscillator()
    const osc2 = ctx.createOscillator()
    const gain = ctx.createGain()
    osc1.type = 'triangle'; osc1.frequency.value = 523
    osc2.type = 'sine';     osc2.frequency.value = 261
    gain.gain.setValueAtTime(0.08, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.45)
    osc1.connect(gain); osc2.connect(gain); gain.connect(ctx.destination)
    osc1.start(); osc2.start()
    osc1.stop(ctx.currentTime + 0.5); osc2.stop(ctx.currentTime + 0.5)
  },
  reply: (ctx) => {
    const osc  = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'square'
    osc.frequency.value = 392
    gain.gain.setValueAtTime(0.04, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.08)
    osc.connect(gain); gain.connect(ctx.destination)
    osc.start(); osc.stop(ctx.currentTime + 0.1)
  },
  reaction: (ctx) => {
    const osc  = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'triangle'

    osc.frequency.setValueAtTime(720, ctx.currentTime)
    osc.frequency.exponentialRampToValueAtTime(659, ctx.currentTime + 0.18)
    gain.gain.setValueAtTime(0.07, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.2)
    osc.connect(gain); gain.connect(ctx.destination)
    osc.start(); osc.stop(ctx.currentTime + 0.22)
  },
}

function playFallbackSynth(type: NotificationType) {
  try {
    const ctx = new (window.AudioContext || (window as any).webkitAudioContext)()
    SYNTH_BY_TYPE[type](ctx)

    setTimeout(() => ctx.close().catch(() => {}), 700)
  } catch {}
}

const audioCache: Partial<Record<NotificationType, HTMLAudioElement>> = {}

function playPing(type: NotificationType) {
  try {
    let a = audioCache[type]
    if (!a) {
      a = new Audio(SOUND_BY_TYPE[type])
      a.volume  = 0.35
      a.preload = 'auto'
      audioCache[type] = a
    }
    const p = a.play()
    if (p && typeof p.catch === 'function') {
      p.catch(() => playFallbackSynth(type))
    }
  } catch {
    playFallbackSynth(type)
  }
}

function showLocalNotification(title: string, body: string, icon?: string, url?: string) {
  if (!('Notification' in window)) return
  if (Notification.permission !== 'granted') return
  try {
    const n = new Notification(title, { body, icon: icon ?? '/astra-logo.png', tag: url ?? 'astra' })
    n.onclick = () => {
      window.focus()
      if (url) window.location.href = url
      n.close()
    }
  } catch {}
}

export function useInAppNotifications() {
  const userId = useAuthStore((s) => s.user?.id)
  const { data: prefsData } = useNotificationPrefs()
  const focusedRef = useRef<boolean>(typeof document !== 'undefined' ? document.hasFocus() : true)

  const prefsRef = useRef(prefsData?.prefs)
  useEffect(() => { prefsRef.current = prefsData?.prefs }, [prefsData])

  useEffect(() => {
    const onFocus = () => { focusedRef.current = true }
    const onBlur  = () => { focusedRef.current = false }
    const onVis   = () => { focusedRef.current = document.visibilityState === 'visible' && document.hasFocus() }
    window.addEventListener('focus', onFocus)
    window.addEventListener('blur',  onBlur)
    document.addEventListener('visibilitychange', onVis)
    return () => {
      window.removeEventListener('focus', onFocus)
      window.removeEventListener('blur',  onBlur)
      document.removeEventListener('visibilitychange', onVis)
    }
  }, [])

  useEffect(() => {
    if (!userId) return
    let sock: ReturnType<typeof getSocket>
    try { sock = getSocket() } catch { return }

    const onNotification = (p: {
      id: string
      type: NotificationType
      payload: Record<string, any>
      silent?: boolean
    }) => {
      const prefs = prefsRef.current

      if (p.silent) return

      const soundsAllowed = prefs ? prefs.sounds : true
      const localMute = localStorage.getItem('astra-sound') === '0'
      if (soundsAllowed && !localMute) playPing(p.type)

      const desktopAllowed = prefs ? prefs.desktop : true
      if (desktopAllowed && !focusedRef.current) {
        const { authorName, preview, channelName, serverName } = p.payload
        let title = ''
        let body  = preview ?? ''
        switch (p.type) {
          case 'mention':
            title = i18n.t('bell.titleMention', { name: authorName ?? i18n.t('bell.someone') })
            body  = `#${channelName ?? '?'} · ${serverName ?? '?'}\n${preview ?? ''}`
            break
          case 'dm':
            title = authorName ?? i18n.t('bell.dmFallback')
            body  = preview ?? ''
            break
          case 'reply':
            title = i18n.t('bell.titleReply', { name: authorName ?? i18n.t('bell.someone') })
            body  = preview ?? ''
            break
          case 'reaction':
            title = i18n.t('bell.titleReaction', { name: authorName ?? i18n.t('bell.someone'), emoji: p.payload.emoji ?? '' })
            body  = preview ?? ''
            break
        }
        const url = p.type === 'dm' ? '/app/dm' : '/app'
        showLocalNotification(title, body, p.payload.authorAvatar ?? undefined, url)
      }
    }

    sock.on('notification', onNotification)
    return () => { sock.off('notification', onNotification) }
  }, [userId])
}
