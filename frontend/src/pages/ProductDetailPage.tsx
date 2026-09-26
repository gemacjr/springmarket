import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiRequest, ApiError } from '../api/client'
import type { Cart, Product } from '../api/types'
import { useAuth } from '../auth/useAuth'

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(amount)
}

export function ProductDetailPage() {
  const { id } = useParams()
  const { session } = useAuth()
  const [product, setProduct] = useState<Product | null>(null)
  const [error, setError] = useState('')
  const [cartMessage, setCartMessage] = useState('')
  const [adding, setAdding] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return

    const controller = new AbortController()
    apiRequest<Product>(`/api/v1/products/${encodeURIComponent(id)}`, {
      signal: controller.signal,
    })
      .then(setProduct)
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof ApiError ? caught.message : 'Could not load this product.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [id])

  async function addToCart() {
    if (!product) return
    setAdding(true)
    setError('')
    setCartMessage('')
    try {
      await apiRequest<Cart>('/api/v1/cart/items', {
        method: 'POST',
        body: JSON.stringify({ productId: product.id, quantity: 1 }),
      })
      setCartMessage('Added to cart.')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not add this product to your cart.')
    } finally {
      setAdding(false)
    }
  }

  return (
    <main className="page-shell detail-page">
      <Link className="back-link" to="/products">Back to catalog</Link>
      {!id && <div className="alert" role="alert">Product ID is missing.</div>}
      {id && loading && <p className="status" role="status">Loading product…</p>}
      {error && <div className="alert" role="alert">{error}</div>}
      {product && (
        <article className="product-detail">
          <p className="category">{product.category}</p>
          <h1>{product.name}</h1>
          <p className="detail-price">{formatPrice(product.priceAmount, product.currency)}</p>
          <p className="detail-description">{product.description}</p>
          {!product.active && <p className="availability">This product is currently unavailable.</p>}
          {cartMessage && <div className="success" role="status">{cartMessage} <Link to="/cart">View cart</Link></div>}
          {session?.roles.includes('BUYER') && product.active && (
            <button
              data-action="add-to-cart"
              type="button"
              disabled={adding}
              onClick={addToCart}
            >
              {adding ? 'Adding…' : 'Add to cart'}
            </button>
          )}
          <dl>
            <div>
              <dt>Listed</dt>
              <dd>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(product.createdAt))}</dd>
            </div>
            <div>
              <dt>Product ID</dt>
              <dd>{product.id}</dd>
            </div>
          </dl>
        </article>
      )}
    </main>
  )
}
