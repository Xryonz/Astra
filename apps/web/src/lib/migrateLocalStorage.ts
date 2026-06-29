
const LOCAL_KEYS: Record<string, string> = {
  'umbra-accent':            'astra-accent',
  'umbra-bg':                'astra-bg',
  'umbra-refresh':           'astra-refresh',
  'umbra-voice-volume':      'astra-voice-volume',
  'umbra-voice-pip-pos':     'astra-voice-pip-pos',
  'umbra-incoming-pos':      'astra-incoming-pos',
  'umbra-sound':             'astra-sound',
  'umbra-sidebar-collapsed': 'astra-sidebar-collapsed',
}

const SESSION_KEYS: Record<string, string> = {
  'umbra-auth': 'astra-auth',
}

function migrate(store: Storage, map: Record<string, string>) {
  for (const [oldKey, newKey] of Object.entries(map)) {
    const old = store.getItem(oldKey)
    if (old === null) continue
    if (store.getItem(newKey) === null) store.setItem(newKey, old)
    store.removeItem(oldKey)
  }
}

export function migrateLocalStorage(): void {
  try { migrate(localStorage,   LOCAL_KEYS)   } catch {}
  try { migrate(sessionStorage, SESSION_KEYS) } catch {}
}
