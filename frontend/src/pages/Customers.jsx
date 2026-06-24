import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, MessageCircle, MapPin, Eye, ChevronDown, ChevronUp } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import SearchSelect from '../components/SearchSelect'
import Pagination from '../components/Pagination'
import { useToast } from '../context/ToastContext'

const emptyForm = { name: '', shopName: '', phone: '', areaId: '', openingBalance: '', creditLimit: '', isManualOverride: false, isNpa: false }

const getCustomerLedger = (customer, allBills, allPayments) => {
  const custPayments = allPayments.filter(p => p.customerId === customer.id)
  const custBills = allBills.filter(b => b.customerId === customer.id)
  const openingBalance = Number(customer.openingBalance || 0)

  const ledger = []

  // 1. Opening Balance
  if (openingBalance > 0) {
    ledger.push({
      type: 'OPENING',
      description: 'Opening Balance',
      debit: openingBalance,
      credit: 0,
      date: new Date(customer.createdAt || 0).getTime() - 86400000,
      createdAt: customer.createdAt || null
    })
  }

  // 2. Bills (Udhar/Partial only — use grandTotal as immutable debit amount)
  custBills.forEach(b => {
    if (b.status === 'CANCELLED' || (b.paymentMode !== 'UDHAR' && b.paymentMode !== 'PARTIAL')) return
    const billAmount = Number(b.grandTotal || 0)
    if (billAmount <= 0) return
    ledger.push({
      type: 'BILL',
      id: b.id,
      billNumber: b.billNumber,
      description: b.paymentMode === 'PARTIAL' ? `Partial Bill #${b.billNumber}` : `Credit Bill #${b.billNumber}`,
      debit: billAmount,
      credit: 0,
      date: new Date(b.createdAt).getTime(),
      createdAt: b.createdAt,
      bill: b
    })

    // Down payment for PARTIAL bills
    if (b.paymentMode === 'PARTIAL') {
      const linkedPayments = custPayments.filter(p => p.billId === b.id)
      const sumLinked = linkedPayments.reduce((sum, p) => sum + Number(p.appliedAmount || p.amount || 0), 0)
      const downPayment = Number(b.paidAmount || 0) - sumLinked
      if (downPayment > 0) {
        ledger.push({
          type: 'PAYMENT',
          id: `downpayment-${b.id}`,
          description: `Down Payment at Billing — Bill ${b.billNumber}`,
          adjustmentType: 'NORMAL',
          debit: 0,
          credit: downPayment,
          date: new Date(b.createdAt).getTime(),
          createdAt: b.createdAt
        })
      }
    }
  })

  // 3. Payments
  custPayments.forEach(p => {
    const baseDesc = p.paymentMode === 'WAIVE_OFF' ? 'Round-off Adjustment' : `Payment Received (${p.paymentMode})`
    const billLink = p.billNumber ? ` — Bill ${p.billNumber}` : ''
    const notePart = p.notes ? ` · ${p.notes}` : ''
    const adjustNote = p.adjustmentNote ? ` 🔁 ${p.adjustmentNote}` : ''
    const creditAmt = Number(p.appliedAmount || p.amount || 0)
    ledger.push({
      type: 'PAYMENT',
      id: p.id,
      description: baseDesc + billLink + notePart + adjustNote,
      adjustmentType: p.adjustmentType || 'NORMAL',
      debit: 0,
      credit: creditAmt,
      date: new Date(p.paidAt).getTime(),
      createdAt: p.paidAt,
      payment: p
    })
  })

  // Sort chronological
  ledger.sort((a, b) => {
    if (a.date !== b.date) return a.date - b.date
    const typeWeight = { OPENING: 1, BILL: 2, PAYMENT: 3 }
    return typeWeight[a.type] - typeWeight[b.type]
  })

  // Calculate running balance
  let running = 0
  return ledger.map(entry => {
    running += (entry.debit - entry.credit)
    return {
      ...entry,
      runningBalance: running
    }
  })
}

