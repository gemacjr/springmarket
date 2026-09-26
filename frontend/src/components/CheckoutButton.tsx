import { useState } from 'react'
import { apiRequest, ApiError } from '../api/client'
import type { Order } from '../api/types'

interface CheckoutButtonProps {
  disabled: boolean
  onPlaced: (order: Order) => void
}

export function CheckoutButton({ disabled, onPlaced }: CheckoutButtonProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  async function checkout() {
    setSubmitting(true)
    setError('')
    try {
      onPlaced(await apiRequest<Order>('/api/v1/orders', { method: 'POST' }))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not place the order.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {error && <div className="alert" role="alert">{error}</div>}
      <button
        data-action="checkout"
        type="button"
        disabled={disabled || submitting}
        onClick={checkout}
      >
        {submitting ? 'Placing order…' : 'Place order'}
      </button>
    </>
  )
}
