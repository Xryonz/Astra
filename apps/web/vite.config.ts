import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { visualizer } from 'rollup-plugin-visualizer'
import { sentryVitePlugin } from '@sentry/vite-plugin'
import type { Plugin } from 'vite'

/**
 * Loga top-10 chunks ordenados por tamanho gzipped após build.
 * Sem fail-hard — só visibility em CI logs pra detectar regressões.
 * Threshold de alerta: 250KB gzip por chunk (acima imprime ⚠️).
 */
function bundleSizeLogger(): Plugin {
  return {
    name: 'astra-bundle-size-logger',
    apply: 'build',
    async writeBundle(_options, bundle) {
      const { gzipSync } = await import('node:zlib')
      const items: { name: string; raw: number; gz: number }[] = []
      for (const [name, asset] of Object.entries(bundle)) {
        if (asset.type !== 'chunk') continue
        const src = asset.code
        items.push({ name, raw: Buffer.byteLength(src), gz: gzipSync(src).length })
      }
      items.sort((a, b) => b.gz - a.gz)
      const top = items.slice(0, 10)
      // eslint-disable-next-line no-console
      console.log('\n📦 Top 10 chunks (gzipped):')
      for (const it of top) {
        const gzKb = (it.gz / 1024).toFixed(1)
        const rawKb = (it.raw / 1024).toFixed(1)
        const flag = it.gz > 250 * 1024 ? ' ⚠️' : ''
        // eslint-disable-next-line no-console
        console.log(`  ${gzKb.padStart(7)} KB gz  (${rawKb} KB raw)  ${it.name}${flag}`)
      }
      const totalGz = items.reduce((s, i) => s + i.gz, 0)
      // eslint-disable-next-line no-console
      console.log(`  ─────  Total: ${(totalGz / 1024).toFixed(1)} KB gzipped\n`)
    },
  }
}

const ANALYZE        = process.env.ANALYZE === '1'
// Sentry upload só roda em CI/build remoto quando os 3 env vars estão setados.
// Local skipa silenciosamente — sem fricção pra dev. Em prod (Railway/Vercel),
// setar SENTRY_AUTH_TOKEN + SENTRY_ORG + SENTRY_PROJECT habilita upload + release.
const SENTRY_UPLOAD  = !!(
  process.env.SENTRY_AUTH_TOKEN &&
  process.env.SENTRY_ORG &&
  process.env.SENTRY_PROJECT
)

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // Só liga quando ANALYZE=1 npm run build — gera dist/stats.html
    ANALYZE && visualizer({
      filename: 'dist/stats.html',
      template: 'treemap',
      gzipSize: true,
      brotliSize: true,
      open: false,
    }),
    // Sentry: upload sourcemaps + cria release. Plugin é DEVE ser o último
    // pra ver os assets finais do Rollup. `disable` desliga em local.
    sentryVitePlugin({
      authToken:  process.env.SENTRY_AUTH_TOKEN,
      org:        process.env.SENTRY_ORG,
      project:    process.env.SENTRY_PROJECT,
      release: {
        name: process.env.VITE_RELEASE ?? process.env.GITHUB_SHA ?? undefined,
      },
      sourcemaps: {
        // Sobe os .map (hidden) e DELETA do dist/ pra browser não baixar.
        filesToDeleteAfterUpload: ['./dist/**/*.map'],
      },
      disable: !SENTRY_UPLOAD,
      silent:  true,
    }),
    // Roda sempre em build prod — imprime top chunks no console pra CI capturar.
    bundleSizeLogger(),
  ].filter(Boolean),
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
  },
  optimizeDeps: {
    include: ['@astra/types'],
  },
  // Strip console.* + debugger em build de prod. dev mantém pra debug.
  esbuild: {
    drop: process.env.NODE_ENV === 'production' ? ['console', 'debugger'] : [],
  },
  build: {
    // Sourcemaps "hidden": geram .map mas browser não carrega automaticamente.
    // Sentry sobe os maps via release pra symbolicar stack traces; user não
    // baixa o JS map (~30% economia transfer no production user).
    sourcemap: 'hidden',
    // Minificação default do Vite é esbuild — já strippa console em prod
    // via esbuild.drop acima.
    // Target: navegadores modernos (top 80% global) — sem polyfills pesados.
    target: 'es2022',
    // Chunks grandes não-essenciais devem ficar fora do main bundle.
    // manualChunks identifica vendors gordos e isola — main fica magro pro initial load.
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          // Vendors pesados em chunks isolados. Shiki/emoji-mart NÃO entram aqui
          // pra cada lang/data ficar seu próprio chunk (lazy granular).
          // Engine do shiki é tão pequeno que cai no chunk do dynamic-import caller.
          if (id.includes('livekit-client') || id.includes('@livekit'))      return 'vendor-livekit'
          if (id.includes('motion'))                                          return 'vendor-motion'
          if (id.includes('@sentry'))                                         return 'vendor-sentry'
          if (id.includes('react-colorful'))                                  return 'vendor-colorful'
          if (id.includes('date-fns'))                                        return 'vendor-datefns'
          if (id.includes('@radix-ui'))                                       return 'vendor-radix'
          if (id.includes('lucide-react'))                                    return 'vendor-icons'
          if (id.includes('socket.io-client'))                                return 'vendor-socket'
          if (id.includes('react-router'))                                    return 'vendor-router'
          if (id.includes('@tanstack/react-query'))                           return 'vendor-query'
          if (id.includes('zod'))                                             return 'vendor-zod'
          // react + react-dom ficam no main (necessários sempre)
        },
      },
    },
    // Avisar quando chunk > 600KB (default é 500, mas livekit/shiki ainda passam)
    chunkSizeWarningLimit: 600,
  },
})