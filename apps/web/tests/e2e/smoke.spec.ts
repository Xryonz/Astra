import { test, expect } from '@playwright/test'

test.describe('smoke', () => {
  test('login page renderiza sem ErrorBoundary', async ({ page }) => {
    await page.goto('/login')

    await expect(page.locator('svg').first()).toBeVisible({ timeout: 10_000 })

    await expect(page.getByText('Algo interrompeu')).toHaveCount(0)

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

test.describe('mobile (iPhone SE 375x667)', () => {
  test.use({ viewport: { width: 375, height: 667 } })

  test('login sem horizontal overflow + aside editorial escondido', async ({ page }) => {
    await page.goto('/login')

    const hasHorizontalScroll = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth
    })
    expect(hasHorizontalScroll).toBe(false)

    const asideText = page.getByText('Onde palavras encontram silêncio')
    await expect(asideText).toHaveCount(0)
  })

  test('register sem horizontal overflow', async ({ page }) => {
    await page.goto('/register')
    const hasOverflow = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth
    })
    expect(hasOverflow).toBe(false)
  })

  test('botão CTA primário tem touch target >= 44px (a11y mobile)', async ({ page }) => {
    await page.goto('/login')

    const submit = page.getByRole('button', { name: /entrar|sign in/i }).first()
    await expect(submit).toBeVisible()

    const box = await submit.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.height).toBeGreaterThanOrEqual(44)
  })
})
