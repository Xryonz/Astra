import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { visualizer } from 'rollup-plugin-visualizer'
import { sentryVitePlugin } from '@sentry/vite-plugin'
import type { Plugin } from 'vite'

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
const SENTRY_UPLOAD  = !!(
  process.env.SENTRY_AUTH_TOKEN &&
  process.env.SENTRY_ORG &&
  process.env.SENTRY_PROJECT
)

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    ANALYZE && visualizer({
      filename: 'dist/stats.html',
      template: 'treemap',
      gzipSize: true,
      brotliSize: true,
      open: false,
    }),
    sentryVitePlugin({
      authToken:  process.env.SENTRY_AUTH_TOKEN,
      org:        process.env.SENTRY_ORG,
      project:    process.env.SENTRY_PROJECT,
      release: {
        name: process.env.VITE_RELEASE ?? process.env.GITHUB_SHA ?? undefined,
      },
      sourcemaps: {
        filesToDeleteAfterUpload: ['./dist/**/*.map'],
      },
      disable: !SENTRY_UPLOAD,
      silent:  true,
    }),
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
  esbuild: {
    drop: process.env.NODE_ENV === 'production' ? ['console', 'debugger'] : [],
  },
  build: {
    sourcemap: 'hidden',
    target: 'es2022',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
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
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
})
