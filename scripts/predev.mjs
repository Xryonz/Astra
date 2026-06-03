#!/usr/bin/env node
/**
 * predev — pre-flight checks pra evitar bugs silenciosos no dev.
 *
 * Roda automaticamente antes de `npm run dev` (configurado em package.json).
 * Garante:
 *   1. Portas 3001 (api) e 5173 (web) livres — mata orphans
 *   2. .env do api existe + tem vars obrigatórias
 *   3. DB schema atualizado (roda manualMigrate)
 *
 * Cross-platform: funciona em Windows (PowerShell), Linux, macOS.
 *
 * Por que existe: bug do ProfileCard (2026-06-02) foi causado por orphan
 * api process com schema antigo. predev mata esse padrão.
 */
import { execSync, spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..')
const IS_WIN = process.platform === 'win32'

const c = {
  reset: '\x1b[0m', dim: '\x1b[2m', red: '\x1b[31m',
  green: '\x1b[32m', yellow: '\x1b[33m', blue: '\x1b[34m', bold: '\x1b[1m',
}
const log = {
  step: (s) => console.log(`${c.blue}▸${c.reset} ${s}`),
  ok:   (s) => console.log(`  ${c.green}✓${c.reset} ${c.dim}${s}${c.reset}`),
  warn: (s) => console.log(`  ${c.yellow}⚠${c.reset} ${s}`),
  err:  (s) => console.log(`  ${c.red}✗${c.reset} ${s}`),
}

// ──────────────────────────────────────────────────────────────
// 1. Portas: mata processos em 3001 (api) e 5173 (web)
// ──────────────────────────────────────────────────────────────
function killOrphansOnPort(port) {
  try {
    if (IS_WIN) {
      // Windows: netstat -ano | findstr PORT → extrai PID → taskkill
      const out = execSync(`netstat -ano | findstr ":${port} "`, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
      const pids = new Set()
      for (const line of out.split('\n')) {
        if (!line.includes('LISTENING')) continue
        const parts = line.trim().split(/\s+/)
        const pid = parts[parts.length - 1]
        if (pid && /^\d+$/.test(pid)) pids.add(pid)
      }
      if (pids.size === 0) { log.ok(`:${port} livre`); return }
      for (const pid of pids) {
        try {
          execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore' })
          log.warn(`:${port} ocupada (pid ${pid}) — matei`)
        } catch {
          log.err(`:${port} pid ${pid} sem acesso — abra Task Manager + finalize manualmente`)
        }
      }
    } else {
      // Unix: lsof -ti:PORT → kill
      const out = execSync(`lsof -ti:${port}`, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
      if (!out) { log.ok(`:${port} livre`); return }
      for (const pid of out.split('\n')) {
        try {
          execSync(`kill -9 ${pid}`, { stdio: 'ignore' })
          log.warn(`:${port} ocupada (pid ${pid}) — matei`)
        } catch {
          log.err(`:${port} pid ${pid} sem acesso`)
        }
      }
    }
  } catch {
    // netstat/lsof retornou erro = nada listening = OK
    log.ok(`:${port} livre`)
  }
}

log.step('Checando portas')
killOrphansOnPort(3001)
killOrphansOnPort(5173)

// ──────────────────────────────────────────────────────────────
// 2. .env validação (apps/api/.env)
// ──────────────────────────────────────────────────────────────
log.step('Validando apps/api/.env')
const envPath = resolve(ROOT, 'apps/api/.env')
if (!existsSync(envPath)) {
  log.err('apps/api/.env não existe')
  console.log(`  ${c.dim}Copie apps/api/.env.example pra apps/api/.env e preencha os valores${c.reset}`)
  process.exit(1)
}

const envContent = readFileSync(envPath, 'utf8')
const REQUIRED = [
  'DATABASE_URL',
  'JWT_ACCESS_SECRET',
  'JWT_REFRESH_SECRET',
  'GOOGLE_CLIENT_ID',
  'GOOGLE_CLIENT_SECRET',
  'CLIENT_URL',
]
const missing = REQUIRED.filter((k) => {
  const m = envContent.match(new RegExp(`^${k}\\s*=\\s*(.+)$`, 'm'))
  return !m || !m[1].trim() || m[1].trim() === '""' || m[1].trim() === "''"
})
if (missing.length > 0) {
  log.err(`vars obrigatórias faltando: ${missing.join(', ')}`)
  process.exit(1)
}
log.ok(`${REQUIRED.length}/${REQUIRED.length} vars obrigatórias presentes`)

// ──────────────────────────────────────────────────────────────
// 3. DB migration (manualMigrate)
// ──────────────────────────────────────────────────────────────
log.step('Aplicando migrations')
// Cross-platform: passa comando inteiro como string única com shell:true.
// Não dispara DEP0190 (args não são separados) — args são literais hardcoded
// neste arquivo, nenhum input do user envolvido.
const migrate = spawnSync('npx ts-node src/db/manualMigrate.ts', {
  cwd: resolve(ROOT, 'apps/api'),
  stdio: 'inherit',
  shell: true,
})
if (migrate.status !== 0) {
  log.err('migration falhou — abortando dev')
  process.exit(1)
}
log.ok('schema sincronizado')

console.log(`\n${c.green}${c.bold}✓ predev OK${c.reset} ${c.dim}— bootando dev server${c.reset}\n`)
