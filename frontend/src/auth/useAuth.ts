import { createContext, useContext } from 'react'

export interface AuthSession {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
  userId: string
  roles: string[]
}

export interface AuthContextValue {
  session: AuthSession | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, role: 'BUYER' | 'SELLER') => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
