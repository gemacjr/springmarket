import { useState, type FormEvent } from 'react'
import { apiRequest, ApiError } from '../api/client'
import type { Product } from '../api/types'

export function SellerProductPage() {
  const [created, setCreated] = useState<Product | null>(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function createProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setSubmitting(true)
    setError('')
    setCreated(null)
    try {
      const product = await apiRequest<Product>('/api/v1/products', {
        method: 'POST',
        body: JSON.stringify({
          name: data.get('name'),
          description: data.get('description'),
          priceAmount: Number(data.get('priceAmount')),
          category: data.get('category'),
          initialStock: Number(data.get('initialStock')),
          currency: 'USD',
        }),
      })
      setCreated(product)
      form.reset()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not create the product.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="page-shell form-page">
      <div className="page-heading">
        <div>
          <h1>Create a product</h1>
          <p className="page-intro">Publish a listing and set the stock available to buyers.</p>
        </div>
      </div>
      {error && <div className="alert" role="alert">{error}</div>}
      {created && <div className="success" role="status">{created.name} created with ID {created.id}.</div>}
      <form className="product-form" onSubmit={createProduct}>
        <label htmlFor="product-name">Name</label>
        <input id="product-name" name="name" required maxLength={160} />

        <label htmlFor="product-description">Description</label>
        <textarea id="product-description" name="description" required rows={5} />

        <div className="form-row">
          <div>
            <label htmlFor="product-price">Price (USD)</label>
            <input id="product-price" name="priceAmount" type="number" min="0.01" step="0.01" required />
          </div>
          <div>
            <label htmlFor="product-stock">Initial stock</label>
            <input id="product-stock" name="initialStock" type="number" min="0" step="1" required />
          </div>
        </div>

        <label htmlFor="product-category">Category</label>
        <input id="product-category" name="category" required maxLength={80} />

        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating product…' : 'Create product'}
        </button>
      </form>
    </main>
  )
}
