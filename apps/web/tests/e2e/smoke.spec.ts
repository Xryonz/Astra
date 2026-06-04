import { test, expect } from '@playwright/test'

/**
 * Smoke — garante que o boot do app não quebrou.
 *
 * Caça especificamente os bugs SILENCIOSOS que pegamos no overhaul:
 *  - CSS vars indefinidas → backgrounds transparentes
 *  - Tailwind v4 sem plugin animate → componentes sem animação visível
 *  - Routes não montando devido a erro de import
 *  - ErrorBoundary disparando (mostraria "Algo interrompeu")
 *
 * Não cobre auth flow — adicionar quando precisar (fixture DB + bypass OAuth).
 */
test.describe('smoke', () => {
  test('login page renderiza sem ErrorBoundary', async ({ page }) => {
    await page.goto('/login')

    // Logo deve aparecer (SVG inline → não depende de fetch)
    await expect(page.locator('svg').first()).toBeVisible({ timeout: 10_000 })

    // ErrorBoundary fallback NÃO pode estar visível
    await expect(page.getByText('Algo interrompeu')).toHaveCount(0)

    // Botão de login Google ou form de email visível
    const hasGoogleBtn = await page.getByText(/google/i).count()
    const hasEmailField = await page.getByPlaceholder(/voce@exemplo/i).count()
    expect(hasGoogleBtn + hasEmailField).toBeGreaterThan(0)
  })

  test('app raiz redireciona pra /login quando não autenticado', async ({ page }) => {
    await page.goto('/')
    await page.waitForURL(/\/login$/, { timeout: 10_000 })
    expect(page.url()).toMatch(/\/login$/)
  })

  test('CSS vars críticas estão definidas no :root', async ({ page }) => {
    await page.goto('/login')

    // Vars que falharam silenciosamente no bug do ProfileCard.
    // computed style do html deve retornar valor pra cada.
    const checked = await page.evaluate(() => {
      const style = getComputedStyle(document.documentElement)
      return {
        overlay:  style.getPropertyValue('--overlay').trim(),
        accent:   style.getPropertyValue('--accent').trim(),
        text1:    style.getPropertyValue('--text-1').trim(),
        base:     style.getPropertyValue('--base').trim(),
      }
    })

    expect(checked.overlay).not.toBe('')
    expect(checked.accent).not.toBe('')
    expect(checked.text1).not.toBe('')
    expect(checked.base).not.toBe('')
  })
})
