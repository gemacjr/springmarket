import { Link, NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/useAuth'
import { CatalogPage } from './pages/CatalogPage'
import { LoginPage } from './pages/LoginPage'
import { ProductDetailPage } from './pages/ProductDetailPage'
import { RegisterPage } from './pages/RegisterPage'

function App() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()

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
        <Route path="*" element={<Navigate to="/products" replace />} />
      </Routes>
    </div>
  )
}

export default App
