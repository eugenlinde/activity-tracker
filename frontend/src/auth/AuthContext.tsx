import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { tokenStore } from '../api/client'

interface AuthState {
  token: string | null
  userId: number | null
  login: (token: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

function subjectOf(token: string | null): number | null {
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return Number(payload.sub)
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => tokenStore.get())

  const login = useCallback((next: string) => {
    tokenStore.set(next)
    setToken(next)
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setToken(null)
  }, [])

  const value = useMemo(
    () => ({ token, userId: subjectOf(token), login, logout }),
    [token, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
