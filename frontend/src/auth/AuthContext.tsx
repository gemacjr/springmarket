import { useMemo, useState, type ReactNode } from 'react'
import {
  apiRequest,
  clearAuthTokens,
  getRefreshToken,
  setAuthTokens,
} from '../api/client'
import { AuthContext, type AuthSession } from './useAuth'

const SESSION_KEY = 'springmarket.session'

function readSession(): AuthSession | null {
  const stored = localStorage.getItem(SESSION_KEY)
  if (!stored) return null

  try {
    return JSON.parse(stored) as AuthSession
  } catch {
    localStorage.removeItem(SESSION_KEY)
    clearAuthTokens()
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(readSession)

  function saveSession(nextSession: AuthSession) {
    setSession(nextSession)
    setAuthTokens(nextSession.accessToken, nextSession.refreshToken)
    localStorage.setItem(SESSION_KEY, JSON.stringify(nextSession))
  }

  async function login(email: string, password: string) {
    const nextSession = await apiRequest<AuthSession>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    saveSession(nextSession)
  }

  async function register(email: string, password: string, role: 'BUYER' | 'SELLER') {
    const nextSession = await apiRequest<AuthSession>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, roles: [role] }),
    })
    saveSession(nextSession)
  }

  async function logout() {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) {
        await apiRequest<void>('/api/v1/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        })
      }
    } finally {
      clearAuthTokens()
      localStorage.removeItem(SESSION_KEY)
      setSession(null)
    }
  }

  const value = useMemo(
    () => ({ session, login, register, logout }),
    [session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
