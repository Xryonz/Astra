import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { UserPublic } from '@astra/types'
import { sentry } from '@/lib/sentry'

export type { UserPublic }

interface AuthState {
  user:            UserPublic | null
  accessToken:     string | null
  isAuthenticated: boolean

  setAuth:        (user: UserPublic, accessToken: string) => void
  setAccessToken: (token: string) => void
  updateUser:     (patch: Partial<UserPublic>) => void
  logout:         () => void
}

// accessToken nunca persiste (XSS-safe; revive via refresh em cold load).
// user + isAuthenticated persistem em localStorage — sobrevivem a tab discard
// que o Chrome faz pra liberar RAM durante jogos fullscreen. Sem isso o
// usuário voltava deslogado mesmo com refresh válido em mão.
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user:            null,
      accessToken:     null,
      isAuthenticated: false,

      setAuth: (user, accessToken) => {
        sentry.setUser(user.id)
        set({ user, accessToken, isAuthenticated: true })
      },

      setAccessToken: (accessToken) => set({ accessToken }),

      updateUser: (patch) =>
        set((s) => ({ user: s.user ? { ...s.user, ...patch } : s.user })),

      logout: () => {
        sentry.setUser(null)
        set({ user: null, accessToken: null, isAuthenticated: false })
      },
    }),
    {
      name:    'astra-auth',
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({
        user:            s.user,
        isAuthenticated: s.isAuthenticated,
      }),
      onRehydrateStorage: () => (state) => {
        if (state?.user?.id) sentry.setUser(state.user.id)
      },
    }
  )
)