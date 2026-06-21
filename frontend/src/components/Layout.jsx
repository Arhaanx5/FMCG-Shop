import { useState, useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { usePaymentSocketContext } from '../context/PaymentSocketContext'
import api from '../services/api'
import lariLogo from '../assets/lari-traders-logo.png'
import {
  LayoutDashboard, Package, Users, ShoppingCart, Warehouse,
  BookOpen, Receipt, AlertTriangle, MapPin, UserCog,
  Menu, X, LogOut, ChevronLeft, Award, Truck, Sun, Moon, Palette,
  MessageSquare, Clock, Activity, TrendingUp, DollarSign
} from 'lucide-react'

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN', 'MANAGER'] },
  { path: '/billing', label: 'Billing', icon: ShoppingCart, roles: ['ADMIN', 'MANAGER', 'SALESMAN', 'DELIVERY_BOY'] },
  { path: '/products', label: 'Products', icon: Package, roles: ['ADMIN', 'MANAGER'] },
  { path: '/customers', label: 'Customers', icon: Users, roles: ['ADMIN', 'MANAGER'] },
  { path: '/whatsapp', label: 'Bulk Reminders', icon: MessageSquare, roles: ['ADMIN', 'MANAGER'] },
  { path: '/salesmen', label: 'Salesmen', icon: Award, roles: ['ADMIN', 'MANAGER'] },
  { path: '/stock', label: 'Stock', icon: Warehouse, roles: ['ADMIN', 'MANAGER'] },
  { path: '/khata', label: 'Khata', icon: BookOpen, roles: ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN'] },
  { path: '/receivables', label: 'Udhar Collection', icon: DollarSign, roles: ['ADMIN', 'MANAGER'] },
  { path: '/expenses', label: 'Expenses', icon: Receipt, roles: ['ADMIN'] },
  { path: '/damage', label: 'Damage', icon: AlertTriangle, roles: ['ADMIN', 'MANAGER'] },
  { path: '/areas', label: 'Areas', icon: MapPin, roles: ['ADMIN', 'MANAGER'] },
  { path: '/deliveries', label: 'Deliveries', icon: Truck, roles: ['ADMIN', 'MANAGER', 'DELIVERY_BOY', 'SALESMAN'] },
  { path: '/users', label: 'Users', icon: UserCog, roles: ['ADMIN'] },
  { path: '/schedulers', label: 'Schedulers', icon: Clock, roles: ['ADMIN'] },
  { path: '/health-report', label: 'Business Health', icon: Activity, roles: ['ADMIN', 'MANAGER'] },
  { path: '/health-trend', label: 'Health Trends', icon: TrendingUp, roles: ['ADMIN', 'MANAGER'] },
]

const pageNames = {
  '/': 'Dashboard',
  '/billing': 'Billing',
  '/products': 'Products',
  '/customers': 'Customers',
  '/whatsapp': 'WhatsApp Bulk Reminders',
  '/salesmen': 'Salesmen & Routes',
  '/stock': 'Stock',
  '/khata': 'Khata / Payments',
  '/receivables': 'Udhar Collection Dashboard',
  '/expenses': 'Expenses',
  '/damage': 'Damage Log',
  '/areas': 'Areas',
  '/deliveries': 'Deliveries & Route Map',
  '/users': 'User Management',
  '/schedulers': 'Task Schedulers & Backups',
  '/health-report': 'Business Health Report',
  '/health-trend': 'Business Health Trends',
}

export default function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)

  // Subscribes to payment socket notifications globally
  usePaymentSocketContext()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  // Pull-to-refresh gesture tracking
  const mainRef = useRef(null)
  const startY = useRef(0)
  const isPulling = useRef(false)
  const pullDistance = useRef(0)
  const [isRefreshing, setIsRefreshing] = useState(false)

  useEffect(() => {
    if (location.pathname !== '/' || !isMobile) return

    const handleTouchStart = (e) => {
      // Only pull down if at the very top of the page
      if (window.scrollY === 0) {
        startY.current = e.touches[0].pageY
        isPulling.current = true
      }
    }

    const handleTouchMove = (e) => {
      if (!isPulling.current) return
      const currentY = e.touches[0].pageY
      const diff = currentY - startY.current
      if (diff > 0) {
        // Apply resistance
        const dist = Math.min(diff * 0.4, 80)
        pullDistance.current = dist
        if (mainRef.current) {
          mainRef.current.style.transform = `translate3d(0, ${dist}px, 0)`
          mainRef.current.style.transition = 'none'
        }
        if (diff > 10) {
          e.preventDefault()
        }
      }
    }

    const handleTouchEnd = () => {
      if (!isPulling.current) return
      isPulling.current = false
      if (pullDistance.current >= 60) {
        setIsRefreshing(true)
        if (mainRef.current) {
          mainRef.current.style.transform = `translate3d(0, 40px, 0)`
          mainRef.current.style.transition = 'transform 0.2s ease'
        }
        // Perform reload/refresh
        setTimeout(() => {
          window.location.reload()
        }, 800)
      } else {
        if (mainRef.current) {
          mainRef.current.style.transform = `translate3d(0, 0, 0)`
          mainRef.current.style.transition = 'transform 0.2s ease'
        }
      }
      pullDistance.current = 0
    }

    const mainEl = mainRef.current
    if (mainEl) {
      mainEl.addEventListener('touchstart', handleTouchStart, { passive: false })
      mainEl.addEventListener('touchmove', handleTouchMove, { passive: false })
      mainEl.addEventListener('touchend', handleTouchEnd)
    }

    return () => {
      if (mainEl) {
        mainEl.removeEventListener('touchstart', handleTouchStart)
        mainEl.removeEventListener('touchmove', handleTouchMove)
        mainEl.removeEventListener('touchend', handleTouchEnd)
      }
    }
  }, [location.pathname, isMobile])

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth < 768)
    }
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Dark Mode state
  const [isDarkMode, setIsDarkMode] = useState(() => {
    const saved = localStorage.getItem('theme')
    return saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)
  })

  useEffect(() => {
    if (isDarkMode) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, [isDarkMode])

  // UI Theme state ('classic' | 'modern')
  const [uiTheme, setUiTheme] = useState(() => {
    return localStorage.getItem('uiTheme') || 'modern'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', uiTheme)
    localStorage.setItem('uiTheme', uiTheme)
  }, [uiTheme])

  // Live location background GPS reporter for delivery boys / salesmen
  // Smart GPS calibration: only sends update if moved >15m AND accuracy is good
  useEffect(() => {
    if (!user) return
    const isDeliveryBoy = user.role === 'DELIVERY_BOY' || user.role === 'SALESMAN'
    if (!isDeliveryBoy) return

    if (!navigator.geolocation) {
      console.warn('Geolocation is not supported by this browser')
      return
    }

    // Haversine distance in meters between two [lat,lng] coords
    const haversineDistance = (lat1, lng1, lat2, lng2) => {
      const R = 6371000 // Earth radius in meters
      const dLat = ((lat2 - lat1) * Math.PI) / 180
      const dLng = ((lng2 - lng1) * Math.PI) / 180
      const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos((lat1 * Math.PI) / 180) *
          Math.cos((lat2 * Math.PI) / 180) *
          Math.sin(dLng / 2) * Math.sin(dLng / 2)
      return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    let lastReportedLat = null
    let lastReportedLng = null
    const MIN_DISTANCE_METERS = 15   // Only update if moved at least 15 meters
    const MAX_ACCURACY_METERS = 200  // Ignore readings with poor GPS accuracy (>200m uncertainty, relaxed for indoor/testing)

    const reportLocation = async (lat, lng, accuracy) => {
      // Skip if GPS accuracy is too poor (phone indoor / weak signal)
      if (accuracy > MAX_ACCURACY_METERS) {
        console.log(`GPS: Skipping update — accuracy too low: ${Math.round(accuracy)}m`)
        return
      }

      // Skip if hasn't moved enough (kills GPS noise jitter when standing still)
      if (lastReportedLat !== null && lastReportedLng !== null) {
        const dist = haversineDistance(lastReportedLat, lastReportedLng, lat, lng)
        if (dist < MIN_DISTANCE_METERS) {
          console.log(`GPS: Skipping update — only moved ${Math.round(dist)}m (min: ${MIN_DISTANCE_METERS}m)`)
          return
        }
      }

      lastReportedLat = lat
      lastReportedLng = lng

      try {
        await api.put('/users/live-location', { latitude: lat, longitude: lng })
        console.log(`GPS: Updated location — accuracy: ${Math.round(accuracy)}m`)
      } catch (err) {
        console.error('Failed to update live location:', err)
      }
    }

    // Capture initial position immediately on login
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude, accuracy } = position.coords
        reportLocation(latitude, longitude, accuracy)
      },
      (err) => console.warn('GPS getCurrentPosition error:', err.message),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    )

    // Watch for position changes — maximumAge: 0 means always fresh (no cached GPS)
    const watchId = navigator.geolocation.watchPosition(
      (position) => {
        const { latitude, longitude, accuracy } = position.coords
        reportLocation(latitude, longitude, accuracy)
      },
      (err) => console.warn('GPS watchPosition error:', err.message),
      {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 0,  // Never use cached location — always ask fresh GPS
      }
    )

    return () => {
      navigator.geolocation.clearWatch(watchId)
    }
  }, [user])

  const role = user?.role
  const visibleItems = role ? navItems.filter((item) => item.roles.includes(role)) : []
  const currentPage = pageNames[location.pathname] || 'Lari Traders'

  return (
    <div className="flex min-h-screen bg-[var(--color-bg)] text-slate-900 dark:text-slate-100 transition-colors duration-200">
      {/* Mobile overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setMobileOpen(false)}
            className="fixed inset-0 bg-black/50 z-[250] md:hidden"
          />
        )}
      </AnimatePresence>

      {/* Sidebar */}
      <motion.aside
        animate={{ width: collapsed ? 72 : 260 }}
        transition={{ duration: 0.3, ease: [0.4, 0, 0.2, 1] }}
        className={`app-sidebar fixed top-0 left-0 bottom-0 ${
          uiTheme !== 'classic' ? 'glass-panel border-r' : 'bg-white dark:bg-slate-800 border-r border-slate-200 dark:border-slate-700'
        } flex flex-col z-[260] overflow-hidden transition-transform duration-300 md:translate-x-0 ${
          mobileOpen ? 'translate-x-0 shadow-2xl w-[260px]' : '-translate-x-full'
        }`}
      >
        {/* Logo */}
        <div className={`flex items-center border-b ${
          uiTheme !== 'classic' ? 'border-border' : 'border-slate-200 dark:border-slate-700'
        } min-h-16 ${
          collapsed ? 'justify-center px-2 py-2' : 'px-4 py-2'
        }`}>
          {collapsed ? (
            /* Collapsed: show just the eagle icon mark */
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: 'var(--radius-md)',
                overflow: 'hidden',
                background: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                boxShadow: '0 1px 4px rgba(0,0,0,0.12)',
              }}
            >
              {/* Eagle-only crop via clipped img */}
              <img
                src={lariLogo}
                alt="LT"
                style={{
                  width: 80,
                  height: 60,
                  objectFit: 'cover',
                  objectPosition: '50% 0%',
                  marginTop: 4,
                }}
              />
            </div>
          ) : (
            /* Expanded: full logo */
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.05 }}
              style={{ width: '100%' }}
            >
              <img
                src={lariLogo}
                alt="Lari Traders"
                style={{
                  width: '100%',
                  maxWidth: 210,
                  height: 'auto',
                  display: 'block',
                  // Invert in dark mode so logo is visible
                  filter: 'none',
                }}
                className="dark:brightness-90"
              />
            </motion.div>
          )}
        </div>

        {/* Nav */}
        <nav className="flex-1 py-3 px-3 overflow-y-auto space-y-1">
          {visibleItems.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.path === '/'}
                onClick={() => setMobileOpen(false)}
                className={({ isActive }) => `
                  flex items-center gap-3 py-2.5 px-3 rounded-theme-sm text-sm transition-all duration-150 relative overflow-hidden
                  ${collapsed ? 'justify-center' : 'justify-start'}
                  ${isActive
                    ? uiTheme === 'modern'
                      ? 'text-violet-500 dark:text-violet-400 font-semibold bg-violet-500/10 dark:bg-violet-400/10'
                      : uiTheme === 'cyber'
                      ? 'text-blue-500 dark:text-cyan-400 font-semibold bg-blue-500/10 dark:bg-cyan-400/10'
                      : uiTheme === 'neon'
                      ? 'text-teal-600 dark:text-emerald-400 font-semibold bg-teal-500/10 dark:bg-emerald-400/10'
                      : 'text-amber-500 font-semibold bg-amber-500/10 dark:bg-amber-500/10'
                    : 'text-slate-600 dark:text-slate-300 font-medium hover:bg-slate-100 dark:hover:bg-slate-700/40 hover:text-slate-900 dark:hover:text-slate-100'}
                `}
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <motion.div
                        layoutId="nav-active"
                        className={`absolute inset-0 ${
                          uiTheme === 'modern'
                            ? 'bg-violet-500/5 dark:bg-violet-400/5 border-l-2 border-violet-500 dark:border-violet-400'
                            : uiTheme === 'cyber'
                            ? 'bg-blue-500/5 dark:bg-cyan-400/5 border-l-2 border-blue-500 dark:border-cyan-400'
                            : uiTheme === 'neon'
                            ? 'bg-teal-500/5 dark:bg-emerald-400/5 border-l-2 border-teal-500 dark:border-emerald-400'
                            : 'bg-amber-500/5 dark:bg-amber-500/5 border-l-2 border-amber-500'
                        }`}
                        transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                      />
                    )}
                    <Icon size={20} className="relative z-10 flex-shrink-0" />
                    {!collapsed && (
                      <span className="relative z-10 whitespace-nowrap">{item.label}</span>
                    )}
                  </>
                )}
              </NavLink>
            )
          })}
        </nav>

        {/* User + collapse */}
        <div className="p-4 border-t border-slate-200 dark:border-slate-700 flex flex-col gap-3">
          {!collapsed && (
            <div className="flex items-center gap-3">
              <div className={`w-9 h-9 rounded-theme-md flex items-center justify-center text-sm font-bold flex-shrink-0 ${
                uiTheme === 'modern' ? 'bg-violet-100 dark:bg-violet-950/40 text-violet-500' :
                uiTheme === 'cyber' ? 'bg-blue-100 dark:bg-cyan-950/40 text-blue-500 dark:text-cyan-400' :
                uiTheme === 'neon' ? 'bg-teal-100 dark:bg-emerald-950/40 text-teal-600 dark:text-emerald-400' :
                'bg-slate-100 dark:bg-slate-700/50 text-amber-500'
              }`}>
                {(user?.name || 'U')[0].toUpperCase()}
              </div>
              <div className="flex-1 min-w-0">
                <div className="truncate text-xs font-semibold text-slate-800 dark:text-slate-200">
                  {user?.name || 'User'}
                </div>
                <div className="truncate text-[10px] text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                  {role}
                </div>
              </div>
            </div>
          )}
          <div className={`flex gap-1.5 ${collapsed ? 'justify-center' : 'justify-start'}`}>
            <button
              className="p-1.5 text-slate-500 dark:text-slate-400 hover:text-slate-850 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700/50 rounded-theme-sm transition-colors"
              onClick={() => setCollapsed(!collapsed)}
              title={collapsed ? 'Expand' : 'Collapse'}
            >
              <ChevronLeft size={18} className={`transition-transform duration-300 ${collapsed ? 'rotate-180' : ''}`} />
            </button>
            <button
              className="p-1.5 text-slate-500 dark:text-slate-400 hover:text-red-500 hover:bg-slate-100 dark:hover:bg-slate-700/50 rounded-theme-sm transition-colors"
              onClick={logout}
              title="Logout"
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </motion.aside>

      {/* Main content */}
      <div 
        className="app-content flex-1 flex flex-col min-h-screen transition-all duration-300"
        style={{
          marginLeft: isMobile ? 0 : (collapsed ? 72 : 260),
          minWidth: 0,
          overflowX: 'hidden',
        }}
      >
        {/* Top bar */}
        <header className={`app-header sticky top-0 h-16 ${
          uiTheme !== 'classic' ? 'glass-panel border-b' : 'bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700'
        } flex items-center px-4 md:px-8 z-[200] gap-4`}>
          <button
            className="p-1.5 text-slate-500 dark:text-slate-400 hover:text-slate-850 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700/50 rounded-theme-sm md:hidden"
            onClick={() => setMobileOpen(true)}
          >
            <Menu size={20} />
          </button>
          <h1 className="text-sm md:text-lg font-bold text-slate-800 dark:text-slate-100 truncate flex-1 min-w-0">
            {currentPage}
          </h1>

          {/* Theme Switcher */}
          <button
            className="ml-auto p-1.5 text-slate-500 dark:text-slate-400 hover:text-amber-500 dark:hover:text-amber-400 hover:bg-slate-100 dark:hover:bg-slate-700/50 rounded-theme-sm transition-colors mr-1"
            onClick={() => {
              const themes = ['classic', 'modern', 'cyber', 'neon']
              const nextTheme = themes[(themes.indexOf(uiTheme) + 1) % themes.length]
              setUiTheme(nextTheme)
            }}
            title={`Current Theme: ${uiTheme.toUpperCase()}. Click to switch theme.`}
          >
            <Palette size={20} />
          </button>

          {/* Dark Mode Toggle */}
          <button
            className="p-1.5 text-slate-500 dark:text-slate-400 hover:text-amber-500 dark:hover:text-amber-400 hover:bg-slate-100 dark:hover:bg-slate-700/50 rounded-theme-sm transition-colors"
            onClick={() => setIsDarkMode(!isDarkMode)}
            title={isDarkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
          >
            {isDarkMode ? <Sun size={20} /> : <Moon size={20} />}
          </button>
        </header>

        {/* Page content with animation */}
        <main className="flex-1" ref={mainRef} style={{ position: 'relative' }}>
          {isRefreshing && (
            <div style={{
              position: 'absolute',
              top: '10px',
              left: '50%',
              transform: 'translateX(-50%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: 'var(--color-surface-2)',
              borderRadius: '50%',
              padding: '6px',
              boxShadow: 'var(--shadow-md)',
              border: '1px solid var(--color-border)',
              zIndex: 100
            }}>
              <div className="spinner spinner-sm" style={{ width: '18px', height: '18px' }} />
            </div>
          )}
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.2 }}
            >
              <Outlet context={[uiTheme, setUiTheme]} />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <style>{`
        @media (max-width: 768px) {
          .app-sidebar {
            transform: ${mobileOpen ? 'translateX(0)' : 'translateX(-260px)'} !important;
            width: 260px !important;
            box-shadow: ${mobileOpen ? '0 20px 25px -5px rgba(0, 0, 0, 0.15)' : 'none'} !important;
          }
          .app-content {
            margin-left: 0 !important;
          }
        }
      `}</style>
    </div>
  )
}
