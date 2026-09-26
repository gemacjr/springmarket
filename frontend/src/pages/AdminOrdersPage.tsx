import { useEffect, useRef, useState } from 'react'
import { apiRequest, ApiError } from '../api/client'
import type { Order, ReportJob } from '../api/types'

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount)
}

function yesterday() {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().slice(0, 10)
}

export function AdminOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [report, setReport] = useState<ReportJob | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [launching, setLaunching] = useState(false)
  const reportDate = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    apiRequest<Order[]>('/api/v1/admin/orders', { signal: controller.signal })
      .then(setOrders)
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof ApiError ? caught.message : 'Could not load orders.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  async function launchReport() {
    setLaunching(true)
    setError('')
    setReport(null)
    const date = reportDate.current?.value
    try {
      setReport(await apiRequest<ReportJob>(
        `/api/v1/admin/reports/daily-sales${date ? `?date=${encodeURIComponent(date)}` : ''}`,
        { method: 'POST' },
      ))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not start the report.')
    } finally {
      setLaunching(false)
    }
  }

  return (
    <main className="page-shell">
      <div className="page-heading admin-heading">
        <div>
          <h1>Order administration</h1>
          <p className="page-intro">Review marketplace orders and run daily sales exports.</p>
        </div>
        <div className="report-controls">
          <label htmlFor="report-date">Report date</label>
          <input ref={reportDate} id="report-date" name="reportDate" type="date" defaultValue={yesterday()} />
          <button
            data-action="daily-report"
            type="button"
            disabled={launching}
            onClick={launchReport}
          >
            {launching ? 'Starting…' : 'Run daily report'}
          </button>
        </div>
      </div>
      {error && <div className="alert" role="alert">{error}</div>}
      {report && (
        <div className="success" role="status">
          Report {report.executionId} started for {report.reportDate} ({report.status}).
        </div>
      )}
      {loading && <p className="status" role="status">Loading orders…</p>}
      {!loading && !error && orders.length === 0 && (
        <div className="empty-state"><h2>No orders</h2><p>Marketplace orders will appear here.</p></div>
      )}
      <div className="admin-order-table" role="region" aria-label="All orders" tabIndex={0}>
        {orders.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Order</th>
                <th>Buyer</th>
                <th>Status</th>
                <th>Created</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>{order.id}</td>
                  <td>{order.buyerId}</td>
                  <td><span className="status-badge">{order.status}</span></td>
                  <td>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(order.createdAt))}</td>
                  <td>{formatPrice(order.totalAmount, order.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </main>
  )
}
