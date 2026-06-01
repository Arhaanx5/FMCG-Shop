import { useState, useEffect } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import api from '../services/api'
import {
  LayoutDashboard, Package, Users, ShoppingCart, Warehouse,
  BookOpen, Receipt, AlertTriangle, MapPin, UserCog,
  Menu, X, LogOut, ChevronLeft, Award, Truck
} from 'lucide-react'

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN', 'MANAGER'] },
  { path: '/billing', label: 'Billing', icon: ShoppingCart, roles: ['ADMIN', 'MANAGER'] },
  { path: '/products', label: 'Products', icon: Package, roles: ['ADMIN', 'MANAGER'] },
  { path: '/customers', label: 'Customers', icon: Users, roles: ['ADMIN', 'MANAGER'] },
  { path: '/salesmen', label: 'Salesmen', icon: Award, roles: ['ADMIN', 'MANAGER'] },
  { path: '/stock', label: 'Stock', icon: Warehouse, roles: ['ADMIN', 'MANAGER'] },
  { path: '/khata', label: 'Khata', icon: BookOpen, roles: ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN'] },
  { path: '/expenses', label: 'Expenses', icon: Receipt, roles: ['ADMIN'] },
  { path: '/damage', label: 'Damage', icon: AlertTriangle, roles: ['ADMIN', 'MANAGER'] },
  { path: '/areas', label: 'Areas', icon: MapPin, roles: ['ADMIN', 'MANAGER'] },
  { path: '/deliveries', label: 'Deliveries', icon: Truck, roles: ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN'] },
  { path: '/users', label: 'Users', icon: UserCog, roles: ['ADMIN'] },
]

const pageNames = {
  '/': 'Dashboard',
  '/billing': 'Billing',
  '/products': 'Products',
  '/customers': 'Customers',
  '/salesmen': 'Salesmen & Routes',
  '/stock': 'Stock',
  '/khata': 'Khata / Payments',
  '/expenses': 'Expenses',
  '/damage': 'Damage Log',
  '/areas': 'Areas',
  '/deliveries': 'Deliveries & Route Map',
  '/users': 'User Management',
}

export default function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)

  // Live location background GPS reporter for delivery boys / salesmen
  useEffect(() => {
    if (!user) return
    const isDeliveryBoy = user.role === 'DELIVERY_BOY' || user.role === 'SALESMAN'
    if (!isDeliveryBoy) return

    if (!navigator.geolocation) {
      console.warn('Geolocation is not supported by this browser')
      return
    }

    const reportLocation = async (lat, lng) => {
      try {
        await api.put('/users/live-location', { latitude: lat, longitude: lng })
      } catch (err) {
        console.error('Failed to update live location:', err)
      }
    }

    // Capture location once immediately, then watch
    navigator.geolocation.getCurrentPosition(
      (position) => {
        reportLocation(position.coords.latitude, position.coords.longitude)
      },
      null,
      { enableHighAccuracy: true }
    )

    const watchId = navigator.geolocation.watchPosition(
      (position) => {
        reportLocation(position.coords.latitude, position.coords.longitude)
      },
      null,
      { enableHighAccuracy: true, timeout: 30000, maximumAge: 30000 }
    )

    return () => {
      navigator.geolocation.clearWatch(watchId)
    }
  }, [user])

  const role = user?.role
  const visibleItems = role ? navItems.filter((item) => item.roles.includes(role)) : []
  const currentPage = pageNames[location.pathname] || 'Lari Traders'

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* Mobile overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setMobileOpen(false)}
            style={{
              position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
              zIndex: 250, display: 'none',
            }}
            className="mobile-overlay"
          />
        )}
      </AnimatePresence>

      {/* Sidebar */}
      <motion.aside
        className="app-sidebar"
        animate={{ width: collapsed ? 72 : 260 }}
        transition={{ duration: 0.3, ease: [0.4, 0, 0.2, 1] }}
        style={{
          position: 'fixed', top: 0, left: 0, bottom: 0,
          background: 'var(--color-surface)',
          borderRight: '1px solid var(--color-border)',
          display: 'flex', flexDirection: 'column',
          zIndex: 260, overflow: 'hidden',
        }}
      >
        {/* Logo */}
        <div style={{
          padding: collapsed ? 'var(--space-4)' : 'var(--space-5) var(--space-6)',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
          minHeight: 'var(--topbar-height)',
        }}>
          <div style={{
            width: 40, height: 40, borderRadius: 'var(--radius-md)', flexShrink: 0,
            background: 'linear-gradient(135deg, var(--color-accent), #d97706)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '16px', fontWeight: 800, color: 'var(--color-text-inverse)',
            boxShadow: '0 0 16px rgba(245, 158, 11, 0.3)',
          }}>
            LT
          </div>
          {!collapsed && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.1 }}
              style={{ overflow: 'hidden', whiteSpace: 'nowrap' }}
            >
              <div style={{ fontSize: 'var(--font-size-md)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text)' }}>
                Lari Traders
              </div>
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                FMCG Management
              </div>
            </motion.div>
          )}
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: 'var(--space-3)', overflowY: 'auto' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
            {visibleItems.map((item, i) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.path === '/'}
                  onClick={() => setMobileOpen(false)}
                  style={({ isActive }) => ({
                    display: 'flex', alignItems: 'center',
                    gap: 'var(--space-3)',
                    padding: collapsed ? 'var(--space-3)' : 'var(--space-3) var(--space-4)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: 'var(--font-size-base)',
                    fontWeight: isActive ? 'var(--font-weight-semibold)' : 'var(--font-weight-medium)',
                    color: isActive ? 'var(--color-accent)' : 'var(--color-text-secondary)',
                    background: isActive ? 'var(--color-accent-soft)' : 'transparent',
                    textDecoration: 'none',
                    transition: 'all 150ms',
                    justifyContent: collapsed ? 'center' : 'flex-start',
                    position: 'relative',
                    overflow: 'hidden',
                  })}
                  className="nav-link"
                >
                  {({ isActive }) => (
                    <>
                      {isActive && (
                        <motion.div
                          layoutId="nav-active"
                          style={{
                            position: 'absolute', inset: 0,
                            background: 'var(--color-accent-soft)',
                            borderRadius: 'var(--radius-md)',
                          }}
                          transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                        />
                      )}
                      <Icon size={20} style={{ position: 'relative', zIndex: 1, flexShrink: 0 }} />
                      {!collapsed && (
                        <span style={{ position: 'relative', zIndex: 1, whiteSpace: 'nowrap' }}>{item.label}</span>
                      )}
                    </>
                  )}
                </NavLink>
              )
            })}
          </div>
        </nav>

        {/* User + collapse */}
        <div style={{
          padding: collapsed ? 'var(--space-3)' : 'var(--space-4) var(--space-5)',
          borderTop: '1px solid var(--color-border)',
          display: 'flex', flexDirection: 'column', gap: 'var(--space-3)',
        }}>
          {!collapsed && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
              <div style={{
                width: 36, height: 36, borderRadius: 'var(--radius-full)',
                background: 'var(--color-surface-3)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-bold)',
                color: 'var(--color-accent)', flexShrink: 0,
              }}>
                {(user?.name || 'U')[0].toUpperCase()}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="truncate" style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)' }}>
                  {user?.name || 'User'}
                </div>
                <div className="truncate" style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                  {role}
                </div>
              </div>
            </div>
          )}
          <div style={{ display: 'flex', gap: 'var(--space-2)', justifyContent: collapsed ? 'center' : 'flex-start' }}>
            <button
              className="btn btn-ghost btn-icon btn-sm"
              onClick={() => setCollapsed(!collapsed)}
              title={collapsed ? 'Expand' : 'Collapse'}
            >
              <ChevronLeft size={18} style={{ transform: collapsed ? 'rotate(180deg)' : 'none', transition: 'transform 300ms' }} />
            </button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={logout} title="Logout">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </motion.aside>

      {/* Main content */}
      <div 
        className="app-content"
        style={{
          flex: 1,
          marginLeft: collapsed ? 72 : 260,
          transition: 'margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
          display: 'flex', flexDirection: 'column', minHeight: '100vh',
        }}
      >
        {/* Top bar */}
        <header 
          className="app-header"
          style={{
            height: 'var(--topbar-height)',
            background: 'var(--color-surface)',
            borderBottom: '1px solid var(--color-border)',
            display: 'flex', alignItems: 'center',
            padding: '0 var(--space-8)',
            position: 'sticky', top: 0,
            zIndex: 'var(--z-sticky)',
            gap: 'var(--space-4)',
          }}
        >
          <button
            className="btn btn-ghost btn-icon btn-sm mobile-menu-btn"
            onClick={() => setMobileOpen(true)}
            style={{ display: 'none' }}
          >
            <Menu size={20} />
          </button>
          <h1 style={{
            fontSize: 'var(--font-size-lg)',
            fontWeight: 'var(--font-weight-semibold)',
            color: 'var(--color-text)',
          }}>
            {currentPage}
          </h1>
        </header>

        {/* Page content with animation */}
        <main style={{ flex: 1 }}>
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              transition={{ duration: 0.25 }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <style>{`
        .nav-link:hover {
          background: var(--color-surface-hover) !important;
          color: var(--color-text) !important;
        }
        @media (max-width: 768px) {
          .mobile-overlay {
            display: ${mobileOpen ? 'block' : 'none'} !important;
          }
          .mobile-menu-btn {
            display: flex !important;
          }
          .app-sidebar {
            transform: ${mobileOpen ? 'translateX(0)' : 'translateX(-260px)'} !important;
            width: 260px !important;
            transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
            box-shadow: ${mobileOpen ? 'var(--shadow-xl)' : 'none'} !important;
          }
          .app-content {
            margin-left: 0 !important;
          }
          .app-header {
            padding: 0 var(--space-4) !important;
          }
        }
      `}</style>
    </div>
  )
}
