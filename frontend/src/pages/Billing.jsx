import { useState, useEffect, useMemo } from 'react'
import { motion } from 'framer-motion'
import { Plus, Minus, ShoppingCart, Trash2, Eye, X as XIcon, RotateCcw, Printer, Edit2, Check } from 'lucide-react'
import api from '../services/api'
import SearchSelect from '../components/SearchSelect'
import Modal from '../components/Modal'
import DataTable from '../components/DataTable'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const mapToBackendUnitType = (unit) => {
  if (!unit) return 'PACK'
  const u = unit.toUpperCase().trim()
  if (u === 'PACKET' || u === 'PACKETS') return 'PACK'
  const valid = ['PACK', 'LADI', 'CRATE', 'BOTTLE', 'BOX']
  if (valid.includes(u)) return u
  if (u.includes('PACK')) return 'PACK'
  if (u.includes('BOX')) return 'BOX'
  if (u.includes('CRATE')) return 'CRATE'
  if (u.includes('BOTTLE')) return 'BOTTLE'
  if (u.includes('LADI')) return 'LADI'
  return 'PACK'
}

export default function Billing() {
  const { isAdmin, isManager, isSalesman } = useAuth()
  const [products, setProducts] = useState([])
  const [customers, setCustomers] = useState([])
  const [bills, setBills] = useState([])
  const [loading, setLoading] = useState(true)
  const toast = useToast()

  // Cart state
  const [cart, setCart] = useState([])
  const [customerId, setCustomerId] = useState('')
  const [paymentMode, setPaymentMode] = useState('CASH')
  const [discount, setDiscount] = useState(0)
  const [paidAmount, setPaidAmount] = useState(0)
  const [notes, setNotes] = useState('')
  const [creating, setCreating] = useState(false)

  // Bill detail modal
  const [viewBill, setViewBill] = useState(null)
  const [activeTab, setActiveTab] = useState('create')

  // Edit Bill state
  const [editingBill, setEditingBill] = useState(null)
  const [editNotes, setEditNotes] = useState('')
  const [editPaymentMode, setEditPaymentMode] = useState('CASH')
  const [updating, setUpdating] = useState(false)

  // Soft reservation & Bulk Confirm states
  const [selectedDrafts, setSelectedDrafts] = useState([])
  const [bulkConfirming, setBulkConfirming] = useState(false)
  const [virtualStockCache, setVirtualStockCache] = useState({})

  useEffect(() => { loadAll() }, [])

  const loadProductBatches = async (productId) => {
    try {
      const res = await api.get(`/stock/batches/${productId}`)
      const batches = res.data.data || []
      const totalVirtualAvailable = batches.reduce((sum, b) => {
        const remaining = Number(b.secondaryRemaining || 0)
        const reserved = Number(b.secondarySoftReserved || 0)
        return sum + Math.max(0, remaining - reserved)
      }, 0)
      setVirtualStockCache(prev => ({ ...prev, [productId]: totalVirtualAvailable }))
    } catch (err) {
      console.error('Failed to load virtual stock cache', err)
    }
  }

  const loadAll = async () => {
    setLoading(true)
    try {
      const [pRes, cRes, bRes] = await Promise.all([
        api.get('/products?size=500'),
        api.get('/customers?size=500'),
        api.get('/bills'),
      ])
      setProducts(pRes.data.data?.content || pRes.data.data || [])
      setCustomers(cRes.data.data?.content || cRes.data.data || [])
      setBills(bRes.data.data || [])
    } catch { toast.error('Failed to load data') }
    finally { setLoading(false) }
  }

  const addToCart = (productId) => {
    const product = products.find(p => p.id === productId)
    if (!product) return
    loadProductBatches(productId)
    const exists = cart.find(item => item.productId === productId)
    if (exists) {
      setCart(cart.map(item => {
        if (item.productId === productId) {
          if (item.canSellSecondary !== false) {
            return { ...item, quantitySecondary: item.quantitySecondary + 1 }
          } else {
            return { ...item, quantityPrimary: item.quantityPrimary + 1 }
          }
        }
        return item
      }))
    } else {
      const hasSecondary = product.canSellSecondary !== false
      const hasPrimary = product.canSellPrimary !== false
      setCart([...cart, {
        productId: product.id, name: product.name, brand: product.brand,
        quantityPrimary: !hasSecondary && hasPrimary ? 1 : 0,
        quantitySecondary: hasSecondary ? 1 : 0,
        sellPricePrimary: Number(product.sellPricePrimary || 0),
        sellPriceSecondary: Number(product.sellPriceSecondary || 0),
        gstPercent: Number(product.gstPercent || 0),
        cessPercent: Number(product.cessPercent || 0),
        primaryUnit: product.primaryUnit,
        secondaryUnit: product.secondaryUnit,
        canSellPrimary: product.canSellPrimary,
        canSellSecondary: product.canSellSecondary,
      }])
    }
  }

  const updateCartItem = (productId, field, value) => {
    setCart(cart.map(item => {
      if (item.productId !== productId) return item
      return { ...item, [field]: value }
    }))
  }

  const removeFromCart = (productId) => {
    setCart(cart.filter(item => item.productId !== productId))
  }

  const { cartSubtotal, gstTotal, cessTotal } = useMemo(() => {
    return cart.reduce((acc, item) => {
      const qPri = Number(item.quantityPrimary || 0)
      const qSec = Number(item.quantitySecondary || 0)
      const gstRate = Number(item.gstPercent || 0)
      const cessRate = Number(item.cessPercent || 0)
      
      const lineTotal = (item.sellPricePrimary * qPri) + (item.sellPriceSecondary * qSec)
      const gst = lineTotal * (gstRate / 100)
      const cess = lineTotal * (cessRate / 100)
      
      acc.cartSubtotal += lineTotal
      acc.gstTotal += gst
      acc.cessTotal += cess
      return acc
    }, { cartSubtotal: 0, gstTotal: 0, cessTotal: 0 })
  }, [cart])

  const subtotal = cartSubtotal + gstTotal + cessTotal

  const grandTotal = Math.max(0, subtotal - Number(discount || 0))

  const handleCreateBill = async () => {
    if (!customerId) { toast.error('Please select a customer'); return }
    if (cart.length === 0) { toast.error('Cart is empty'); return }
    const hasItems = cart.some(item => Number(item.quantityPrimary || 0) > 0 || Number(item.quantitySecondary || 0) > 0)
    if (!hasItems) { toast.error('Please specify quantity for at least one item'); return }

    const activeCustomer = customers.find(c => c.id === customerId)
    if (activeCustomer) {
      if (activeCustomer.isNpa) {
        if (paymentMode === 'UDHAR' || paymentMode === 'PARTIAL') {
          toast.error(`Credit sales are blocked for NPA Defaulter: ${activeCustomer.name}. CASH mode only.`)
          return
        }
      }
      
      if (paymentMode === 'UDHAR' || paymentMode === 'PARTIAL') {
        const currentPending = Number(activeCustomer.totalPending || 0)
        const creditLimit = Number(activeCustomer.creditLimit || 0)
        const requestedCredit = paymentMode === 'UDHAR' ? grandTotal : Math.max(0, grandTotal - Number(paidAmount || 0))
        const projectedPending = currentPending + requestedCredit
        
        if (projectedPending > creditLimit) {
          toast.error(
            `Credit limit exceeded! Customer: ${activeCustomer.name} | Limit: ₹${creditLimit.toLocaleString('en-IN')} | Current Pending: ₹${currentPending.toLocaleString('en-IN')} | Requested Credit: ₹${requestedCredit.toLocaleString('en-IN')} | Projected Pending: ₹${projectedPending.toLocaleString('en-IN')}`
          )
          return
        }
      }
    }
    setCreating(true)
    try {
      const itemsPayload = []
      cart.forEach(item => {
        const qPri = Number(item.quantityPrimary || 0)
        const qSec = Number(item.quantitySecondary || 0)
        if (qPri > 0) {
          itemsPayload.push({
            productId: item.productId,
            unitType: mapToBackendUnitType(item.primaryUnit),
            quantity: qPri,
            freeQuantity: 0,
            gstPercent: Number(item.gstPercent || 0),
            cessPercent: Number(item.cessPercent || 0),
          })
        }
        if (qSec > 0) {
          itemsPayload.push({
            productId: item.productId,
            unitType: mapToBackendUnitType(item.secondaryUnit),
            quantity: qSec,
            freeQuantity: 0,
            gstPercent: Number(item.gstPercent || 0),
            cessPercent: Number(item.cessPercent || 0),
          })
        }
      })
      const payload = {
        customerId: customerId,
        paymentMode,
        discount: Number(discount || 0),
        paidAmount: paymentMode === 'CASH' || paymentMode === 'UPI' ? grandTotal : Number(paidAmount || 0),
        notes,
        items: itemsPayload,
        status: isSalesman ? 'DRAFT' : 'CONFIRMED'
      }
      await api.post('/bills', payload)
      toast.success('Bill created successfully!')
      setCart([]); setCustomerId(''); setDiscount(0); setPaidAmount(0); setNotes('')
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Bill creation failed')
    } finally { setCreating(false) }
  }

  const handleBulkConfirm = async () => {
    if (selectedDrafts.length === 0) {
      toast.error('No orders selected for confirmation')
      return
    }
    setBulkConfirming(true)
    try {
      const res = await api.post('/bills/bulk-confirm', selectedDrafts)
      const results = res.data.data || []
      
      const failures = results.filter(r => !r.success)
      const successCount = results.length - failures.length
      
      if (successCount > 0) {
        toast.success(`Successfully confirmed ${successCount} orders!`)
      }
      
      if (failures.length > 0) {
        failures.forEach(f => {
          const failedBill = bills.find(b => b.id === f.billId)
          const billNo = failedBill ? failedBill.billNumber : 'Unknown Order'
          toast.error(`Failed to confirm ${billNo}: ${f.message}`)
        })
      }
      
      setSelectedDrafts([])
      loadAll()
    } catch (err) {
      toast.error('Bulk confirmation failed')
    } finally {
      setBulkConfirming(false)
    }
  }

  const handleConfirmSingle = async (billId) => {
    try {
      await api.put(`/bills/${billId}/confirm`)
      toast.success('Order confirmed and dispatched successfully!')
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Confirmation failed')
    }
  }

  const cancelBill = async (billId) => {
    try {
      await api.put(`/bills/${billId}/cancel`)
      toast.success('Bill cancelled')
      loadAll()
    } catch { toast.error('Cancel failed') }
  }

  const deleteBill = async (billId) => {
    if (!window.confirm('Are you sure you want to permanently delete this cancelled bill from records?')) return
    try {
      await api.delete(`/bills/${billId}`)
      toast.success('Bill deleted successfully')
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Delete failed')
    }
  }

  const openEditBill = (bill) => {
    setEditingBill(bill)
    setEditNotes(bill.notes || '')
    setEditPaymentMode(bill.paymentMode || 'CASH')
  }

  const handleUpdateBillSubmit = async (e) => {
    e.preventDefault()
    setUpdating(true)
    try {
      await api.put(`/bills/${editingBill.id}`, {
        paymentMode: editPaymentMode,
        notes: editNotes
      })
      toast.success('Bill updated successfully')
      setEditingBill(null)
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update bill')
    } finally { setUpdating(false) }
  }

  const printBill = (bill) => {
    const printWindow = window.open('', '_blank', 'width=800,height=600')
    if (!printWindow) {
      toast.error('Popup blocker is active. Please allow popups to print/download PDF.')
      return
    }

    const itemsHtml = (bill.items || []).map(item => `
      <tr>
        <td style="padding: 10px; border-bottom: 1px solid #ddd;">
          <div style="font-weight: 600;">${item.productName}</div>
          ${item.brand ? `<div style="font-size: 11px; color: #666;">${item.brand}</div>` : ''}
        </td>
        <td style="padding: 10px; border-bottom: 1px solid #ddd; text-align: center;">
          ${item.quantity} ${item.unitType || ''}
          ${item.freeQuantity > 0 ? `<br/><span style="font-size: 11px; color: green;">+${item.freeQuantity} free</span>` : ''}
        </td>
        <td style="padding: 10px; border-bottom: 1px solid #ddd; text-align: right;">₹${Number(item.rate || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
        <td style="padding: 10px; border-bottom: 1px solid #ddd; text-align: center;">${item.gstPercent}%</td>
        <td style="padding: 10px; border-bottom: 1px solid #ddd; text-align: right; font-weight: 600;">₹${Number(item.total || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
      </tr>
    `).join('')

    const formattedDate = bill.createdAt ? new Date(bill.createdAt).toLocaleDateString('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    }) : '—'

    const gstSummaryHtml = bill.gstSummary ? `
      <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #555;">
        <span>GST Breakdown</span>
        <span>${bill.gstSummary}</span>
      </div>
    ` : ''

    const discountHtml = Number(bill.discount || 0) > 0 ? `
      <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #e11d48; font-weight: 600;">
        <span>Discount</span>
        <span>-₹${Number(bill.discount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
      </div>
    ` : ''

    const content = `
      <!DOCTYPE html>
      <html>
      <head>
        <title>Invoice - ${bill.billNumber}</title>
        <meta charset="utf-8">
        <style>
          body {
            font-family: 'Inter', -apple-system, sans-serif;
            color: #1e293b;
            margin: 0;
            padding: 40px;
            background: #fff;
          }
          @media print {
            body { padding: 0; }
            .no-print { display: none; }
          }
          table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 30px;
            margin-bottom: 30px;
          }
          th {
            background: #f8fafc;
            color: #475569;
            font-weight: 600;
            font-size: 12px;
            text-transform: uppercase;
            padding: 12px 10px;
            border-bottom: 2px solid #e2e8f0;
          }
        </style>
      </head>
      <body>
        <!-- Header -->
        <div style="display: flex; justify-content: space-between; align-items: start; border-bottom: 2px solid #f1f5f9; padding-bottom: 20px;">
          <div>
            <h1 style="margin: 0; font-size: 26px; color: #0f172a; font-weight: 800; letter-spacing: -0.5px;">LARI TRADERS</h1>
            <p style="margin: 4px 0 0 0; font-size: 13px; color: #64748b;">FMCG Wholesale & Distribution</p>
          </div>
          <div style="text-align: right;">
            <div style="background: #f1f5f9; padding: 6px 12px; border-radius: 6px; font-weight: 700; font-size: 14px; color: #0f172a; display: inline-block;">
              INVOICE
            </div>
            <p style="margin: 8px 0 0 0; font-size: 14px; font-weight: 600; color: #334155;">${bill.billNumber}</p>
          </div>
        </div>

        <!-- Info Grid -->
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 40px; margin-top: 30px;">
          <div>
            <h3 style="margin: 0 0 8px 0; font-size: 11px; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px;">Billed To</h3>
            <div style="font-size: 15px; font-weight: 700; color: #0f172a;">${bill.customerName}</div>
            ${bill.customerShopName ? `<div style="font-size: 13px; color: #475569; margin-top: 2px;">${bill.customerShopName}</div>` : ''}
            ${bill.customerPhone ? `<div style="font-size: 13px; color: #64748b; margin-top: 2px;">📞 ${bill.customerPhone}</div>` : ''}
            ${bill.customerArea ? `<div style="font-size: 13px; color: #64748b; margin-top: 2px;">📍 Area: ${bill.customerArea}</div>` : ''}
          </div>
          <div style="text-align: right;">
            <h3 style="margin: 0 0 8px 0; font-size: 11px; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px;">Invoice Details</h3>
            <div style="font-size: 13px; color: #475569;"><strong>Date:</strong> ${formattedDate}</div>
            <div style="font-size: 13px; color: #475569; margin-top: 4px;"><strong>Payment Mode:</strong> ${bill.paymentMode}</div>
            <div style="font-size: 13px; color: #475569; margin-top: 4px;"><strong>Created By:</strong> ${bill.createdBy || 'System'}</div>
            <div style="font-size: 13px; color: #475569; margin-top: 4px;">
              <strong>Status:</strong> 
              <span style="font-weight: 600; color: ${bill.status === 'ACTIVE' ? 'green' : bill.status === 'CANCELLED' ? 'red' : 'orange'}">${bill.status}</span>
            </div>
          </div>
        </div>

        <!-- Items Table -->
        <table>
          <thead>
            <tr>
              <th style="text-align: left; width: 45%;">Product</th>
              <th style="text-align: center; width: 15%;">Qty</th>
              <th style="text-align: right; width: 15%;">Rate</th>
              <th style="text-align: center; width: 10%;">GST</th>
              <th style="text-align: right; width: 15%;">Total</th>
            </tr>
          </thead>
          <tbody>
            ${itemsHtml}
          </tbody>
        </table>

        <!-- Totals Row -->
        <div style="display: flex; justify-content: flex-end; margin-top: 20px;">
          <div style="width: 300px; border-top: 2px solid #e2e8f0; padding-top: 15px;">
            <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #555;">
              <span>Subtotal (excl. GST & Cess)</span>
              <span>₹${Number(bill.subtotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
            </div>
            <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #555;">
              <span>GST Total</span>
              <span>₹${Number(bill.gstTotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
            </div>
            ${Number(bill.cessTotal || 0) > 0 ? `
            <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #555;">
              <span>Cess Total</span>
              <span>₹${Number(bill.cessTotal).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
            </div>
            ` : ''}
            ${gstSummaryHtml}
            ${discountHtml}
            <div style="display: flex; justify-content: space-between; padding: 8px 0; font-size: 18px; font-weight: 800; color: #0f172a; border-top: 1px solid #e2e8f0; margin-top: 8px;">
              <span>Grand Total</span>
              <span>₹${Number(bill.grandTotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
            </div>
            
            <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #16a34a; font-weight: 600;">
              <span>Amount Paid</span>
              <span>₹${Number(bill.paidAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
            </div>
            ${Number(bill.pendingAmount || 0) > 0 ? `
              <div style="display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #16a34a; font-weight: 600;">
                <span>Pending Balance (Udhar)</span>
                <span>₹${Number(bill.pendingAmount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
              </div>
            ` : ''}
          </div>
        </div>

        <div style="margin-top: 80px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; padding-top: 20px;">
          Thank you for your business! For any queries regarding this invoice, contact Lari Traders.
        </div>

        <div class="no-print" style="margin-top: 40px; text-align: center;">
          <button onclick="window.print()" style="background: #3b82f6; color: #fff; border: none; padding: 10px 20px; font-size: 14px; font-weight: 600; border-radius: 6px; cursor: pointer; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);">
            Print / Save as PDF
          </button>
        </div>
      </body>
      </html>
    `

    printWindow.document.open()
    printWindow.document.write(content)
    printWindow.document.close()
    
    printWindow.onload = () => {
      printWindow.focus()
      printWindow.print()
    }
  }

  const billColumns = [
    { header: 'Bill #', accessor: 'billNumber', render: (row) => <span className="font-medium">{row.billNumber}</span> },
    { header: 'Customer', accessor: 'customerName', render: (row) => (
      <div>
        <div>{row.customerName}</div>
        {row.customerShopName && <div className="text-xs text-muted">{row.customerShopName}</div>}
      </div>
    )},
    { header: 'Total', accessor: 'grandTotal', render: (row) => <span className="font-semibold">₹{Number(row.grandTotal || 0).toLocaleString('en-IN')}</span> },
    { header: 'Payment', accessor: 'paymentMode', render: (row) => {
      const colors = { CASH: 'badge-success', UPI: 'badge-info', UDHAR: 'badge-danger', PARTIAL: 'badge-warning' }
      return <span className={`badge ${colors[row.paymentMode] || 'badge-neutral'}`}>{row.paymentMode}</span>
    }},
    { header: 'Status', accessor: 'status', render: (row) => {
      const colors = { CONFIRMED: 'badge-success', ACTIVE: 'badge-success', CANCELLED: 'badge-danger', RETURNED: 'badge-warning', DRAFT: 'badge-warning' }
      return <span className={`badge ${colors[row.status] || 'badge-neutral'}`}>{row.status}</span>
    }},
    { header: 'Created By', accessor: 'createdBy', render: (row) => <span className="font-medium text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.createdBy || 'System'}</span> },
    { header: 'Date', accessor: 'createdAt', render: (row) => row.createdAt ? new Date(row.createdAt).toLocaleDateString('en-IN') : '—' },
  ]

  const bookingColumns = [
    {
      header: (
        <input
          type="checkbox"
          checked={selectedDrafts.length === bills.filter(b => b.status === 'DRAFT').length && selectedDrafts.length > 0}
          onChange={(e) => {
            if (e.target.checked) {
              setSelectedDrafts(bills.filter(b => b.status === 'DRAFT').map(b => b.id))
            } else {
              setSelectedDrafts([])
            }
          }}
        />
      ),
      key: 'select',
      render: (row) => (
        <input
          type="checkbox"
          checked={selectedDrafts.includes(row.id)}
          onChange={(e) => {
            if (e.target.checked) {
              setSelectedDrafts([...selectedDrafts, row.id])
            } else {
              setSelectedDrafts(selectedDrafts.filter(id => id !== row.id))
            }
          }}
        />
      )
    },
    { header: 'Order #', accessor: 'billNumber', render: (row) => <span className="font-medium">{row.billNumber}</span> },
    { header: 'Customer', accessor: 'customerName', render: (row) => (
      <div>
        <div>{row.customerName}</div>
        {row.customerShopName && <div className="text-xs text-muted">{row.customerShopName}</div>}
      </div>
    )},
    { header: 'Grand Total', accessor: 'grandTotal', render: (row) => <span className="font-semibold">₹{Number(row.grandTotal || 0).toLocaleString('en-IN')}</span> },
    { header: 'Payment Mode', accessor: 'paymentMode', render: (row) => {
      const colors = { CASH: 'badge-success', UPI: 'badge-info', UDHAR: 'badge-danger', PARTIAL: 'badge-warning' }
      return <span className={`badge ${colors[row.paymentMode] || 'badge-neutral'}`}>{row.paymentMode}</span>
    }},
    { header: 'Booked By', accessor: 'createdBy', render: (row) => <span className="font-medium text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.createdBy || 'System'}</span> },
    { header: 'Date', accessor: 'createdAt', render: (row) => row.createdAt ? new Date(row.createdAt).toLocaleDateString('en-IN') : '—' },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <h2 className="page-title">Billing</h2>
      </div>

      <div className="tabs">
        {!isSalesman && (
          <button className={`tab ${activeTab === 'create' ? 'active' : ''}`} onClick={() => setActiveTab('create')}>
            <ShoppingCart size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> New Bill
          </button>
        )}
        {isSalesman && (
          <button className={`tab ${activeTab === 'create' ? 'active' : ''}`} onClick={() => setActiveTab('create')}>
            <ShoppingCart size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> 📝 Book Draft Order
          </button>
        )}
        <button className={`tab ${activeTab === 'history' ? 'active' : ''}`} onClick={() => setActiveTab('history')}>
          Bill History ({bills.filter(b => b.status !== 'DRAFT').length})
        </button>
        {(isAdmin || isManager) && (
          <button className={`tab ${activeTab === 'bookings' ? 'active' : ''}`} onClick={() => setActiveTab('bookings')}>
            📥 Order Bookings ({bills.filter(b => b.status === 'DRAFT').length})
          </button>
        )}
      </div>

      {activeTab === 'create' && (
        <div className="billing-layout" style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 'var(--space-6)' }}>
          {/* Left — Product selector */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">Add Products</span>
              <span className="text-sm text-muted">{cart.length} items in cart</span>
            </div>
            <div style={{ marginBottom: 'var(--space-4)' }}>
              <SearchSelect
                options={products.filter(p => p.active !== false)}
                value=""
                onChange={addToCart}
                placeholder="Search products to add..."
                labelKey="name"
                valueKey="id"
                renderOption={(p) => (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>{p.name} {p.brand && <span className="text-muted text-xs">({p.brand})</span>}</span>
                    <span className="text-accent text-sm">₹{p.sellPriceSecondary}</span>
                  </div>
                )}
              />
            </div>

            {/* Cart items */}
            {cart.length === 0 ? (
              <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
                <ShoppingCart size={48} style={{ color: 'var(--color-text-muted)', opacity: 0.3 }} />
                <p className="empty-state-title">Cart is empty</p>
                <p className="empty-state-text">Search and add products to create a bill</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {cart.map((item, idx) => (
                  <motion.div
                    key={item.productId}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 20 }}
                    transition={{ delay: idx * 0.05 }}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 'var(--space-4)',
                      padding: 'var(--space-3) var(--space-4)',
                      background: 'var(--color-bg-secondary)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--color-border)',
                    }}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div className="font-medium truncate">{item.name}</div>
                      {item.brand && <div className="text-xs text-muted">{item.brand}</div>}
                      <div className="text-xs mt-1" style={{ fontSize: '11px', fontWeight: '600' }}>
                        {virtualStockCache[item.productId] !== undefined ? (
                          virtualStockCache[item.productId] <= 0 ? (
                            <span style={{ color: 'var(--color-danger)' }}>⚠️ Low Stock (Soft-Reserved by others)</span>
                          ) : (
                            <span style={{ color: 'var(--color-success)' }}>✓ Available: {virtualStockCache[item.productId]} units</span>
                          )
                        ) : (
                          <span className="text-muted">Checking available stock...</span>
                        )}
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: 'var(--space-4)', alignItems: 'center' }}>
                      {item.canSellPrimary !== false && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                          <span className="text-xs text-muted" style={{ fontWeight: 'var(--font-weight-semibold)' }}>Primary</span>
                          <button className="btn btn-ghost btn-icon btn-sm" onClick={() => updateCartItem(item.productId, 'quantityPrimary', Math.max(0, Number(item.quantityPrimary || 0) - 1))}>
                            <Minus size={14} />
                          </button>
                          <input
                            className="form-input"
                            type="number"
                            min="0"
                            value={item.quantityPrimary}
                            onChange={e => updateCartItem(item.productId, 'quantityPrimary', e.target.value)}
                            style={{ width: 50, textAlign: 'center', padding: 'var(--space-1)', fontSize: 'var(--font-size-sm)' }}
                          />
                          <button className="btn btn-ghost btn-icon btn-sm" onClick={() => updateCartItem(item.productId, 'quantityPrimary', Number(item.quantityPrimary || 0) + 1)}>
                            <Plus size={14} />
                          </button>
                        </div>
                      )}

                      {item.canSellSecondary !== false && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                          <span className="text-xs text-muted" style={{ fontWeight: 'var(--font-weight-semibold)' }}>Secondary</span>
                          <button className="btn btn-ghost btn-icon btn-sm" onClick={() => updateCartItem(item.productId, 'quantitySecondary', Math.max(0, Number(item.quantitySecondary || 0) - 1))}>
                            <Minus size={14} />
                          </button>
                          <input
                            className="form-input"
                            type="number"
                            min="0"
                            value={item.quantitySecondary}
                            onChange={e => updateCartItem(item.productId, 'quantitySecondary', e.target.value)}
                            style={{ width: 50, textAlign: 'center', padding: 'var(--space-1)', fontSize: 'var(--font-size-sm)' }}
                          />
                          <button className="btn btn-ghost btn-icon btn-sm" onClick={() => updateCartItem(item.productId, 'quantitySecondary', Number(item.quantitySecondary || 0) + 1)}>
                            <Plus size={14} />
                          </button>
                        </div>
                      )}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                      <span className="text-xs text-muted" style={{ fontWeight: 'var(--font-weight-semibold)' }}>GST %</span>
                      <input
                        className="form-input"
                        type="number"
                        min="0"
                        max="40"
                        step="0.01"
                        value={item.gstPercent}
                        onChange={e => updateCartItem(item.productId, 'gstPercent', e.target.value)}
                        style={{ width: 52, textAlign: 'center', padding: 'var(--space-1)', fontSize: 'var(--font-size-sm)' }}
                      />
                    </div>

                    {item.cessPercent > 0 && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                        <span className="text-xs text-muted" style={{ fontWeight: 'var(--font-weight-semibold)' }}>Cess %</span>
                        <input
                          className="form-input"
                          type="number"
                          min="0"
                          max="100"
                          step="0.01"
                          value={item.cessPercent}
                          onChange={e => updateCartItem(item.productId, 'cessPercent', e.target.value)}
                          style={{ width: 52, textAlign: 'center', padding: 'var(--space-1)', fontSize: 'var(--font-size-sm)' }}
                        />
                      </div>
                    )}

                    <div style={{ width: 90, textAlign: 'right', fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--font-size-sm)' }}>
                      ₹{((item.sellPricePrimary * Number(item.quantityPrimary || 0)) + (item.sellPriceSecondary * Number(item.quantitySecondary || 0))).toLocaleString('en-IN')}
                    </div>
                    <button className="btn btn-ghost btn-icon btn-sm" onClick={() => removeFromCart(item.productId)} style={{ color: 'var(--color-danger)' }}>
                      <Trash2 size={14} />
                    </button>
                  </motion.div>
                ))}
              </div>
            )}
          </div>

          {/* Right — Bill summary */}
          <div className="card" style={{ alignSelf: 'start', position: 'sticky', top: 'calc(var(--topbar-height) + var(--space-6))' }}>
            <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="card-title">Bill Summary</span>
              {isSalesman && (
                <span className="badge badge-warning" style={{ fontSize: '10px', padding: '3px 8px' }}>
                  📝 Salesman Draft Mode
                </span>
              )}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label">Customer *</label>
                <SearchSelect
                  options={customers.filter(c => c.active !== false)}
                  value={customerId}
                  onChange={setCustomerId}
                  placeholder="Select customer..."
                  labelKey="name"
                  valueKey="id"
                  renderOption={(c) => (
                    <div>
                      <span>{c.name}</span>
                      {c.shopName && <span className="text-muted text-xs"> — {c.shopName}</span>}
                    </div>
                  )}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Payment Mode</label>
                <select className="form-select" value={paymentMode} onChange={e => setPaymentMode(e.target.value)}>
                  <option value="CASH">Cash</option>
                  <option value="UPI">UPI</option>
                  <option value="UDHAR">Udhar (Credit)</option>
                  <option value="PARTIAL">Partial</option>
                </select>
              </div>
              {paymentMode === 'PARTIAL' && (
                <div className="form-group">
                  <label className="form-label">Paid Amount ₹</label>
                  <input className="form-input" type="number" min="0" step="0.01" value={paidAmount} onChange={e => setPaidAmount(e.target.value)} />
                </div>
              )}
              <div className="form-group">
                <label className="form-label">Discount ₹</label>
                <input className="form-input" type="number" min="0" step="0.01" value={discount} onChange={e => setDiscount(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label">Notes</label>
                <textarea className="form-textarea" value={notes} onChange={e => setNotes(e.target.value)} placeholder="Optional notes..." rows={2} />
              </div>

              <div className="divider" style={{ margin: 'var(--space-2) 0' }} />

              {/* Totals */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                <div className="flex justify-between text-sm">
                  <span className="text-secondary">Base Subtotal</span>
                  <span>₹{cartSubtotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-secondary">GST Total</span>
                  <span>₹{gstTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                </div>
                {cessTotal > 0 && (
                  <div className="flex justify-between text-sm">
                    <span className="text-secondary">Cess Total</span>
                    <span style={{ color: 'var(--color-primary)' }}>₹{cessTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                  </div>
                )}
                {Number(discount) > 0 && (
                  <div className="flex justify-between">
                    <span className="text-secondary">Discount</span>
                    <span className="text-danger">-₹{Number(discount).toLocaleString('en-IN')}</span>
                  </div>
                )}
                <div className="flex justify-between" style={{ fontSize: 'var(--font-size-xl)', fontWeight: 'var(--font-weight-bold)', paddingTop: 'var(--space-2)', borderTop: '1px solid var(--color-border)' }}>
                  <span>Grand Total</span>
                  <motion.span
                    key={grandTotal}
                    initial={{ scale: 1.1, color: '#f59e0b' }}
                    animate={{ scale: 1, color: 'var(--color-text)' }}
                    transition={{ duration: 0.3 }}
                  >
                    ₹{grandTotal.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                  </motion.span>
                </div>
              </div>

              <motion.button
                className="btn btn-primary btn-lg w-full"
                onClick={handleCreateBill}
                disabled={creating || cart.length === 0 || !customerId}
                whileTap={{ scale: 0.97 }}
                style={{ marginTop: 'var(--space-2)' }}
              >
                {creating ? 'Creating...' : `Create Bill — ₹${grandTotal.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`}
              </motion.button>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'history' && (
        <DataTable
          columns={billColumns}
          data={bills.filter(b => b.status !== 'DRAFT')}
          loading={loading}
          searchPlaceholder="Search bills..."
          emptyMessage="No bills found"
          actions={(row) => (
            <>
              <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setViewBill(row)} title="View"><Eye size={15} /></button>
              {(isAdmin || isManager) && (
                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEditBill(row)} title="Edit Details"><Edit2 size={15} /></button>
              )}
              {(row.status === 'CONFIRMED' || row.status === 'ACTIVE') && (
                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => cancelBill(row.id)} title="Cancel Bill" style={{ color: 'var(--color-danger)' }}><XIcon size={15} /></button>
              )}
              {row.status === 'CANCELLED' && isAdmin && (
                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => deleteBill(row.id)} title="Delete Bill Permanent" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
              )}
            </>
          )}
        />
      )}

      {activeTab === 'bookings' && (isAdmin || isManager) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {selectedDrafts.length > 0 && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: 'var(--space-3) var(--space-4)',
                background: 'var(--color-bg-secondary)',
                borderRadius: 'var(--radius-lg)',
                border: '1px solid var(--color-border)',
                boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)'
              }}
            >
              <span className="font-medium text-sm text-secondary" style={{ color: 'var(--color-text-secondary)' }}>
                Selected <span className="text-primary font-bold">{selectedDrafts.length}</span> draft booking{selectedDrafts.length > 1 ? 's' : ''}
              </span>
              <button
                className="btn btn-primary"
                onClick={handleBulkConfirm}
                disabled={bulkConfirming}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)',
                  background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
                  border: 'none',
                  color: '#fff',
                  boxShadow: '0 4px 10px rgba(245, 158, 11, 0.3)'
                }}
              >
                {bulkConfirming ? 'Confirming...' : '⚡ Bulk Confirm & Dispatch'}
              </button>
            </motion.div>
          )}

          <DataTable
            columns={bookingColumns}
            data={bills.filter(b => b.status === 'DRAFT')}
            loading={loading}
            searchPlaceholder="Search draft bookings..."
            emptyMessage="No draft bookings found"
            actions={(row) => (
              <>
                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setViewBill(row)} title="View"><Eye size={15} /></button>
                <button
                  className="btn btn-ghost btn-icon btn-sm"
                  onClick={() => handleConfirmSingle(row.id)}
                  title="Confirm & Dispatch"
                  style={{ color: 'var(--color-success)' }}
                >
                  <Check size={15} />
                </button>
                <button
                  className="btn btn-ghost btn-icon btn-sm"
                  onClick={() => cancelBill(row.id)}
                  title="Cancel Booking"
                  style={{ color: 'var(--color-danger)' }}
                >
                  <XIcon size={15} />
                </button>
              </>
            )}
          />
        </div>
      )}

      {/* Bill Detail Modal */}
      <Modal isOpen={!!viewBill} onClose={() => setViewBill(null)} title={`Bill ${viewBill?.billNumber || ''}`} wide>
        {viewBill && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-row-3">
              <div>
                <p className="text-sm text-muted">Customer</p>
                <p className="font-medium">{viewBill.customerName}</p>
                {viewBill.customerShopName && <p className="text-sm text-muted">{viewBill.customerShopName}</p>}
              </div>
              <div>
                <p className="text-sm text-muted">Payment</p>
                <p className="font-medium">{viewBill.paymentMode}</p>
              </div>
              <div>
                <p className="text-sm text-muted">Created By</p>
                <p className="font-medium" style={{ color: 'var(--color-accent)' }}>{viewBill.createdBy || 'System'}</p>
              </div>
            </div>
            <div className="divider" style={{ margin: 'var(--space-2) 0' }} />
            <div className="table-container">
              <table className="table">
                <thead>
                  <tr>
                    <th>Product</th><th>Qty</th><th>Rate</th><th>GST</th><th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {(viewBill.items || []).map((item, idx) => (
                    <tr key={idx}>
                      <td>{item.productName} {item.brand && <span className="text-muted text-xs">({item.brand})</span>}</td>
                      <td>
                        {item.quantity} {item.unitType || ''} {item.freeQuantity > 0 && (
                          <span className="text-success text-xs">+{item.freeQuantity} {item.unitType || ''} free</span>
                        )}
                      </td>
                      <td>₹{Number(item.rate || 0).toLocaleString('en-IN')}</td>
                      <td>{item.gstPercent}%</td>
                      <td className="font-semibold">₹{Number(item.total || 0).toLocaleString('en-IN')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: 'var(--space-4)' }}>
              <button className="btn btn-primary" onClick={() => printBill(viewBill)} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                <Printer size={16} /> Print / Save PDF
              </button>
              <div style={{ width: 240, display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                <div className="flex justify-between text-sm"><span className="text-secondary">Subtotal</span><span>₹{Number(viewBill.subtotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span></div>
                <div className="flex justify-between text-sm"><span className="text-secondary">GST Total</span><span>₹{Number(viewBill.gstTotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span></div>
                {Number(viewBill.cessTotal || 0) > 0 && (
                  <div className="flex justify-between text-sm"><span className="text-secondary">Cess Total</span><span style={{ color: 'var(--color-primary)' }}>₹{Number(viewBill.cessTotal).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span></div>
                )}
                {Number(viewBill.discount) > 0 && <div className="flex justify-between text-sm"><span className="text-secondary">Discount</span><span className="text-danger">-₹{Number(viewBill.discount).toLocaleString('en-IN')}</span></div>}
                <div className="flex justify-between font-bold" style={{ borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-2)' }}>
                  <span>Grand Total</span><span>₹{Number(viewBill.grandTotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* Edit Bill Modal */}
      <Modal isOpen={!!editingBill} onClose={() => setEditingBill(null)} title={`Edit Bill Details — ${editingBill?.billNumber || ''}`}>
        {editingBill && (
          <form onSubmit={handleUpdateBillSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Customer</label>
              <input className="form-input" value={editingBill.customerName} disabled style={{ opacity: 0.7 }} />
            </div>
            <div className="form-group">
              <label className="form-label">Payment Mode</label>
              <select className="form-select" value={editPaymentMode} onChange={e => setEditPaymentMode(e.target.value)}>
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
                <option value="UDHAR">Udhar (Credit)</option>
                <option value="PARTIAL">Partial</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Notes</label>
              <textarea className="form-textarea" value={editNotes} onChange={e => setEditNotes(e.target.value)} placeholder="Enter details..." rows={3} />
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingBill(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={updating}>
                {updating ? 'Updating...' : 'Update Details'}
              </button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
