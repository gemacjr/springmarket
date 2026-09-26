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

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function authValue(role: string): AuthContextValue {
  const session: AuthSession = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    userId: 'user-1',
    roles: [role],
  }
  return {
    session,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }
}

async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

describe('task 13 pages', () => {
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
    vi.unstubAllGlobals()
  })

  async function renderRoute(path: string, role: string) {
    await act(async () => {
      root.render(
        <AuthContext.Provider value={authValue(role)}>
          <MemoryRouter initialEntries={[path]}>
            <App />
          </MemoryRouter>
        </AuthContext.Provider>,
      )
    })
    await settle()
  }

  it('adds an active product to the buyer cart', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        id: 'product-1',
        sellerId: 'seller-1',
        name: 'Coffee',
        description: 'Single-origin beans',
        priceAmount: 18.5,
        currency: 'USD',
        category: 'GROCERY',
        active: true,
        createdAt: '2026-09-26T12:00:00Z',
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 'cart-1',
        userId: 'user-1',
        items: [{ productId: 'product-1', quantity: 1, unitPrice: 18.5, currency: 'USD' }],
        updatedAt: '2026-09-26T12:00:00Z',
      }))
    vi.stubGlobal('fetch', fetchMock)

    await renderRoute('/products/product-1', 'BUYER')
    await act(async () => {
      container.querySelector<HTMLButtonElement>('button[data-action="add-to-cart"]')?.click()
    })
    await settle()

    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/v1/cart/items',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ productId: 'product-1', quantity: 1 }),
      }),
    )
    expect(container.textContent).toContain('Added to cart')
  })

  it('checks out the current cart and shows the created order', async () => {
    const cart = {
      id: 'cart-1',
      userId: 'user-1',
      items: [{ productId: 'product-1', quantity: 2, unitPrice: 18.5, currency: 'USD' }],
      updatedAt: '2026-09-26T12:00:00Z',
    }
    const order = {
      id: 'order-1',
      buyerId: 'user-1',
      status: 'PENDING_PAYMENT',
      totalAmount: 37,
      currency: 'USD',
      items: cart.items,
      createdAt: '2026-09-26T12:01:00Z',
      updatedAt: '2026-09-26T12:01:00Z',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(cart))
      .mockResolvedValueOnce(jsonResponse(order, 201))
    vi.stubGlobal('fetch', fetchMock)

    await renderRoute('/cart', 'BUYER')
    expect(container.textContent).toContain('product-1')
    expect(container.textContent).toContain('$37.00')

    await act(async () => {
      container.querySelector<HTMLButtonElement>('button[data-action="checkout"]')?.click()
    })
    await settle()

    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/v1/orders',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(container.textContent).toContain('Order order-1 placed')
  })

  it('lists the buyer orders', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([{
      id: 'order-42',
      buyerId: 'user-1',
      status: 'PAID',
      totalAmount: 24,
      currency: 'USD',
      items: [{ productId: 'tea-1', quantity: 2, unitPrice: 12, currency: 'USD' }],
      createdAt: '2026-09-26T12:00:00Z',
      updatedAt: '2026-09-26T12:00:00Z',
    }])))

    await renderRoute('/orders', 'BUYER')

    expect(container.textContent).toContain('order-42')
    expect(container.textContent).toContain('PAID')
    expect(container.textContent).toContain('$24.00')
  })

  it('creates a seller product with initial stock', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      id: 'new-product',
      sellerId: 'user-1',
      name: 'Tea',
      description: 'Green tea',
      priceAmount: 12,
      currency: 'USD',
      category: 'GROCERY',
      active: true,
      createdAt: '2026-09-26T12:00:00Z',
    }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await renderRoute('/seller/products', 'SELLER')
    const values: Record<string, string> = {
      name: 'Tea',
      description: 'Green tea',
      priceAmount: '12',
      category: 'GROCERY',
      initialStock: '8',
    }
    for (const [name, value] of Object.entries(values)) {
      const input = container.querySelector<HTMLInputElement | HTMLTextAreaElement>(`[name="${name}"]`)
      if (input) {
        input.value = value
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }
    }
    await act(async () => {
      container.querySelector<HTMLFormElement>('form')?.requestSubmit()
    })
    await settle()

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/products',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ ...values, priceAmount: 12, initialStock: 8, currency: 'USD' }),
      }),
    )
    expect(container.textContent).toContain('Tea created')
  })

  it('lists all orders and triggers a dated daily sales report for admins', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{
        id: 'order-admin',
        buyerId: 'buyer-1',
        status: 'PAID',
        totalAmount: 60,
        currency: 'USD',
        items: [],
        createdAt: '2026-09-25T12:00:00Z',
        updatedAt: '2026-09-25T12:00:00Z',
      }]))
      .mockResolvedValueOnce(jsonResponse({
        executionId: 7,
        status: 'STARTING',
        reportDate: '2026-09-25',
      }))
    vi.stubGlobal('fetch', fetchMock)

    await renderRoute('/admin/orders', 'ADMIN')
    expect(container.textContent).toContain('order-admin')
    const dateInput = container.querySelector<HTMLInputElement>('input[name="reportDate"]')
    if (dateInput) {
      dateInput.value = '2026-09-25'
      dateInput.dispatchEvent(new Event('input', { bubbles: true }))
    }
    await act(async () => {
      container.querySelector<HTMLButtonElement>('button[data-action="daily-report"]')?.click()
    })
    await settle()

    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/v1/admin/reports/daily-sales?date=2026-09-25',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(container.textContent).toContain('Report 7 started')
  })
})
