/**
 * SpotifyNowPlaying — widget compacto que mostra a música atual.
 *
 * Otimizações pra não estourar perf:
 *  - useQuery com staleTime 30s (Spotify rate limit = 1 req/30s seguro)
 *  - enabled: false quando server diz que Spotify não tá habilitado
 *  - render condicional: nulo se nada tocando OU user não conectado
 *  - sem polling agressivo: refetch só quando query é re-mounted ou stale
 *
 * Backend (rota stub): GET /api/profile/:userId/spotify/now-playing
 * retorna null hoje. Quando SPOTIFY_CLIENT_ID for setado +
 * implementarmos OAuth, retorna { track, artist, album, albumArt,
 * progressMs, durationMs, externalUrl }.
 */
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

interface NowPlaying {
  track:       string
  artist:      string
  album?:      string
  albumArt?:   string
  externalUrl: string
  progressMs?: number
  durationMs?: number
}

interface Props {
  userId:    string
  accentColor: string
  /** Quando user nunca conectou Spotify → não chama API. */
  enabled?:  boolean
}

export function SpotifyNowPlaying({ userId, accentColor, enabled = true }: Props) {
  // Status global (env-driven): só faz query se servidor tem creds
  const statusQ = useQuery<{ enabled: boolean }>({
    queryKey: ['spotify-status'],
    queryFn:  async () => (await api.get('/api/profile/spotify/status')).data.data,
    staleTime: 5 * 60_000, // cached 5min — env não muda runtime
  })

  const canFetch = enabled && statusQ.data?.enabled === true
  const nowQ = useQuery<NowPlaying | null>({
    queryKey: ['spotify-now', userId],
    queryFn:  async () => (await api.get(`/api/profile/${userId}/spotify/now-playing`)).data.data,
    enabled:   canFetch,
    staleTime: 30_000,
    refetchInterval: canFetch ? 30_000 : false, // só polla quando habilitado
    refetchIntervalInBackground: false,         // pausa quando aba escondida
  })

  if (!nowQ.data) return null
  const np  = nowQ.data
  const pct = np.durationMs && np.progressMs
    ? Math.min(100, Math.max(0, (np.progressMs / np.durationMs) * 100))
    : 0

  return (
    <div>
      <span className="ed-label block mb-2">— Tocando agora</span>
      <a
        href={np.externalUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-2.5 p-2.5 rounded-lg border bg-(--raised)/40 hover:scale-[1.01] transition-transform"
        style={{
          borderColor: 'color-mix(in srgb, ' + accentColor + ' 35%, transparent)',
        }}
      >
        {np.albumArt && (
          <img
            src={np.albumArt}
            alt=""
            loading="lazy"
            decoding="async"
            className="size-10 rounded-md object-cover shrink-0"
          />
        )}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-(--text-1) m-0 truncate">{np.track}</p>
          <p className="text-[11px] text-(--text-3) m-0 truncate">{np.artist}</p>
          {np.durationMs && (
            <div className="mt-1.5 h-0.5 rounded-full bg-(--border) overflow-hidden">
              <div
                className="h-full transition-[width] duration-1000 ease-linear"
                style={{ width: `${pct}%`, background: accentColor }}
              />
            </div>
          )}
        </div>
      </a>
    </div>
  )
}
