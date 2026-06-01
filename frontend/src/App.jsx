import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import LoadingScreen from './components/LoadingScreen'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Products from './pages/Products'
import Customers from './pages/Customers'
import Billing from './pages/Billing'
import Stock from './pages/Stock'
import Khata from './pages/Khata'
import Expenses from './pages/Expenses'
import Damage from './pages/Damage'
import Areas from './pages/Areas'
import Users from './pages/Users'
import Salesmen from './pages/Salesmen'
import Deliveries from './pages/Deliveries'

function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, user } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user?.role)) return <Navigate to="/" replace />
  return children
}

function DashboardRedirect() {
  const { user } = useAuth()
  if (user?.role === 'DELIVERY_BOY' || user?.role === 'SALESMAN') {
    return <Navigate to="/deliveries" replace />
  }
  if (user?.role === 'ADMIN' || user?.role === 'MANAGER') {
    return <Dashboard />
  }
  return <Navigate to="/login" replace />
}

export default function App() {
  const { loading } = useAuth()

  if (loading) return <LoadingScreen />

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardRedirect />} />
        <Route path="billing" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Billing /></ProtectedRoute>} />
        <Route path="products" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Products /></ProtectedRoute>} />
        <Route path="customers" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Customers /></ProtectedRoute>} />
        <Route path="salesmen" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Salesmen /></ProtectedRoute>} />
        <Route path="stock" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Stock /></ProtectedRoute>} />
        <Route path="khata" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN']}><Khata /></ProtectedRoute>} />
        <Route path="expenses" element={<ProtectedRoute roles={['ADMIN']}><Expenses /></ProtectedRoute>} />
        <Route path="damage" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Damage /></ProtectedRoute>} />
        <Route path="areas" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Areas /></ProtectedRoute>} />
        <Route path="deliveries" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN']}><Deliveries /></ProtectedRoute>} />
        <Route path="users" element={<ProtectedRoute roles={['ADMIN']}><Users /></ProtectedRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
