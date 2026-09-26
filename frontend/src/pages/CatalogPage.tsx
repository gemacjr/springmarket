import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest, ApiError } from '../api/client'
import type { ProductPage } from '../api/types'

const PAGE_SIZE = 12

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  }).format(amount)
}

export function CatalogPage() {
  const [page, setPage] = useState(0)
  const [products, setProducts] = useState<ProductPage | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const controller = new AbortController()

    apiRequest<ProductPage>(`/api/v1/products?page=${page}&size=${PAGE_SIZE}`, {
      signal: controller.signal,
    })
      .then(setProducts)
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof ApiError ? caught.message : 'Could not load products.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [page])

  function changePage(nextPage: number) {
    setLoading(true)
    setError('')
    setPage(nextPage)
  }

  return (
    <main className="page-shell">
      <header className="page-heading">
        <div>
          <h1>Catalog</h1>
          <p className="page-intro">Browse what SpringMarket sellers have listed.</p>
        </div>
        {products && <p className="result-count">{products.totalElements} products</p>}
      </header>

      {loading && <p className="status" role="status">Loading products…</p>}
      {error && <div className="alert" role="alert">{error}</div>}
      {!loading && !error && products?.content.length === 0 && (
        <div className="empty-state">
          <h2>No products yet</h2>
          <p>Products will appear here after sellers add them.</p>
        </div>
      )}
      {!loading && !error && products && products.content.length > 0 && (
        <>
          <div className="product-grid">
            {products.content.map((product) => (
              <article className="product-card" key={product.id}>
                <p className="category">{product.category}</p>
                <h2><Link to={`/products/${product.id}`}>{product.name}</Link></h2>
                <p className="product-description">{product.description}</p>
                <p className="price">{formatPrice(product.priceAmount, product.currency)}</p>
              </article>
            ))}
          </div>
          {products.totalPages > 1 && (
            <nav className="pagination" aria-label="Catalog pages">
              <button
                type="button"
                className="secondary-button"
                disabled={products.first}
                onClick={() => changePage(page - 1)}
              >
                Previous
              </button>
              <span>Page {products.number + 1} of {products.totalPages}</span>
              <button
                type="button"
                className="secondary-button"
                disabled={products.last}
                onClick={() => changePage(page + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}
    </main>
  )
}
