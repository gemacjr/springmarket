/** @vitest-environment jsdom */

import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const storedValues = new Map<string, string>()
const localStorageMock: Storage = {
  get length() {
    return storedValues.size
  },
  clear: () => storedValues.clear(),
  getItem: (key) => storedValues.get(key) ?? null,
  key: (index) => [...storedValues.keys()][index] ?? null,
  removeItem: (key) => {
    storedValues.delete(key)
  },
  setItem: (key, value) => {
    storedValues.set(key, value)
  },
}
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: localStorageMock,
})

const { apiRequest, setAuthTokens } = await import('../api/client')
const { AuthProvider } = await import('./AuthContext')
const { useAuth } = await import('./useAuth')
type AuthSession = import('./useAuth').AuthSession

const SESSION_KEY = 'springmarket.session'

function SessionProbe() {
  const { session } = useAuth()

  async function triggerUnauthorizedRequest() {
    await apiRequest('/api/v1/protected').catch(() => undefined)
  }

  return (
    <>
      <p data-testid="session-state">{session ? 'authenticated' : 'anonymous'}</p>
      <button type="button" onClick={triggerUnauthorizedRequest}>Trigger 401</button>
    </>
  )
}

describe('AuthProvider', () => {
  let container: HTMLDivElement
  let root: Root

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    localStorage.clear()
    window.history.replaceState({}, '', '/login')
    container = document.createElement('div')
    document.body.append(container)
    root = createRoot(container)
  })

  afterEach(() => {
    act(() => root.unmount())
    container.remove()
    vi.unstubAllGlobals()
  })

  it('clears in-memory auth state when an API request returns 401 on the login page', async () => {
    const session: AuthSession = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      userId: 'user-1',
      roles: ['BUYER'],
    }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
    setAuthTokens(session.accessToken, session.refreshToken)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ status: 401, detail: 'Unauthorized' }),
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    ))

    await act(async () => {
      root.render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )
    })
    expect(container.querySelector('[data-testid="session-state"]')?.textContent)
      .toBe('authenticated')

    await act(async () => {
      container.querySelector('button')?.click()
      await Promise.resolve()
    })

    expect(container.querySelector('[data-testid="session-state"]')?.textContent)
      .toBe('anonymous')
  })
})
