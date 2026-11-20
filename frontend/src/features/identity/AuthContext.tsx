import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import * as identity from './service'
import type { LoginResult, Session } from './types'
import type { Account } from './types'

interface AuthContextValue {
  account: Account | null
  isRestoring: boolean
  login: (email: string, password: string) => Promise<LoginResult>
  verifyMfa: (email: string, code: string) => Promise<Session>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [isRestoring, setRestoring] = useState(true)
  const queryClient = useQueryClient()

  useEffect(() => {
    const unsubscribe = identity.sessionStore.subscribe(setSession)
    void identity.refreshSession().catch(() => {
      // No refresh cookie, an expired session, and an unavailable API all show public access.
    }).finally(() => setRestoring(false))
    return unsubscribe
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    account: session?.account ?? null,
    isRestoring,
    login: identity.login,
    verifyMfa: identity.verifyMfa,
    logout: async () => {
      try {
        await identity.logout()
      } finally {
        queryClient.clear()
      }
    },
  }), [isRestoring, queryClient, session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider.')
  return value
}
