import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest, ApiError } from '../api/client'
import type { Cart, Order } from '../api/types'
import { CheckoutButton } from '../components/CheckoutButton'

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount)
}

export function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null)
  const [placedOrder, setPlacedOrder] = useState<Order | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const controller = new AbortController()
    apiRequest<Cart>('/api/v1/cart', { signal: controller.signal })
      .then(setCart)
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof ApiError ? caught.message : 'Could not load your cart.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  async function setQuantity(productId: string, quantity: number) {
    setError('')
    try {
      const nextCart = quantity === 0
        ? await apiRequest<Cart>(`/api/v1/cart/items/${encodeURIComponent(productId)}`, {
            method: 'DELETE',
          })
        : await apiRequest<Cart>(`/api/v1/cart/items/${encodeURIComponent(productId)}`, {
            method: 'PUT',
            body: JSON.stringify({ quantity }),
          })
      setCart(nextCart)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not update your cart.')
    }
  }

  const total = cart?.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0) ?? 0
  const currency = cart?.items[0]?.currency ?? 'USD'

  return (
    <main className="page-shell">
      <div className="page-heading">
        <div>
          <h1>Your cart</h1>
          <p className="page-intro">Review quantities before placing your order.</p>
        </div>
      </div>
      {loading && <p className="status" role="status">Loading cart…</p>}
      {error && <div className="alert" role="alert">{error}</div>}
      {placedOrder && (
        <div className="success" role="status">
          Order {placedOrder.id} placed. <Link to="/orders">View your orders</Link>
        </div>
      )}
      {!loading && cart?.items.length === 0 && (
        <div className="empty-state">
          <h2>Your cart is empty</h2>
          <p><Link to="/products">Browse the catalog</Link> to add a product.</p>
        </div>
      )}
      {cart && cart.items.length > 0 && (
        <div className="cart-layout">
          <ul className="line-item-list">
            {cart.items.map((item) => (
              <li key={item.productId}>
                <div>
                  <Link to={`/products/${encodeURIComponent(item.productId)}`}>{item.productId}</Link>
                  <p>{formatPrice(item.unitPrice, item.currency)} each</p>
                </div>
                <div className="quantity-controls" aria-label={`Quantity for ${item.productId}`}>
                  <button
                    className="secondary-button"
                    type="button"
                    aria-label={`Decrease ${item.productId} quantity`}
                    onClick={() => setQuantity(item.productId, item.quantity - 1)}
                  >
                    −
                  </button>
                  <span>{item.quantity}</span>
                  <button
                    className="secondary-button"
                    type="button"
                    aria-label={`Increase ${item.productId} quantity`}
                    onClick={() => setQuantity(item.productId, item.quantity + 1)}
                  >
                    +
                  </button>
                </div>
                <strong>{formatPrice(item.unitPrice * item.quantity, item.currency)}</strong>
              </li>
            ))}
          </ul>
          <aside className="order-summary">
            <p>Total</p>
            <strong>{formatPrice(total, currency)}</strong>
            <CheckoutButton
              disabled={Boolean(placedOrder)}
              onPlaced={(order) => {
                setPlacedOrder(order)
                setCart((current) => current ? { ...current, items: [] } : current)
              }}
            />
          </aside>
        </div>
      )}
    </main>
  )
}
