import { createContext, useContext, useState, useEffect, useRef } from 'react'
import { useAuth } from './AuthContext'
import { useToast } from './ToastContext'
import usePaymentSocket from '../hooks/usePaymentSocket'

const PaymentSocketContext = createContext(null)

export function PaymentSocketProvider({ children }) {
  const { user, isAuthenticated } = useAuth()
  const toast = useToast()
  const [livePayments, setLivePayments] = useState([])
  const [liveDeliveries, setLiveDeliveries] = useState([])
  const seenIds = useRef(new Set())
  const seenDeliveryIds = useRef(new Set())

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
      const isWaived = payment.paymentMode === 'WAIVE_OFF'
      const actionStr = isWaived ? 'waived off for' : 'collected from'
      toast.success(
        `₹${amtStr} ${actionStr} ${payment.customerName}${shopStr} by ${payment.collectedBy}`,
        15000
      )
    }

    // 3. Append payment to the bounded list (max 20 items to prevent UI slow downs)
    setLivePayments((prev) => [payment, ...prev].slice(0, 20))
  }

  const handleDeliveryReceived = (delivery) => {
    if (!delivery || !delivery.id) return

    const key = `${delivery.id}-${delivery.status}`
    if (seenDeliveryIds.current.has(key)) return

    if (seenDeliveryIds.current.size > 100) {
      const arr = [...seenDeliveryIds.current]
      seenDeliveryIds.current = new Set(arr.slice(-50))
    }
    seenDeliveryIds.current.add(key)

    if (!document.hidden) {
      const billNum = delivery.bill?.billNumber || delivery.billNumber || 'N/A'
      const customerName = delivery.bill?.customer?.name || delivery.bill?.customerName || 'Customer'
      const shopStr = delivery.bill?.customer?.shopName || delivery.bill?.customerShopName ? ` (${delivery.bill?.customer?.shopName || delivery.bill?.customerShopName})` : ''
      const boyName = delivery.deliveryBoy?.name || delivery.deliveryBoyName || 'Self'
      const isSelf = delivery.type === 'SELF_PICKUP'

      let msg = ''
      if (delivery.status === 'DELIVERED') {
        msg = `Delivery completed for Bill #${billNum} (${customerName}${shopStr}) by ${isSelf ? 'Self Pickup' : boyName}!`
        toast.success(msg, 15000)
      } else if (delivery.status === 'COD_COLLECTED') {
        const amtStr = delivery.cashCollected ? ` (Collected: ₹${Number(delivery.cashCollected).toLocaleString('en-IN')})` : ''
        msg = `Delivery completed for Bill #${billNum} (${customerName}${shopStr}) by ${boyName}${amtStr}!`
        toast.success(msg, 15000)
      } else if (delivery.status === 'COD_PARTIAL') {
        const amtStr = delivery.cashCollected ? ` (Collected: ₹${Number(delivery.cashCollected).toLocaleString('en-IN')})` : ''
        msg = `Partial payment collected for Bill #${billNum} (${customerName}${shopStr}) by ${boyName}${amtStr}`
        toast.info(msg, 15000)
      } else if (delivery.status === 'COD_DEFAULTED') {
        msg = `Delivery default (Udhar conversion) for Bill #${billNum} (${customerName}${shopStr}) by ${boyName}`
        toast.warning(msg, 15000)
      } else if (delivery.status === 'FAILED') {
        msg = `Delivery failed for Bill #${billNum} (${customerName}${shopStr}) by ${boyName}. Notes: ${delivery.notes || 'None'}`
        toast.error(msg, 15000)
      } else if (delivery.status === 'OUT') {
        msg = `Bill #${billNum} (${customerName}${shopStr}) is out for delivery by ${boyName}`
        toast.info(msg, 10000)
      }
    }

    setLiveDeliveries((prev) => [delivery, ...prev].slice(0, 20))
  }

  // Hook up the socket connection (only connects if isAuthorized is true)
  usePaymentSocket(handlePaymentReceived, handleDeliveryReceived, isAuthorized)

  // Clear state when user logs out
  useEffect(() => {
    if (!isAuthenticated) {
      setLivePayments([])
      setLiveDeliveries([])
      seenIds.current.clear()
      seenDeliveryIds.current.clear()
    }
  }, [isAuthenticated])

  return (
    <PaymentSocketContext.Provider value={{ livePayments, setLivePayments, liveDeliveries, setLiveDeliveries }}>
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
