/** @vitest-environment jsdom */

import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthContextValue, AuthSession } from './auth/useAuth'

const storedValues = new Map<string, string>()
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    get length() {
      return storedValues.size
    },
    clear: () => storedValues.clear(),
    getItem: (key: string) => storedValues.get(key) ?? null,
    key: (index: number) => [...storedValues.keys()][index] ?? null,
    removeItem: (key: string) => storedValues.delete(key),
    setItem: (key: string, value: string) => storedValues.set(key, value),
  } satisfies Storage,
})

const { default: App } = await import('./App')
const { AuthContext } = await import('./auth/useAuth')

function sessionWithRole(role: string): AuthSession {
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    userId: 'user-1',
    roles: [role],
  }
}

describe('role navigation', () => {
  let container: HTMLDivElement
  let root: Root

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    container = document.createElement('div')
    document.body.append(container)
    root = createRoot(container)
  })

  afterEach(() => {
    act(() => root.unmount())
    container.remove()
  })

  async function renderFor(role: string) {
    const value: AuthContextValue = {
      session: sessionWithRole(role),
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
    }

    await act(async () => {
      root.render(
        <AuthContext.Provider value={value}>
          <MemoryRouter>
            <App />
          </MemoryRouter>
        </AuthContext.Provider>,
      )
    })
  }

  it('shows cart and orders to buyers', async () => {
    await renderFor('BUYER')

    expect(container.querySelector('a[href="/cart"]')?.textContent).toBe('Cart')
    expect(container.querySelector('a[href="/orders"]')?.textContent).toBe('Orders')
    expect(container.querySelector('a[href="/seller/products"]')).toBeNull()
    expect(container.querySelector('a[href="/admin/orders"]')).toBeNull()
  })

  it('shows product creation to sellers', async () => {
    await renderFor('SELLER')

    expect(container.querySelector('a[href="/seller/products"]')?.textContent).toBe('Sell')
    expect(container.querySelector('a[href="/cart"]')).toBeNull()
  })

  it('shows order administration to admins', async () => {
    await renderFor('ADMIN')

    expect(container.querySelector('a[href="/admin/orders"]')?.textContent).toBe('Admin')
    expect(container.querySelector('a[href="/cart"]')).toBeNull()
  })
})
