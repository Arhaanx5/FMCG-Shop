import { useEffect, lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { App as CapApp } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { useAuth } from './context/AuthContext'
import LoadingScreen from './components/LoadingScreen'
import Layout from './components/Layout'

// Lazy-loaded pages — each page becomes a separate JS chunk
// loaded only when the user navigates to that route
const Login        = lazy(() => import('./pages/Login'))
const Dashboard    = lazy(() => import('./pages/Dashboard'))
const Products     = lazy(() => import('./pages/Products'))
const Customers    = lazy(() => import('./pages/Customers'))
const Billing      = lazy(() => import('./pages/Billing'))
const Stock        = lazy(() => import('./pages/Stock'))
const Khata        = lazy(() => import('./pages/Khata'))
const Expenses     = lazy(() => import('./pages/Expenses'))
const Damage       = lazy(() => import('./pages/Damage'))
const Areas        = lazy(() => import('./pages/Areas'))
const Users        = lazy(() => import('./pages/Users'))
const Salesmen     = lazy(() => import('./pages/Salesmen'))
const Deliveries   = lazy(() => import('./pages/Deliveries'))
const WhatsAppSetup = lazy(() => import('./pages/WhatsAppSetup'))

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

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      const handler = CapApp.addListener('backButton', ({ canGoBack }) => {
        if (!canGoBack) {
          CapApp.exitApp()
        } else {
          window.history.back()
        }
      })
      return () => {
        handler.then(h => h.remove())
      }
    }
  }, [])

  if (loading) return <LoadingScreen />

  return (
    <Suspense fallback={<LoadingScreen />}>
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
          <Route path="billing" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'SALESMAN', 'DELIVERY_BOY']}><Billing /></ProtectedRoute>} />
          <Route path="products" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Products /></ProtectedRoute>} />
          <Route path="customers" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Customers /></ProtectedRoute>} />
          <Route path="whatsapp" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><WhatsAppSetup /></ProtectedRoute>} />
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
    </Suspense>
  )
}

