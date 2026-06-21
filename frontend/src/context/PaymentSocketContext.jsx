import { createContext, useContext, useState, useEffect, useRef } from 'react'
import { useAuth } from './AuthContext'
import { useToast } from './ToastContext'
import usePaymentSocket from '../hooks/usePaymentSocket'

const PaymentSocketContext = createContext(null)

export function PaymentSocketProvider({ children }) {
  const { user, isAuthenticated } = useAuth()
  const toast = useToast()
  const [livePayments, setLivePayments] = useState([])
  const seenIds = useRef(new Set())

  const isAuthorized = isAuthenticated && user && (user.role === 'ADMIN' || user.role === 'MANAGER')

  const handlePaymentReceived = (payment) => {
    if (!payment || !payment.id) return

    // 1. Prevent duplicate processing
    if (seenIds.current.has(payment.id)) return

    // Limit memory footprint of seenIds Set to prevent leaks over long session
    if (seenIds.current.size > 100) {
      const arr = [...seenIds.current]
      seenIds.current = new Set(arr.slice(-50)) // Keep the last 50 IDs
    }
    seenIds.current.add(payment.id)

    // 2. Trigger visual Toast ONLY if the browser tab/document is active and visible
    if (!document.hidden) {
      const amtStr = Number(payment.amount || 0).toLocaleString('en-IN')
      const shopStr = payment.customerShopName ? ` (${payment.customerShopName})` : ''
      toast.success(
        `₹${amtStr} collected from ${payment.customerName}${shopStr} by ${payment.collectedBy}`
      )
    }

    // 3. Append payment to the bounded list (max 20 items to prevent UI slow downs)
    setLivePayments((prev) => [payment, ...prev].slice(0, 20))
  }

  // Hook up the socket connection (only connects if isAuthorized is true)
  usePaymentSocket(handlePaymentReceived, isAuthorized)

  // Clear state when user logs out
  useEffect(() => {
    if (!isAuthenticated) {
      setLivePayments([])
      seenIds.current.clear()
    }
  }, [isAuthenticated])

  return (
    <PaymentSocketContext.Provider value={{ livePayments, setLivePayments }}>
      {children}
    </PaymentSocketContext.Provider>
  )
}

export function usePaymentSocketContext() {
  const context = useContext(PaymentSocketContext)
  if (!context) {
    throw new Error('usePaymentSocketContext must be used within PaymentSocketProvider')
  }
  return context
}
