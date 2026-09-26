/** @vitest-environment jsdom */

import { beforeEach, describe, expect, it, vi } from 'vitest'

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

describe('apiRequest refresh handling', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.resetModules()
  })

  it('refreshes a rejected protected request once and retries it with the new access token', async () => {
    const refreshedSession = {
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      userId: 'user-1',
      roles: ['BUYER'],
    }
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json(refreshedSession))
      .mockResolvedValueOnce(Response.json({ id: 'order-1' }))
    vi.stubGlobal('fetch', fetchMock)

    const { apiRequest, setAuthTokens } = await import('./client')
    setAuthTokens('expired-access-token', 'old-refresh-token')
    localStorage.setItem(
      'springmarket.session',
      JSON.stringify({ ...refreshedSession, accessToken: 'expired-access-token', refreshToken: 'old-refresh-token' }),
    )

    await expect(apiRequest<{ id: string }>('/api/v1/orders/order-1')).resolves.toEqual({ id: 'order-1' })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/v1/auth/refresh')
    expect(fetchMock.mock.calls[1]?.[1]?.body).toBe(JSON.stringify({ refreshToken: 'old-refresh-token' }))
    expect(new Headers(fetchMock.mock.calls[2]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer new-access-token',
    )
    expect(JSON.parse(localStorage.getItem('springmarket.session') ?? '{}')).toEqual(refreshedSession)
  })
})
