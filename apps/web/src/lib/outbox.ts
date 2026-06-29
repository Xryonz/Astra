
import { api } from '@/lib/api'

export interface OutboxAuthor {
  id: string; username: string; displayName: string
  avatarUrl: string | null; displayFont?: string
}
export interface OutboxItem {
  id:        string
  kind:      'channel' | 'dm'
  targetId:  string
  content:   string
  createdAt: number
  author:    OutboxAuthor
}

const DB_NAME = 'astra-outbox'
const STORE   = 'queue'

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE)) {
        const os = db.createObjectStore(STORE, { keyPath: 'id' })
        os.createIndex('target', ['kind', 'targetId'], { unique: false })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror   = () => reject(req.error)
  })
}

async function tx<T>(mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest): Promise<T> {
  const db = await openDB()
  return new Promise<T>((resolve, reject) => {
    const t = db.transaction(STORE, mode)
    const req = fn(t.objectStore(STORE))
    req.onsuccess = () => resolve(req.result as T)
    req.onerror   = () => reject(req.error)
    t.oncomplete  = () => db.close()
  })
}

export async function enqueueOutbox(item: OutboxItem): Promise<void> {
  try { await tx('readwrite', (s) => s.put(item)) } catch { }
}

export async function removeOutbox(id: string): Promise<void> {
  try { await tx('readwrite', (s) => s.delete(id)) } catch { }
}

export async function getOutbox(): Promise<OutboxItem[]> {
  try { return (await tx<OutboxItem[]>('readonly', (s) => s.getAll())) ?? [] } catch { return [] }
}

export async function getOutboxFor(kind: 'channel' | 'dm', targetId: string): Promise<OutboxItem[]> {
  const all = await getOutbox()
  return all.filter((i) => i.kind === kind && i.targetId === targetId)
}

let flushing = false

export async function flushOutbox(): Promise<void> {
  if (flushing) return
  flushing = true
  try {
    const items = await getOutbox()
    items.sort((a, b) => a.createdAt - b.createdAt)
    for (const it of items) {
      try {
        const url = it.kind === 'channel'
          ? `/api/channels/${it.targetId}/messages`
          : `/api/dm/${it.targetId}/messages`
        await api.post(url, { content: it.content, clientNonce: it.id })
        await removeOutbox(it.id)
      } catch (e: any) {
        if (e?.response) await removeOutbox(it.id)
        else break
      }
    }
  } finally {
    flushing = false
  }
}
