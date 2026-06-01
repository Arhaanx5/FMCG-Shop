import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import api from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [token, setToken] = useState(localStorage.getItem('token'))
  const [loading, setLoading] = useState(true)

  const checkAuth = useCallback(async () => {
    const storedToken = localStorage.getItem('token')
    if (!storedToken) {
      setLoading(false)
      return
    }
    try {
      const res = await api.get('/auth/me')
      setUser(res.data)
      setToken(storedToken)
    } catch {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      setUser(null)
      setToken(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    checkAuth()
  }, [checkAuth])

  const login = async (phone, password) => {
    const res = await api.post('/auth/login', { phone, password })
    const data = res.data
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data))
    setToken(data.token)
    setUser({
      id: data.userId,
      name: data.name,
      role: data.role,
      phone,
      mustChangePassword: data.mustChangePassword,
    })
    return data
  }

  const changePassword = async (currentPassword, newPassword) => {
    await api.post('/auth/change-password', { currentPassword, newPassword })
    setUser((prev) => ({ ...prev, mustChangePassword: false }))
  }

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setToken(null)
    setUser(null)
  }

  const isAdmin = user?.role === 'ADMIN'
  const isManager = user?.role === 'MANAGER'
  const isDeliveryBoy = user?.role === 'DELIVERY_BOY'
  const isSalesman = user?.role === 'SALESMAN'

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        login,
        logout,
        changePassword,
        isAdmin,
        isManager,
        isDeliveryBoy,
        isSalesman,
        isAuthenticated: !!token && !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
