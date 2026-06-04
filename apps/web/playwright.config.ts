import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright config — smoke tests do front em chromium.
 *
 * webServer: faz npm run dev:fast (skip predev pra não matar a sessão
 * do user) e aguarda :5173 responder. Use `npm run test:e2e` na raiz.
 *
 * Acrescentar suite com auth real: precisa de fixture pro DB + bypass
 * OAuth via API. Hoje cobre só golden path de "app monta".
 */
export default defineConfig({
  testDir:     './tests/e2e',
  fullyParallel: true,
  forbidOnly:  !!process.env.CI,
  retries:     process.env.CI ? 2 : 0,
  workers:     process.env.CI ? 1 : undefined,
  reporter:    'list',

  use: {
    baseURL:   'http://localhost:5173',
    trace:     'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  webServer: {
    command:      'npm run dev:fast',
    cwd:          '../..',
    url:          'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout:      120_000,
    stdout:       'pipe',
    stderr:       'pipe',
  },
})
