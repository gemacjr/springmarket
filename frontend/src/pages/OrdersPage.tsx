import { useEffect, useState } from 'react'
import { apiRequest, ApiError } from '../api/client'
import type { Order } from '../api/types'

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount)
}

export function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const controller = new AbortController()
    apiRequest<Order[]>('/api/v1/orders/mine', { signal: controller.signal })
      .then(setOrders)
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof ApiError ? caught.message : 'Could not load your orders.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  return (
    <main className="page-shell">
      <div className="page-heading">
        <div>
          <h1>Your orders</h1>
          <p className="page-intro">Track purchases and payment status.</p>
        </div>
        {!loading && <p className="result-count">{orders.length} {orders.length === 1 ? 'order' : 'orders'}</p>}
      </div>
      {loading && <p className="status" role="status">Loading orders…</p>}
      {error && <div className="alert" role="alert">{error}</div>}
      {!loading && !error && orders.length === 0 && (
        <div className="empty-state">
          <h2>No orders yet</h2>
          <p>Your completed checkouts will appear here.</p>
        </div>
      )}
      <div className="order-list">
        {orders.map((order) => (
          <article className="order-card" key={order.id}>
            <div className="order-card-heading">
              <div>
                <h2>{order.id}</h2>
                <p>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(order.createdAt))}</p>
              </div>
              <span className="status-badge">{order.status}</span>
            </div>
            <p>{order.items.reduce((sum, item) => sum + item.quantity, 0)} items</p>
            <strong>{formatPrice(order.totalAmount, order.currency)}</strong>
          </article>
        ))}
      </div>
    </main>
  )
}
