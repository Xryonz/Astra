
import type { QueryClient } from '@tanstack/react-query'

const DB_NAME    = 'astra-offline'
const STORE      = 'messages-first-page'
const DB_VERSION = 1
const MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000

interface CachedEntry { page: unknown; savedAt: number }

let dbPromise: Promise<IDBDatabase> | null = null
function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) req.result.createObjectStore(STORE)
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror   = () => { dbPromise = null; reject(req.error) }
  })
  return dbPromise
}

export async function saveFirstPage(channelId: string, page: unknown): Promise<void> {
  try {
    const db = await openDb()
    const entry: CachedEntry = { page, savedAt: Date.now() }
    db.transaction(STORE, 'readwrite').objectStore(STORE).put(entry, channelId)
  } catch { }
}

export async function getCachedFirstPage(channelId: string): Promise<unknown | null> {
  try {
    const db = await openDb()
    return await new Promise((resolve) => {
      const req = db.transaction(STORE, 'readonly').objectStore(STORE).get(channelId)
      req.onsuccess = () => {
        const entry = req.result as CachedEntry | undefined
        resolve(entry && Date.now() - entry.savedAt < MAX_AGE_MS ? entry.page : null)
      }
      req.onerror = () => resolve(null)
    })
  } catch { return null }
}

export async function clearMessageCache(): Promise<void> {
  try {
    const db = await openDb()
    db.transaction(STORE, 'readwrite').objectStore(STORE).clear()
  } catch {}
}

export function setupMessageCache(qc: QueryClient): void {
  const lastWrite = new Map<string, number>()
  qc.getQueryCache().subscribe((event) => {
    if (event.type !== 'updated') return
    const { queryKey, state } = event.query
    if (queryKey[0] !== 'messages' || typeof queryKey[1] !== 'string') return
    if (state.status !== 'success') return
    const channelId = queryKey[1]
    const now = Date.now()
    if (now - (lastWrite.get(channelId) ?? 0) < 2000) return
    lastWrite.set(channelId, now)
    const data = state.data as { pages?: unknown[] } | undefined
    if (data?.pages?.[0]) void saveFirstPage(channelId, data.pages[0])
  })
}

export async function hydrateChannelFromCache(qc: QueryClient, channelId: string): Promise<void> {
  if (qc.getQueryData(['messages', channelId])) return
  const page = await getCachedFirstPage(channelId)
  if (!page) return
  if (qc.getQueryData(['messages', channelId])) return
  qc.setQueryData(
    ['messages', channelId],
    { pages: [page], pageParams: [undefined] },
    { updatedAt: 0 },
  )
}
