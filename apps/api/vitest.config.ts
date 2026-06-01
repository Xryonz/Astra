import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    setupFiles: ['./src/__tests__/setup.ts'],
    // Roda em série pra evitar conflitos com mocks globais de Redis
    fileParallelism: false,
    testTimeout: 10_000,
    coverage: {
      reporter: ['text', 'html'],
      include: ['src/lib/**/*.ts', 'src/middleware/**/*.ts'],
      exclude: ['src/**/*.test.ts', 'src/__tests__/**'],
    },
  },
})
