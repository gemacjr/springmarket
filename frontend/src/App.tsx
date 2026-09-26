import type { ReactNode } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/useAuth'
import { AdminOrdersPage } from './pages/AdminOrdersPage'
import { CartPage } from './pages/CartPage'
import { CatalogPage } from './pages/CatalogPage'
import { LoginPage } from './pages/LoginPage'
import { OrdersPage } from './pages/OrdersPage'
import { ProductDetailPage } from './pages/ProductDetailPage'
import { RegisterPage } from './pages/RegisterPage'
import { SellerProductPage } from './pages/SellerProductPage'

function RoleRoute({ role, children }: { role: string; children: ReactNode }) {
  const { session } = useAuth()
  if (!session) return <Navigate to="/login" replace />
  if (!session.roles.includes(role)) return <Navigate to="/products" replace />
  return children
}

function App() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const roles = new Set(session?.roles ?? [])

  async function handleLogout() {
    await logout().catch(() => undefined)
    navigate('/login')
  }

  return (
    <div className="app">
      <header className="site-header">
        <div className="header-inner">
          <Link className="brand" to="/products">SpringMarket</Link>
          <nav aria-label="Main navigation">
            <NavLink to="/products">Catalog</NavLink>
            {roles.has('BUYER') && (
              <>
                <NavLink to="/cart">Cart</NavLink>
                <NavLink to="/orders">Orders</NavLink>
              </>
            )}
            {roles.has('SELLER') && <NavLink to="/seller/products">Sell</NavLink>}
            {roles.has('ADMIN') && <NavLink to="/admin/orders">Admin</NavLink>}
            {session ? (
              <button className="header-button" type="button" onClick={handleLogout}>
                Sign out
              </button>
            ) : (
              <>
                <NavLink to="/login">Sign in</NavLink>
                <NavLink to="/register">Register</NavLink>
              </>
            )}
          </nav>
        </div>
      </header>
      <Routes>
        <Route path="/" element={<Navigate to="/products" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/products" element={<CatalogPage />} />
        <Route path="/products/:id" element={<ProductDetailPage />} />
        <Route path="/cart" element={<RoleRoute role="BUYER"><CartPage /></RoleRoute>} />
        <Route path="/orders" element={<RoleRoute role="BUYER"><OrdersPage /></RoleRoute>} />
        <Route
          path="/seller/products"
          element={<RoleRoute role="SELLER"><SellerProductPage /></RoleRoute>}
        />
        <Route
          path="/admin/orders"
          element={<RoleRoute role="ADMIN"><AdminOrdersPage /></RoleRoute>}
        />
        <Route path="*" element={<Navigate to="/products" replace />} />
      </Routes>
    </div>
  )
}

export default App