export default function Customers() {
  const navigate = useNavigate()
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const [customers, setCustomers] = useState([])
  const [areas, setAreas] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [activeTab, setActiveTab] = useState('active')
  const [reminderLoading, setReminderLoading] = useState(null)
  const [showLocationModal, setShowLocationModal] = useState(false)
  const [locationCustomer, setLocationCustomer] = useState(null)
  const [locForm, setLocForm] = useState({ latitude: '', longitude: '', method: 'MANUAL' })
  // Server-side pagination
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const PAGE_SIZE = 20
  const toast = useToast()

  // Transaction History Modal States
  const [historyCustomer, setHistoryCustomer] = useState(null)
  const [customerBills, setCustomerBills] = useState([])
  const [customerPayments, setCustomerPayments] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyModalTab, setHistoryModalTab] = useState('purchases')
  const [expandedBills, setExpandedBills] = useState({})

  const toggleExpandBill = (billId) => {
    setExpandedBills(prev => ({ ...prev, [billId]: !prev[billId] }))
  }

  const openHistory = async (customer) => {
    setHistoryCustomer(customer)
    setHistoryLoading(true)
    setHistoryModalTab('udhar')
    try {
      const [billsRes, paymentsRes] = await Promise.all([
        api.get(`/bills/customer/${customer.id}`),
        api.get(`/payments/customer/${customer.id}`)
      ])
      const fetchedBills = (billsRes.data?.data || []).map(b => ({ ...b, customerId: customer.id }))
      const fetchedPayments = (paymentsRes.data || []).map(p => ({ ...p, customerId: customer.id }))
      setCustomerBills(fetchedBills)
      setCustomerPayments(fetchedPayments)
    } catch (err) {
      console.error(err)
      toast.error('Failed to load transaction history')
    } finally {
      setHistoryLoading(false)
    }
  }

  const renderCustomerLedgerTable = (customer, customerBills, customerPayments) => {
    const ledger = getCustomerLedger(customer, customerBills, customerPayments)
    
    if (ledger.length === 0) {
      return <div className="text-center py-6 text-xs text-muted">No transactions found for this customer.</div>
    }

    return (
      <div className="overflow-x-auto border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 rounded-theme shadow-sm">
        <table className="w-full border-collapse text-left text-sm text-slate-500 dark:text-slate-400">
          <thead>
            <tr className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Date & Time</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Transaction Details</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Udhar Taken (Dr / +)</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Amount Paid (Cr / -)</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">O/S Balance (After)</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
            {ledger.map((row, idx) => (
              <tr key={idx} className="border-b border-slate-200 dark:border-slate-700 text-slate-900 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors duration-150">
                <td className="px-4 py-3 align-middle whitespace-nowrap">
                  {row.createdAt ? new Date(row.createdAt).toLocaleString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true }) : '—'}
                </td>
                <td className="px-4 py-3 align-middle">
                  <span className="font-medium">{row.description}</span>
                  {row.type === 'PAYMENT' && (
                    <div className="text-xs text-muted" style={{ marginTop: 2 }}>
                      Collected By: <span className="font-semibold text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.payment?.collectedBy || 'System'}</span>
                    </div>
                  )}
                  {row.type === 'BILL' && (
                    <div className="text-xs text-muted" style={{ marginTop: 2 }}>
                      Booked By: <span className="font-semibold text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.bill?.createdBy || 'System'}</span>
                    </div>
                  )}
                </td>
                <td className="px-4 py-3 align-middle whitespace-nowrap font-medium text-slate-600 dark:text-slate-300">
                  {row.debit > 0 ? `₹${Number(row.debit).toLocaleString('en-IN')}` : '—'}
                </td>
                <td className="px-4 py-3 align-middle whitespace-nowrap font-medium text-success">
                  {row.credit > 0 ? `₹${Number(row.credit).toLocaleString('en-IN')}` : '—'}
                </td>
                <td className={`px-4 py-3 align-middle whitespace-nowrap font-bold ${row.runningBalance > 0 ? 'text-danger' : 'text-success'}`}>
                  ₹{Number(row.runningBalance).toLocaleString('en-IN')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  const renderCustomerUdharTable = (customer, customerBills, customerPayments) => {
    const custBills = customerBills.filter(b => 
      b.status !== 'CANCELLED' && 
      (b.paymentMode === 'UDHAR' || b.paymentMode === 'PARTIAL' || b.paymentMode === 'CREDIT')
    )
    
    const openingBalance = Number(customer.openingBalance || 0)
    const custPendingBills = custBills.filter(b => Number(b.pendingAmount || 0) > 0)
    const pendingBillsTotal = custPendingBills.reduce((sum, b) => sum + Number(b.pendingAmount || 0), 0)
    const totalPending = Number(customer.totalPending || 0)
    const unpaidOpeningBalance = Math.max(0, totalPending - pendingBillsTotal)
    
    const virtualOpening = []
    if (openingBalance > 0) {
      const pendingAmt = Math.min(openingBalance, unpaidOpeningBalance)
      const paidAmt = openingBalance - pendingAmt
      const isPaid = (pendingAmt <= 0)
      
      virtualOpening.push({
        id: `opening-${customer.id}`,
        billNumber: 'Opening Balance',
        grandTotal: openingBalance,
        paidAmount: paidAmt,
        pendingAmount: pendingAmt,
        createdAt: customer.createdAt || null,
        status: isPaid ? 'PAID' : 'CONFIRMED'
      })
    }
    
    const combinedEntries = [...virtualOpening, ...custBills].map(b => {
      if (b.id.startsWith?.('opening-')) {
        return {
          ...b,
          initialUdhar: b.grandTotal,
          paidAfterwards: b.paidAmount,
        }
      }
      const billPayments = customerPayments.filter(p => p.billId === b.id)
      const sumPaidAfterwards = billPayments.reduce((sum, p) => sum + Number(p.appliedAmount || p.amount || 0), 0)
      const downPayment = Math.max(0, Number(b.paidAmount || 0) - sumPaidAfterwards)
      const initialUdhar = Number(b.grandTotal || 0) - downPayment
      return {
        ...b,
        initialUdhar,
        paidAfterwards: sumPaidAfterwards,
      }
    }).sort((a, b) => {
      const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0
      const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0
      return dateB - dateA
    })
    
    if (combinedEntries.length === 0) {
      return <div className="text-center py-6 text-xs text-muted">No credit records found for this customer.</div>
    }
    
    return (
      <div className="overflow-x-auto border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 rounded-theme shadow-sm">
        <table className="w-full border-collapse text-left text-sm text-slate-500 dark:text-slate-400">
          <thead>
            <tr className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Bill #</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Date & Time</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Original Amount</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Udhar Taken</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Total Paid</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Outstanding Balance</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
            {combinedEntries.map((row, idx) => {
              const statusColors = { CONFIRMED: 'badge-neutral', PARTIAL: 'badge-warning', PAID: 'badge-success' }
              const statusLabels = { CONFIRMED: 'UNPAID', PARTIAL: 'PARTIAL', PAID: 'PAID' }
              return (
                <tr key={idx} className="border-b border-slate-200 dark:border-slate-700 text-slate-900 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors duration-150">
                  <td className="px-4 py-3 align-middle font-medium">
                    {row.billNumber}
                    {!row.id.startsWith?.('opening-') && (
                      <div className="text-xs text-muted font-normal" style={{ marginTop: 2 }}>
                        Booked By: <span className="font-semibold text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.createdBy || 'System'}</span>
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 align-middle whitespace-nowrap">
                    {row.createdAt ? new Date(row.createdAt).toLocaleString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true }) : '—'}
                  </td>
                  <td className="px-4 py-3 align-middle whitespace-nowrap text-slate-500 dark:text-slate-400">
                    ₹{Number(row.grandTotal || 0).toLocaleString('en-IN')}
                  </td>
                  <td className="px-4 py-3 align-middle whitespace-nowrap font-bold text-slate-800 dark:text-slate-200">
                    ₹{Number(row.initialUdhar || 0).toLocaleString('en-IN')}
                  </td>
                  <td className="px-4 py-3 align-middle whitespace-nowrap text-success font-semibold">
                    ₹{Number(row.paidAfterwards || 0).toLocaleString('en-IN')}
                  </td>
                  <td className="px-4 py-3 align-middle whitespace-nowrap text-danger font-bold">
                    ₹{Number(row.pendingAmount || 0).toLocaleString('en-IN')}
                  </td>
                  <td className="px-4 py-3 align-middle">
                    <span className={`badge ${statusColors[row.status] || 'badge-neutral'}`}>
                      {statusLabels[row.status] || row.status}
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    )
  }

  const renderCustomerPurchaseTable = (customer, customerBills) => {
    const activeBills = customerBills.filter(b => b.status !== 'CANCELLED')
    if (activeBills.length === 0) {
      return <div className="text-center py-6 text-xs text-muted">No purchase records found for this customer.</div>
    }
    
    return (
      <div className="overflow-x-auto border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 rounded-theme shadow-sm">
        <table className="w-full border-collapse text-left text-sm text-slate-500 dark:text-slate-400">
          <thead>
            <tr className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider" style={{ width: '40px' }}></th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Bill #</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Date & Time</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Payment Mode</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Total Amount</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Paid Amount</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Pending Amount</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Status</th>
            </tr>
          </thead>
            {activeBills.map((bill) => {
              const isExpanded = !!expandedBills[bill.id]
              const statusColors = { CONFIRMED: 'badge-success', PARTIAL: 'badge-warning', PAID: 'badge-success', DRAFT: 'badge-warning' }
              const paymentColors = { CASH: 'badge-success', UPI: 'badge-info', UDHAR: 'badge-danger', PARTIAL: 'badge-warning' }
              
              return (
                <tbody key={bill.id} className="border-b last:border-0 border-slate-200 dark:border-slate-700">
                  <tr className="text-slate-900 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors duration-150 cursor-pointer" onClick={() => toggleExpandBill(bill.id)}>
                    <td className="px-4 py-3 align-middle text-center">
                      {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </td>
                    <td className="px-4 py-3 align-middle font-medium">{bill.billNumber}</td>
                    <td className="px-4 py-3 align-middle whitespace-nowrap">
                      {bill.createdAt ? new Date(bill.createdAt).toLocaleString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true }) : '—'}
                    </td>
                    <td className="px-4 py-3 align-middle">
                      <span className={`badge ${paymentColors[bill.paymentMode] || 'badge-neutral'}`}>
                        {bill.paymentMode}
                      </span>
                    </td>
                    <td className="px-4 py-3 align-middle font-bold">₹{Number(bill.grandTotal || 0).toLocaleString('en-IN')}</td>
                    <td className="px-4 py-3 align-middle text-success font-semibold">₹{Number(bill.paidAmount || 0).toLocaleString('en-IN')}</td>
                    <td className="px-4 py-3 align-middle text-danger font-semibold">₹{Number(bill.pendingAmount || 0).toLocaleString('en-IN')}</td>
                    <td className="px-4 py-3 align-middle">
                      <span className={`badge ${statusColors[bill.status] || 'badge-neutral'}`}>
                        {bill.status}
                      </span>
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr className="bg-slate-50 dark:bg-slate-900/40">
                      <td colSpan="8" className="px-6 py-4">
                        <div style={{ padding: '4px' }}>
                          <h5 className="font-semibold text-xs text-slate-500 uppercase tracking-wider mb-2">Items Purchased</h5>
                          <table className="min-w-full text-xs text-slate-600 dark:text-slate-400">
                            <thead>
                              <tr className="border-b border-slate-200 dark:border-slate-700 text-left">
                                <th className="py-1">Product</th>
                                <th className="py-1 text-center">Qty</th>
                                <th className="py-1 text-right">Rate</th>
                                <th className="py-1 text-right">GST</th>
                                <th className="py-1 text-right">Total</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(bill.items || []).map((item, idx) => (
                                <tr key={idx} className="border-b border-slate-100 dark:border-slate-800 last:border-0 text-slate-800 dark:text-slate-300">
                                  <td className="py-2">{item.productName} {item.brand && <span className="text-muted">({item.brand})</span>}</td>
                                  <td className="py-2 text-center">{item.quantity} {item.unitType} {item.freeQuantity > 0 && <span className="text-success">(+{item.freeQuantity} free)</span>}</td>
                                  <td className="py-2 text-right">₹{Number(item.rate || 0).toLocaleString('en-IN')}</td>
                                  <td className="py-2 text-right">{item.gstPercent}%</td>
                                  <td className="py-2 text-right font-medium">₹{Number(item.total || 0).toLocaleString('en-IN')}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                          <div className="mt-3 text-xs text-slate-500 flex justify-between items-center flex-wrap gap-2">
                            <span><strong>Booked By:</strong> <span className="text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{bill.createdBy || 'System'}</span></span>
                            {bill.notes && <span><strong>Notes:</strong> {bill.notes}</span>}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              )
            })}
        </table>
      </div>
    )
  }

  useEffect(() => { loadCustomers(0); loadAreas() }, [])

  const loadCustomers = useCallback(async (pg = page, tab = activeTab, search = searchQuery) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      params.set('page', pg)
      params.set('size', PAGE_SIZE)
      if (search && search.trim()) params.set('search', search.trim())
      params.set('active', tab === 'active' ? 'true' : 'false')
      const res = await api.get(`/customers?${params.toString()}`)
      const pageData = res.data.data
      setCustomers(pageData?.content || [])
      setTotalPages(pageData?.totalPages || 0)
      setTotalElements(pageData?.totalElements || 0)
    } catch { toast.error('Failed to load customers') }
    finally { setLoading(false) }
  }, [page, activeTab, searchQuery])

  const loadAreas = async () => {
    try {
      const res = await api.get('/areas')
      setAreas(res.data.data || [])
    } catch {}
  }



  const openCreate = () => { setForm({ ...emptyForm }); setEditingId(null); setShowModal(true) }

  const openEdit = (c) => {
    setForm({
      name: c.name || '', shopName: c.shopName || '', phone: c.phone || '',
      areaId: c.areaId || '', openingBalance: c.openingBalance !== undefined ? c.openingBalance.toString() : '',
      isManualOverride: c.isManualOverride || false,
      creditLimit: c.isManualOverride ? (c.manualCreditLimit || '') : '',
      isNpa: c.isNpa || false,
    })
    setEditingId(c.id)
    setShowModal(true)
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        areaId: form.areaId || null,
        openingBalance: Number(form.openingBalance || 0),
        creditLimit: form.isManualOverride ? Number(form.creditLimit || 0) : null
      }
      if (editingId) {
        await api.put(`/customers/${editingId}`, payload)
        toast.success('Customer updated!')
      } else {
        await api.post('/customers', payload)
        toast.success('Customer created!')
      }
      setShowModal(false)
      loadCustomers(0, activeTab, searchQuery)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Save failed')
    } finally { setSaving(false) }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/customers/${deleteTarget}`)
      toast.success('Customer deactivated')
      setDeleteTarget(null)
      loadCustomers(0, activeTab, searchQuery)
    } catch { toast.error('Deactivation failed') }
  }

  const sendReminder = async (customerId) => {
    setReminderLoading(customerId)
    try {
      const res = await api.post(`/customers/${customerId}/reminder`)
      const data = res.data.data
      if (data?.whatsappLink) {
        window.open(data.whatsappLink, '_blank')
        toast.success('WhatsApp reminder opened!')
      } else {
        toast.info(data?.message || 'Reminder generated')
      }
    } catch { toast.error('Failed to generate reminder') }
    finally { setReminderLoading(null) }
  }

  const openLocationModal = (c) => {
    setLocationCustomer(c)
    setLocForm({
      latitude: c.latitude !== null && c.latitude !== undefined ? c.latitude : '',
      longitude: c.longitude !== null && c.longitude !== undefined ? c.longitude : '',
      method: c.locationMethod || 'MANUAL',
    })
    setShowLocationModal(true)
  }

  const handleCaptureLocation = () => {
    if (!navigator.geolocation) {
      toast.error('Geolocation is not supported by your browser')
      return
    }
    toast.info('Capturing GPS location...')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocForm({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          method: 'BROWSER_GPS',
        })
        toast.success('Current location captured!')
      },
      (error) => {
        toast.error('Failed to get location: ' + error.message)
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }

  const handleSaveLocation = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await api.put(`/customers/${locationCustomer.id}/location`, {
        latitude: parseFloat(locForm.latitude),
        longitude: parseFloat(locForm.longitude),
        method: locForm.method,
      })
      toast.success('Location updated successfully!')
      setShowLocationModal(false)
      loadCustomers()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update location')
    } finally {
      setSaving(false)
    }
  }

  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const columns = [
    { header: 'Customer', accessor: 'name', render: (row) => (
      <div title={`Name: ${row.name}${row.customerCode ? ` | Code: ${row.customerCode}` : ''}`} style={{ cursor: 'help' }}>
        <div style={{ fontWeight: 'var(--font-weight-medium)' }}>{row.shopName || row.name}</div>
        {isMobile && row.areaName && (
          <div className="text-xs text-muted" style={{ display: 'flex', alignItems: 'center', gap: '3px', marginTop: '2px' }}>
            <span>📍 {row.areaName}</span>
          </div>
        )}
      </div>
    )},
    { header: 'Phone', accessor: 'phone' },
    { header: 'Area', accessor: 'areaName', render: (row) => row.areaName || <span className="text-muted">—</span> },
    { header: 'Outstanding', accessor: 'totalPending', render: (row) => {
      const pending = Number(row.totalPending || 0)
      return (
        <span style={{ color: pending > 0 ? 'var(--color-danger)' : 'var(--color-success)', fontWeight: 'var(--font-weight-semibold)' }}>
          ₹{pending.toLocaleString('en-IN')}
        </span>
      )
    }},
    { header: 'Credit Limit & Status', accessor: 'effectiveCreditLimit', render: (row) => {
      const pending = Number(row.totalPending || 0)
      const effectiveLimit = Number(row.effectiveCreditLimit || 0)
      const isManual = row.isManualOverride
      const autoEligible = row.autoEligible

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxWidth: '200px' }}>
          {isManual ? (
            <span className="badge badge-success" style={{ alignSelf: 'flex-start', fontSize: '9px', padding: '2px 6px' }}>Admin Override</span>
          ) : autoEligible ? (
            <span className="badge badge-info" style={{ alignSelf: 'flex-start', fontSize: '9px', padding: '2px 6px' }}>Loyalty Unlocked</span>
          ) : (
            <span className="badge badge-neutral" style={{ alignSelf: 'flex-start', fontSize: '9px', padding: '2px 6px', color: 'var(--color-text-muted)', border: '1px dashed var(--color-border)', background: 'transparent' }}>🔒 Credit Locked</span>
          )}

          <div style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: '13px', color: effectiveLimit > 0 ? 'var(--color-text)' : 'var(--color-text-muted)' }}>
            Limit: ₹{effectiveLimit.toLocaleString('en-IN')}
          </div>

          {!isManual && !autoEligible && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', marginTop: '2px' }}>
              <div>
                <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Route Age: {row.daysActive || 0}/30 days</span>
                  <span>{Math.min(Math.round(((row.daysActive || 0)/30)*100), 100)}%</span>
                </div>
                <div style={{ width: '100%', height: '3px', background: 'var(--color-border)', borderRadius: '2px', overflow: 'hidden', marginTop: '1px' }}>
                  <div style={{ width: `${Math.min(((row.daysActive || 0)/30)*100, 100)}%`, height: '100%', background: 'var(--color-info)' }} />
                </div>
              </div>

              <div>
                <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', display: 'flex', justifyContent: 'space-between', marginTop: '1px' }}>
                  <span>Paid: ₹{Math.round(row.cumulativePaidAmount || 0).toLocaleString('en-IN')}/25k</span>
                  <span>{Math.min(Math.round(((row.cumulativePaidAmount || 0)/25000)*100), 100)}%</span>
                </div>
                <div style={{ width: '100%', height: '3px', background: 'var(--color-border)', borderRadius: '2px', overflow: 'hidden', marginTop: '1px' }}>
                  <div style={{ width: `${Math.min(((row.cumulativePaidAmount || 0)/25000)*100, 100)}%`, height: '100%', background: 'var(--color-accent)' }} />
                </div>
              </div>
            </div>
          )}

          {pending > 0 && effectiveLimit > 0 && (
            <div style={{ marginTop: '2px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '9px', color: 'var(--color-danger)', fontWeight: 600 }}>
                <span>O/S Credit Used:</span>
                <span>{Math.min(Math.round((pending / effectiveLimit) * 100), 100)}%</span>
              </div>
              <div style={{ width: '100%', height: '4px', background: 'var(--color-border)', borderRadius: '2px', overflow: 'hidden', marginTop: '1px' }}>
                <div style={{ width: `${Math.min((pending / effectiveLimit) * 100, 100)}%`, height: '100%', background: 'var(--color-danger)' }} />
              </div>
            </div>
          )}
        </div>
      )
    }},
    { header: 'Status', key: 'status', render: (row) => (
      <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
        {row.isNpa && <span className="badge badge-danger">NPA</span>}
        {row.inactive && <span className="badge badge-warning">Inactive</span>}
        {!row.isNpa && !row.inactive && <span className="badge badge-success">Active</span>}
      </div>
    )},
    { header: 'Location', key: 'location', render: (row) => row.hasLocation
      ? <MapPin size={16} style={{ color: 'var(--color-success)' }} />
      : <span className="text-muted text-xs">No GPS</span>,
      sortable: false,
    },
  ].filter(col => {
    if (isMobile) {
      return !['phone', 'areaName', 'location', 'status'].includes(col.accessor || col.key)
    }
    return true
  })

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Customers</h2>
          <p className="page-subtitle">{totalElements} customers total</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={openCreate} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add Customer
          </motion.button>
        </div>
      </div>

      {/* Search */}
      <div style={{ marginBottom: 'var(--space-4)' }}>
        <input
          className="form-input"
          placeholder="Search by name, phone, shop name..."
          value={searchQuery}
          onChange={e => {
            const q = e.target.value
            setSearchQuery(q)
            setPage(0)
            loadCustomers(0, activeTab, q)
          }}
          style={{ maxWidth: '300px', height: '38px' }}
        />
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'active' ? 'active' : ''}`} onClick={() => { setActiveTab('active'); setPage(0); loadCustomers(0, 'active', searchQuery) }}>
          Active
        </button>
        <button className={`tab ${activeTab === 'inactive' ? 'active' : ''}`} onClick={() => { setActiveTab('inactive'); setPage(0); loadCustomers(0, 'inactive', searchQuery) }}>
          Inactive
        </button>
      </div>

      <DataTable
        columns={columns}
        data={customers}
        loading={loading}
        searchable={false}
        emptyMessage="No customers found"
        actions={(row) => (
          <>
            <button 
              className="btn btn-ghost btn-icon btn-sm" 
              onClick={() => openHistory(row)} 
              title="View Transaction History" 
              style={{ color: 'var(--color-accent)' }}
            >
              <Eye size={15} />
            </button>
            {Number(row.totalPending || 0) > 0 && (
              <button
                className="btn btn-ghost btn-icon btn-sm"
                onClick={() => sendReminder(row.id)}
                title="Send WhatsApp Reminder"
                disabled={reminderLoading === row.id}
                style={{ color: 'var(--color-success)' }}
              >
                <MessageCircle size={15} />
              </button>
            )}
            <button 
              className="btn btn-ghost btn-icon btn-sm" 
              onClick={() => openLocationModal(row)} 
              title="Set GPS Location" 
              style={{ color: 'var(--color-info)' }}
            >
              <MapPin size={15} />
            </button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(row)} title="Edit"><Edit2 size={15} /></button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} title="Deactivate" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
          </>
        )}
      />
      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={PAGE_SIZE}
        onPageChange={(p) => { setPage(p); loadCustomers(p, activeTab, searchQuery) }}
      />

      {/* Create/Edit Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editingId ? 'Edit Customer' : 'Add Customer'}>
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Customer Name *</label>
            <input className="form-input" value={form.name} onChange={e => updateField('name', e.target.value)} required minLength={2} placeholder="e.g. Ramesh ji" />
          </div>
          <div className="form-group">
            <label className="form-label">Shop Name</label>
            <input className="form-input" value={form.shopName} onChange={e => updateField('shopName', e.target.value)} placeholder="e.g. Ramesh Kirana Store" />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Phone *</label>
              <input className="form-input" type="tel" value={form.phone} onChange={e => updateField('phone', e.target.value.replace(/\D/g, '').slice(0, 10))} required maxLength={10} placeholder="10-digit number" />
            </div>
            <div className="form-group">
              <label className="form-label">Area</label>
              <SearchSelect
                options={areas}
                value={form.areaId}
                onChange={val => updateField('areaId', val)}
                labelKey="name"
                valueKey="id"
                placeholder="Select area..."
              />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Opening Balance ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.openingBalance} onChange={e => updateField('openingBalance', e.target.value)} />
            </div>
            <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
              <label className="form-label">Credit Limit Policy</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', height: '42px' }}>
                <input 
                  type="checkbox" 
                  id="is-manual-override" 
                  checked={form.isManualOverride} 
                  onChange={e => updateField('isManualOverride', e.target.checked)} 
                  style={{ width: 18, height: 18, accentColor: 'var(--color-accent)' }} 
                />
                <label htmlFor="is-manual-override" className="form-label" style={{ margin: 0, fontWeight: 'normal', cursor: 'pointer' }}>Admin Custom Override</label>
              </div>
            </div>
          </div>
          
          {form.isManualOverride && (
            <div className="form-group" style={{ marginTop: '-var(--space-2)' }}>
              <label className="form-label">Custom Credit Limit ₹ *</label>
              <input className="form-input" type="number" min="0" step="1" value={form.creditLimit} onChange={e => updateField('creditLimit', e.target.value)} required placeholder="e.g. 20000" />
              <span className="text-xs text-muted" style={{ display: 'block', marginTop: '2px' }}>Bypasses age & paid transaction requirements completely.</span>
            </div>
          )}
          <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', margin: 'var(--space-1) 0' }}>
            <input type="checkbox" id="is-npa" checked={form.isNpa} onChange={e => updateField('isNpa', e.target.checked)} style={{ width: 18, height: 18, accentColor: 'var(--color-accent)' }} />
            <label htmlFor="is-npa" className="form-label" style={{ margin: 0 }}>Mark as NPA (Defaulter credit lock)</label>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : editingId ? 'Update Customer' : 'Create Customer'}
            </motion.button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDelete}
        title="Deactivate Customer" message="This customer will be marked as inactive." confirmLabel="Deactivate" />

      {/* Set GPS Location Modal */}
      <Modal isOpen={showLocationModal} onClose={() => setShowLocationModal(false)} title={`Set GPS Location — ${locationCustomer?.name || ''}`}>
        <form onSubmit={handleSaveLocation} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <p className="text-sm text-muted">
            Set latitude and longitude coordinates for optimal delivery route planning and maps.
          </p>
          
          <button 
            type="button" 
            className="btn btn-secondary w-full" 
            onClick={handleCaptureLocation}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', border: '1px dashed var(--color-info)', color: 'var(--color-info)' }}
          >
            <MapPin size={18} /> Capture Current GPS Location
          </button>

          <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Latitude *</label>
              <input 
                className="form-input" 
                type="number" 
                step="any" 
                value={locForm.latitude} 
                onChange={e => setLocForm(lf => ({ ...lf, latitude: e.target.value }))} 
                required 
                placeholder="e.g. 26.8467" 
              />
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="form-label">Longitude *</label>
              <input 
                className="form-input" 
                type="number" 
                step="any" 
                value={locForm.longitude} 
                onChange={e => setLocForm(lf => ({ ...lf, longitude: e.target.value }))} 
                required 
                placeholder="e.g. 80.9462" 
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Location Collection Method</label>
            <select 
              className="form-select" 
              value={locForm.method} 
              onChange={e => setLocForm(lf => ({ ...lf, method: e.target.value }))}
            >
              <option value="MANUAL">Manual Input</option>
              <option value="BROWSER_GPS">Captured via Browser GPS</option>
              <option value="MOBILE_APP">Captured via Mobile App</option>
            </select>
          </div>

          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowLocationModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : 'Save Location'}
            </motion.button>
          </div>
        </form>
      </Modal>

      {/* Transaction History Modal */}
      <Modal isOpen={!!historyCustomer} onClose={() => setHistoryCustomer(null)} title="Customer Transaction History" xl>
        {historyCustomer && (() => {
          const activeBills = customerBills.filter(b => b.status !== 'CANCELLED')
          const totalPurchases = activeBills.reduce((sum, b) => sum + Number(b.grandTotal || 0), 0) + Number(historyCustomer.openingBalance || 0)
          const totalPaid = Math.max(0, totalPurchases - Number(historyCustomer.totalPending || 0))
          const billCount = activeBills.length
          const pendingCount = customerBills.filter(b => b.status !== 'CANCELLED' && Number(b.pendingAmount || 0) > 0).length
          const limit = Number(historyCustomer.effectiveCreditLimit || 0)
          const isNpa = historyCustomer.isNpa

          return (
            <div>
              {/* Customer Details Summary Cards */}
              <div className="grid-4" style={{ marginBottom: 'var(--space-6)', gap: 'var(--space-4)' }}>
                {/* Card 1: Customer Details */}
                <div className="card" style={{ padding: 'var(--space-3) var(--space-4)', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                  <div className="text-xs text-muted" style={{ marginBottom: '2px' }}>Customer Info</div>
                  <div className="font-bold text-sm text-slate-800 dark:text-slate-200">{historyCustomer.name}</div>
                  {historyCustomer.shopName && <div className="text-xs text-slate-600 dark:text-slate-400 font-medium" style={{ marginTop: '2px' }}>{historyCustomer.shopName}</div>}
                  <div className="text-xs text-muted" style={{ marginTop: '4px' }}>Phone: {historyCustomer.phone || '—'}</div>
                </div>

                {/* Card 2: Purchase & Loyalty KPIs */}
                <div className="card" style={{ padding: 'var(--space-3) var(--space-4)', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                  <div className="text-xs text-muted" style={{ marginBottom: '2px' }}>Loyalty & Purchases</div>
                  <div className="font-bold text-sm text-success">
                    ₹{totalPaid.toLocaleString('en-IN')} <span className="text-xs font-normal text-muted">paid of ₹{totalPurchases.toLocaleString('en-IN')}</span>
                  </div>
                  <div className="text-xs text-muted" style={{ marginTop: '4px' }}>
                    Bills: {billCount} | Avg Bill: ₹{billCount > 0 ? Math.round(totalPurchases / billCount).toLocaleString('en-IN') : '0'}
                  </div>
                </div>

                {/* Card 3: Outstanding Udhar */}
                <div className="card" style={{ padding: 'var(--space-3) var(--space-4)', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                  <div className="text-xs text-muted" style={{ marginBottom: '2px' }}>Outstanding Udhar</div>
                  <div className="font-bold text-sm text-danger">₹{Number(historyCustomer.totalPending || 0).toLocaleString('en-IN')}</div>
                  <div className="text-xs text-muted" style={{ marginTop: '4px' }}>
                    Pending Bills: {pendingCount}
                  </div>
                </div>

                {/* Card 4: Trust Tier & Limit */}
                <div className="card" style={{ padding: 'var(--space-3) var(--space-4)', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                  <div className="text-xs text-muted" style={{ marginBottom: '2px' }}>Credit Limit & Tier</div>
                  <div className="font-bold text-sm text-info">₹{limit.toLocaleString('en-IN')}</div>
                  <div className="text-xs font-semibold" style={{ marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    {isNpa ? (
                      <span className="badge badge-danger" style={{ fontSize: '10px', padding: '2px 8px' }}>⚠️ Defaulter (NPA)</span>
                    ) : totalPaid >= 25000 || limit >= 25000 ? (
                      <span className="badge" style={{ fontSize: '10px', padding: '2px 8px', background: 'linear-gradient(135deg, #f59e0b, #d97706)', color: 'white', border: 'none' }}>🏆 Gold Trust Tier</span>
                    ) : totalPaid >= 10000 || limit >= 10000 ? (
                      <span className="badge" style={{ fontSize: '10px', padding: '2px 8px', background: 'linear-gradient(135deg, #94a3b8, #64748b)', color: 'white', border: 'none' }}>🥈 Silver Trust Tier</span>
                    ) : (
                      <span className="badge badge-neutral" style={{ fontSize: '10px', padding: '2px 8px', color: 'var(--color-text-muted)' }}>Bronze / Regular Tier</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Toggle tabs */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
                <div className="flex gap-2">
                  <button 
                    type="button"
                    className={`btn btn-sm ${historyModalTab === 'purchases' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setHistoryModalTab('purchases')}
                  >
                    Purchase History (All Bills)
                  </button>
                  <button 
                    type="button"
                    className={`btn btn-sm ${historyModalTab === 'ledger' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setHistoryModalTab('ledger')}
                  >
                    Ledger Statement
                  </button>
                  <button 
                    type="button"
                    className={`btn btn-sm ${historyModalTab === 'udhar' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setHistoryModalTab('udhar')}
                  >
                    Bill-wise Udhar History
                  </button>
                </div>
                <h4 className="font-semibold text-sm text-slate-700 dark:text-slate-300">
                  {historyModalTab === 'purchases' ? 'Customer Complete Purchase History' : historyModalTab === 'ledger' ? 'Customer Account Ledger Statement' : 'Customer Bill-wise Udhar Records'}
                </h4>
              </div>

              {historyLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-10)' }}>
                  <div className="spinner" style={{ width: '30px', height: '30px', borderTopColor: 'var(--color-accent)' }}></div>
                  <p className="text-muted text-xs mt-3">Fetching transaction history...</p>
                </div>
              ) : (
                <div>
                  {historyModalTab === 'purchases' ? (
                    renderCustomerPurchaseTable(historyCustomer, customerBills)
                  ) : historyModalTab === 'ledger' ? (
                    renderCustomerLedgerTable(historyCustomer, customerBills, customerPayments)
                  ) : (
                    renderCustomerUdharTable(historyCustomer, customerBills, customerPayments)
                  )}
                </div>
              )}
              
              <div className="form-actions" style={{ marginTop: 'var(--space-6)' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setHistoryCustomer(null)}>Close History</button>
              </div>
            </div>
          )
        })()}
      </Modal>

    </div>
  )
}
