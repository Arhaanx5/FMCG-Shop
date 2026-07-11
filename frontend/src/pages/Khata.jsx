import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Plus, IndianRupee, Edit2, Trash2, ChevronDown, ChevronUp, Sparkles } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import SearchSelect from '../components/SearchSelect'
import StatCard from '../components/StatCard'
import OverpaymentModal from '../components/OverpaymentModal'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { getCustomerLedger, generateLedgerHtml, getCustomerLedgerForPeriod } from '../utils/ledger'

const emptyForm = { customerId: '', billId: '', amount: '', paymentMode: 'CASH', notes: '', waivedAmount: '' }

const computeRunningOutstandings = (paymentsList, billsList, customersList) => {
  const paymentsByCustomer = {}
  paymentsList.forEach(p => {
    if (!p.customerId) return
    if (!paymentsByCustomer[p.customerId]) paymentsByCustomer[p.customerId] = []
    paymentsByCustomer[p.customerId].push(p)
  })

  const billsByCustomer = {}
  billsList.forEach(b => {
    if (!b.customerId) return
    if (!billsByCustomer[b.customerId]) billsByCustomer[b.customerId] = []
    billsByCustomer[b.customerId].push(b)
  })

  const updatedPaymentsMap = {}

  customersList.forEach(customer => {
    const custId = customer.id
    const custPayments = paymentsByCustomer[custId] || []
    const custBills = billsByCustomer[custId] || []
    const openingBalance = Number(customer.openingBalance || 0)

    const ledger = []

    if (openingBalance > 0) {
      ledger.push({
        type: 'OPENING',
        amount: openingBalance,
        date: new Date(customer.createdAt || 0).getTime() - 86400000
      })
    }

    custBills.forEach(b => {
      // Both UDHAR and PARTIAL bills create outstanding — use grandTotal (immutable source of truth)
      if (b.status === 'CANCELLED' || (b.paymentMode !== 'UDHAR' && b.paymentMode !== 'PARTIAL')) return
      const billAmount = Number(b.grandTotal || 0)
      if (billAmount <= 0) return
      ledger.push({
        type: 'BILL',
        amount: billAmount,
        date: new Date(b.createdAt).getTime(),
        id: b.id
      })

      // Down payment for PARTIAL bills
      if (b.paymentMode === 'PARTIAL') {
        const linkedPayments = custPayments.filter(p => p.billId === b.id)
        const sumLinked = linkedPayments.reduce((sum, p) => sum + Number(p.appliedAmount || p.amount || 0), 0)
        const downPayment = Number(b.paidAmount || 0) - sumLinked
        if (downPayment > 0) {
          ledger.push({
            type: 'PAYMENT',
            amount: -downPayment,
            date: new Date(b.createdAt).getTime(),
            id: `downpayment-${b.id}`
          })
        }
      }
    })

    custPayments.forEach(p => {
      ledger.push({
        type: 'PAYMENT',
        // Use appliedAmount if set, fallback to amount (for backward compat)
        amount: -Number(p.appliedAmount || p.amount || 0),
        date: new Date(p.paidAt).getTime(),
        id: p.id
      })
    })

    ledger.sort((a, b) => {
      if (a.date !== b.date) return a.date - b.date
      const typeWeight = { OPENING: 1, BILL: 2, PAYMENT: 3 }
      return (typeWeight[a.type] || 0) - (typeWeight[b.type] || 0)
    })

    let running = 0
    ledger.forEach(entry => {
      running += entry.amount
      if (entry.type === 'PAYMENT') {
        updatedPaymentsMap[entry.id] = running
      }
    })
  })

  return updatedPaymentsMap
}

export default function Khata() {
  const { aiEnabled, isAdmin, isManager } = useAuth()
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const [searchParams] = useSearchParams()
  const [payments, setPayments] = useState([])
  const [todayPayments, setTodayPayments] = useState([])
  const [customers, setCustomers] = useState([])
  const [allBills, setAllBills] = useState([])
  const [pendingBills, setPendingBills] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [activeTab, setActiveTab] = useState('ledgers')
  const [searchQuery, setSearchQuery] = useState('')
  const [expandedCustomers, setExpandedCustomers] = useState({})
  const [customerPanelTab, setCustomerPanelTab] = useState({})
  const [customerPayments, setCustomerPayments] = useState({}) // { [customerId]: [payments] }
  const [customerPaymentsLoading, setCustomerPaymentsLoading] = useState({}) // { [customerId]: boolean }
  const [selectedMonths, setSelectedMonths] = useState({}) // { [customerId]: 'ALL' }
  const toast = useToast()

  const getMonthOptions = () => {
    const options = [{ value: 'ALL', label: 'All Time' }]
    const now = new Date()
    for (let i = 0; i < 12; i++) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
      const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
      const label = d.toLocaleString('en-IN', { month: 'long', year: 'numeric' })
      options.push({ value, label })
    }
    return options
  }

  // Edit Payment State
  const [editingPayment, setEditingPayment] = useState(null)
  const [editNotes, setEditNotes] = useState('')
  const [editPaymentMode, setEditPaymentMode] = useState('CASH')
  const [updating, setUpdating] = useState(false)

  // Overpayment modal state
  const [overpaymentPreview, setOverpaymentPreview] = useState(null)
  const [pendingPaymentForm, setPendingPaymentForm] = useState(null)

  // AI Reminder states
  const [aiReminderCustomer, setAiReminderCustomer] = useState(null)
  const [aiReminderText, setAiReminderText] = useState('')
  const [aiReminderLoading, setAiReminderLoading] = useState(false)
  const [showAiReminderModal, setShowAiReminderModal] = useState(false)
  const [sendingAiReminder, setSendingAiReminder] = useState(false)
  const [aiReminderLanguage, setAiReminderLanguage] = useState('HINGLISH')

  const triggerAiReminder = async (customer, lang = 'HINGLISH') => {
    setAiReminderCustomer(customer)
    setAiReminderLanguage(lang)
    setAiReminderText('')
    setAiReminderLoading(true)
    setShowAiReminderModal(true)
    
    try {
      const res = await api.get(`/khata/ai/generate-reminder?customerId=${customer.id}&language=${lang}`)
      if (res.data?.data?.draft) {
        setAiReminderText(res.data.data.draft)
      } else {
        setAiReminderText('Failed to generate draft. Please check service connection.')
      }
    } catch (err) {
      console.error(err)
      toast.error('Failed to generate AI reminder draft.')
      setAiReminderText('Failed to generate AI reminder draft.')
    } finally {
      setAiReminderLoading(false)
    }
  }

  const handleLanguageChange = (newLang) => {
    if (aiReminderCustomer) {
      triggerAiReminder(aiReminderCustomer, newLang)
    }
  }

  const sendAiReminder = async () => {
    if (!aiReminderCustomer || !aiReminderText.trim()) return
    setSendingAiReminder(true)
    
    const phone = aiReminderCustomer.phone ? aiReminderCustomer.phone.trim().replace(/\D/g, '') : ''
    let phoneWithCountry = phone
    if (phone.length === 10) {
      phoneWithCountry = '91' + phone
    }

    try {
      let isConnected = false
      try {
        const statusRes = await api.get('/customers/whatsapp/status')
        isConnected = statusRes.data.data?.status === 'CONNECTED'
      } catch (err) {
        console.error('Failed to check WhatsApp status', err)
      }

      if (isConnected && (isAdmin || isManager)) {
        await api.post('/customers/whatsapp/send-text', {
          phone: phoneWithCountry,
          message: aiReminderText
        })
        toast.success('AI Reminder sent successfully via WhatsApp!')
      } else {
        const encodedText = encodeURIComponent(aiReminderText)
        const whatsappUrl = `https://api.whatsapp.com/send?phone=${phoneWithCountry}&text=${encodedText}`
        window.open(whatsappUrl, '_blank')
        toast.success('Redirecting to WhatsApp web...')
      }
      setShowAiReminderModal(false)
    } catch (err) {
      console.error(err)
      toast.error('Failed to send WhatsApp message. Falling back to browser redirect...')
      const encodedText = encodeURIComponent(aiReminderText)
      const whatsappUrl = `https://api.whatsapp.com/send?phone=${phoneWithCountry}&text=${encodedText}`
      window.open(whatsappUrl, '_blank')
      setShowAiReminderModal(false)
    } finally {
      setSendingAiReminder(false)
    }
  }

  useEffect(() => { loadAll(true) }, [])

  useEffect(() => {
    if (!loading && customers.length > 0) {
      const paramCustId = searchParams.get('customerId')
      if (paramCustId) {
        setExpandedCustomers({ [paramCustId]: true })
        setActiveTab('ledgers')
        setTimeout(() => {
          const el = document.getElementById(`customer-card-${paramCustId}`)
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'start' })
          }
        }, 100)
      }
    }
  }, [loading, customers, searchParams])

  const loadAll = async (showSpinner = false) => {
    if (showSpinner) setLoading(true)
    try {
      const [pRes, tRes, cRes, bRes, billsRes] = await Promise.all([
        api.get('/payments'),
        api.get('/payments/today'),
        api.get('/customers?size=500'),
        api.get('/bills/pending'),
        api.get('/bills?size=5000'),
      ])
      const fetchedPayments = pRes.data || []
      const fetchedTodayPayments = tRes.data || []
      const fetchedCustomers = cRes.data.data?.content || cRes.data.data || []
      const fetchedPendingBills = bRes.data?.data || []
      const rawBills = billsRes.data?.data?.content || billsRes.data?.data || billsRes.data || []
      const fetchedAllBills = Array.isArray(rawBills) ? rawBills : (rawBills.content || [])

      const runningMap = computeRunningOutstandings(fetchedPayments, fetchedAllBills, fetchedCustomers)

      const mapWithRunning = (list) => list.map(p => ({
        ...p,
        customerPendingBalance: runningMap[p.id] !== undefined ? runningMap[p.id] : p.customerPendingBalance
      }))

      setPayments(mapWithRunning(fetchedPayments))
      setTodayPayments(mapWithRunning(fetchedTodayPayments))
      setCustomers(fetchedCustomers)
      setAllBills(fetchedAllBills)
      setPendingBills(fetchedPendingBills)
    } catch (err) {
      console.error(err)
      toast.error('Failed to load payments')
    } finally {
      if (showSpinner) setLoading(false)
    }
  }

  const handleWhatsAppShare = async (customer, periodMonth = 'ALL') => {
    if (!customer) return
    
    const phone = customer.phone ? customer.phone.trim().replace(/\D/g, '') : ''
    let phoneWithCountry = phone
    if (phone.length === 10) {
      phoneWithCountry = '91' + phone
    }

    toast.info('Checking WhatsApp connection...')
    let isConnected = false
    try {
      const statusRes = await api.get('/customers/whatsapp/status')
      isConnected = statusRes.data.data?.status === 'CONNECTED'
    } catch (err) {
      console.error('Failed to check WhatsApp status, falling back to browser redirect', err)
    }

    if (isConnected) {
      toast.info('Fetching payment records...')
      let custPayments = customerPayments[customer.id]
      if (!custPayments) {
        try {
          const res = await api.get(`/payments/customer/${customer.id}`)
          custPayments = res.data || []
          setCustomerPayments(prev => ({ ...prev, [customer.id]: custPayments }))
        } catch (err) {
          console.error(err)
          toast.error('Failed to load transaction history')
          return
        }
      }

      toast.info('Generating PDF Ledger and sending to WhatsApp...')
      const { ledger, openingBalance, totalUdhar, totalPaid, outstanding } = getCustomerLedgerForPeriod(customer, allBills, custPayments, periodMonth)
      
      let dateHeading = 'Outstanding Statement'
      if (periodMonth !== 'ALL') {
        const [year, month] = periodMonth.split('-').map(Number)
        const monthName = new Date(year, month - 1, 1).toLocaleString('en-IN', { month: 'long', year: 'numeric' })
        dateHeading = `Statement for ${monthName}`
      }

      const text = `*LARI TRADERS*
${dateHeading}
Customer: *${customer.name}*
Shop Name: *${customer.shopName || '—'}*
${periodMonth !== 'ALL' ? `Opening Balance (Pichla Outstanding): *₹${openingBalance.toLocaleString('en-IN')}*\nNew Credit (Naya Udhar): *₹${totalUdhar.toLocaleString('en-IN')}*\nNew Paid (Naya Bhugtan): *₹${totalPaid.toLocaleString('en-IN')}*\nTotal Outstanding (Kul Baki): *₹${outstanding.toLocaleString('en-IN')}*` : `Total Udhar Taken: *₹${totalUdhar.toLocaleString('en-IN')}*\nTotal Amount Paid: *₹${totalPaid.toLocaleString('en-IN')}*\nRemaining Outstanding: *₹${outstanding.toLocaleString('en-IN')}*`}

Please clear your outstanding balance. Thank you for doing business with Lari Traders!`

      const htmlContent = generateLedgerHtml(customer, ledger, totalUdhar, totalPaid, outstanding, openingBalance, periodMonth)

      toast.info('Generating PDF statement and sending to WhatsApp...')
      try {
        const pdfRes = await api.post('/customers/whatsapp/generate-pdf', { html: htmlContent })
        const base64Pdf = pdfRes.data.data.pdf
        await api.post('/customers/whatsapp/send-media', {
          phone: phoneWithCountry,
          media: base64Pdf,
          filename: `Ledger-${customer.name.replace(/\s+/g, '_')}.pdf`,
          caption: text
        })
        toast.success('Ledger statement PDF sent successfully via WhatsApp!')
      } catch (err) {
        console.error(err)
        toast.error('WhatsApp PDF delivery failed: ' + (err.response?.data?.message || err.message))
      }
    } else {
      const custPayments = customerPayments[customer.id] || []
      const { totalUdhar, totalPaid, outstanding } = getCustomerLedgerForPeriod(customer, allBills, custPayments, periodMonth)
      let dateHeading = 'Outstanding Statement'
      if (periodMonth !== 'ALL') {
        const [year, month] = periodMonth.split('-').map(Number)
        const monthName = new Date(year, month - 1, 1).toLocaleString('en-IN', { month: 'long', year: 'numeric' })
        dateHeading = `Statement for ${monthName}`
      }
      const text = `*LARI TRADERS*
${dateHeading}
Customer: *${customer.name}*
Shop Name: *${customer.shopName || '—'}*
Current Outstanding Balance: *₹${outstanding.toLocaleString('en-IN')}*

Please clear your outstanding balance as soon as possible. Thank you for doing business with Lari Traders!`
      toast.info('WhatsApp service is disconnected. Sharing via text summary...')
      const encodedText = encodeURIComponent(text)
      const whatsappUrl = `https://api.whatsapp.com/send?phone=${phoneWithCountry}&text=${encodedText}`
      window.open(whatsappUrl, '_blank')
    }
  }

  const toggleExpandCustomer = async (custId) => {
    const isExpanding = !expandedCustomers[custId]
    setExpandedCustomers(prev => ({
      ...prev,
      [custId]: isExpanding
    }))

    if (isExpanding && !customerPayments[custId]) {
      setCustomerPaymentsLoading(prev => ({ ...prev, [custId]: true }))
      try {
        const res = await api.get(`/payments/customer/${custId}`)
        setCustomerPayments(prev => ({ ...prev, [custId]: res.data || [] }))
      } catch (err) {
        toast.error('Failed to load customer payment history')
      } finally {
        setCustomerPaymentsLoading(prev => ({ ...prev, [custId]: false }))
      }
    }
  }

  const renderCustomerLedgerTable = (customer) => {
    const custPayments = customerPayments[customer.id] || []
    const custSelectedMonth = selectedMonths[customer.id] || 'ALL'
    const { ledger, openingBalance, totalUdhar, totalPaid, outstanding } = getCustomerLedgerForPeriod(customer, allBills, custPayments, custSelectedMonth)
    
    if (ledger.length === 0) {
      return (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>Statement Period:</span>
              <select 
                value={custSelectedMonth}
                onChange={(e) => setSelectedMonths(prev => ({ ...prev, [customer.id]: e.target.value }))}
                className="form-select text-xs"
                style={{ 
                  padding: '4px 8px', 
                  borderRadius: '6px', 
                  border: '1px solid var(--color-border)', 
                  background: 'var(--color-bg-primary)', 
                  color: 'var(--color-text)', 
                  fontSize: '12px' 
                }}
              >
                {getMonthOptions().map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
            <button 
              className="btn btn-secondary btn-sm"
              onClick={() => handleWhatsAppShare(customer, custSelectedMonth)}
              style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#25D366' }}
            >
              Share PDF
            </button>
          </div>
          <div className="text-center py-6 text-xs text-muted">No transactions found for this customer in this period.</div>
        </div>
      )
    }

    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>Statement Period:</span>
            <select 
              value={custSelectedMonth}
              onChange={(e) => setSelectedMonths(prev => ({ ...prev, [customer.id]: e.target.value }))}
              className="form-select text-xs"
              style={{ 
                padding: '4px 8px', 
                borderRadius: '6px', 
                border: '1px solid var(--color-border)', 
                background: 'var(--color-bg-primary)', 
                color: 'var(--color-text)', 
                fontSize: '12px' 
              }}
            >
              {getMonthOptions().map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>
          <button 
            className="btn btn-secondary btn-sm"
            onClick={() => handleWhatsAppShare(customer, custSelectedMonth)}
            style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#25D366' }}
          >
            Share PDF
          </button>
        </div>
        
        {custSelectedMonth !== 'ALL' && (
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', 
            gap: '12px', 
            marginBottom: '16px',
            background: 'var(--color-bg-primary)',
            padding: '12px',
            borderRadius: '8px',
            border: '1px solid var(--color-border)'
          }}>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Previous Outstanding (Pichli Baki)</div>
              <div style={{ fontWeight: 'bold', fontSize: '15px' }}>₹{openingBalance.toLocaleString('en-IN')}</div>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>New Udhar (Credit)</div>
              <div style={{ fontWeight: 'bold', fontSize: '15px' }}>₹{totalUdhar.toLocaleString('en-IN')}</div>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>New Bhugtan (Paid)</div>
              <div style={{ fontWeight: 'bold', fontSize: '15px', color: 'var(--color-success)' }}>- ₹{totalPaid.toLocaleString('en-IN')}</div>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Total Outstanding (Kul Baki)</div>
              <div style={{ fontWeight: 'bold', fontSize: '15px', color: outstanding > 0 ? 'var(--color-danger)' : 'var(--color-success)' }}>₹{outstanding.toLocaleString('en-IN')}</div>
            </div>
          </div>
        )}

        <div className="overflow-x-auto border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 rounded-theme shadow-sm">
          <table className="w-full border-collapse text-left text-sm text-slate-500 dark:text-slate-400">
          <thead>
            <tr className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Date & Time</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Transaction Details</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Udhar Taken (Dr / +)</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">Amount Paid (Cr / -)</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">O/S Balance (After)</th>
              <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider text-right">Actions</th>
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
                  {row.type === 'PAYMENT' && (isAdmin || isManager) && (
                    <div className="text-xs text-muted" style={{ marginTop: 2 }}>
                      Collected By: <span className="font-semibold text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.payment?.collectedBy || 'System'}</span>
                    </div>
                  )}
                  {row.type === 'BILL' && (isAdmin || isManager) && (
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
                <td className="px-4 py-3 align-middle text-right whitespace-nowrap">
                  {row.type === 'PAYMENT' && (
                    <div className="flex justify-end gap-1">
                      {(isAdmin || isManager) && (
                        <button 
                          className="btn btn-ghost btn-icon btn-sm" 
                          onClick={(e) => { e.stopPropagation(); openEditPayment(row.payment); }} 
                          title="Edit Payment"
                        >
                          <Edit2 size={14} />
                        </button>
                      )}
                      {isAdmin && (
                        <button 
                          className="btn btn-ghost btn-icon btn-sm" 
                          onClick={(e) => { e.stopPropagation(); deletePayment(row.payment.id); }} 
                          title="Delete Payment & Reverse Balance" 
                          style={{ color: 'var(--color-danger)' }}
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
    )
  }

  const renderCustomerUdharTable = (customer) => {
    // Filter all credit/partial bills for this customer (excluding CANCELLED, including PAID)
    const custBills = allBills.filter(b => 
      b.customerId === customer.id && 
      b.status !== 'CANCELLED' && 
      (b.paymentMode === 'UDHAR' || b.paymentMode === 'PARTIAL')
    )
    
    // Virtual opening bill if customer has opening balance
    const openingBalance = Number(customer.openingBalance || 0)
    const custPendingBills = pendingBills.filter(b => b.customerId === customer.id)
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
    
    const custPayments = customerPayments[customer.id] || []
    
    const combinedEntries = [...virtualOpening, ...custBills].map(b => {
      if (b.id.startsWith?.('opening-')) {
        return {
          ...b,
          initialUdhar: b.grandTotal,
          paidAfterwards: b.paidAmount,
        }
      }
      const billPayments = custPayments.filter(p => p.billId === b.id)
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
                    {!row.id.startsWith?.('opening-') && (isAdmin || isManager) && (
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

  const renderCustomerLedgersSection = () => {
    const sortedCustomers = [...customers].sort((a, b) => a.name.localeCompare(b.name))
    const filteredCustomers = sortedCustomers.filter(c => {
      const query = searchQuery.toLowerCase().trim()
      if (!query) return true
      return (
        c.name?.toLowerCase().includes(query) ||
        c.shopName?.toLowerCase().includes(query) ||
        c.phone?.toLowerCase().includes(query) ||
        c.customerCode?.toLowerCase().includes(query)
      )
    })

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div style={{ marginBottom: 'var(--space-2)' }}>
          <input
            className="form-input"
            placeholder="Search customer by name, shop name, phone..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            style={{ maxWidth: '360px', height: '38px' }}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {filteredCustomers.length === 0 ? (
            <div className="table-empty card">No customers found</div>
          ) : (
            filteredCustomers.map(customer => {
              const isExpanded = !!expandedCustomers[customer.id]
              const custPayments = payments.filter(p => p.customerId === customer.id)
              const outstanding = Number(customer.totalPending || 0)
              const totalPaid = custPayments.reduce((sum, p) => sum + Number(p.amount || 0), 0)
              const totalUdhar = totalPaid + outstanding

              return (
                <div key={customer.id} id={`customer-card-${customer.id}`} className="card" style={{ padding: '0', overflow: 'hidden' }}>
                  {/* Accordion Header */}
                  <div 
                    style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'space-between', 
                      padding: 'var(--space-4) var(--space-6)',
                      cursor: 'pointer',
                      background: 'var(--color-surface)',
                      flexWrap: 'wrap',
                      gap: 'var(--space-4)'
                    }}
                    onClick={() => toggleExpandCustomer(customer.id)}
                  >
                    {/* Left: Customer Info */}
                    <div style={{ flex: '1 1 220px', minWidth: '200px' }}>
                      <h3 className="font-semibold text-base" style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                        {customer.name}
                        {customer.customerCode && <span className="text-xs text-muted">({customer.customerCode})</span>}
                      </h3>
                      {customer.shopName && <div className="text-xs text-muted" style={{ marginTop: '2px' }}>{customer.shopName}</div>}
                      <div className="text-xs text-muted" style={{ marginTop: '4px', display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
                        <span>{customer.phone || '—'}</span>
                        {customer.areaName && (
                          <>
                            <span>•</span>
                            <span className="badge badge-neutral" style={{ fontSize: '10px', padding: '1px 6px' }}>{customer.areaName}</span>
                          </>
                        )}
                      </div>
                    </div>

                    {/* Middle: 3 Metric Blocks */}
                    <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap', flex: '2 1 auto' }}>
                      {/* Block 1: Total Udhar */}
                      <div style={{ 
                        padding: 'var(--space-2) var(--space-4)', 
                        background: 'var(--color-bg-secondary)', 
                        border: '1px solid var(--color-border)', 
                        minWidth: '120px',
                        textAlign: 'center',
                        flex: '1 1 0'
                      }}>
                        <div className="text-xs text-muted" style={{ marginBottom: '2px' }}>Total Udhar (Taken)</div>
                        <div className="font-bold text-sm">₹{totalUdhar.toLocaleString('en-IN')}</div>
                      </div>

                      {/* Block 2: Total Paid */}
                      <div style={{ 
                        padding: 'var(--space-2) var(--space-4)', 
                        background: 'var(--color-success-soft)', 
                        border: '1px solid rgba(16, 185, 129, 0.2)', 
                        minWidth: '120px',
                        textAlign: 'center',
                        flex: '1 1 0'
                      }}>
                        <div className="text-xs text-success" style={{ marginBottom: '2px' }}>Total Paid (Bhugtan)</div>
                        <div className="font-bold text-sm text-success">₹{totalPaid.toLocaleString('en-IN')}</div>
                      </div>

                      {/* Block 3: Outstanding */}
                      <div style={{ 
                        padding: 'var(--space-2) var(--space-4)', 
                        background: outstanding > 0 ? 'var(--color-danger-soft)' : 'var(--color-success-soft)', 
                        border: outstanding > 0 ? '1px solid rgba(239, 68, 68, 0.2)' : '1px solid rgba(16, 185, 129, 0.2)', 
                        minWidth: '120px',
                        textAlign: 'center',
                        flex: '1 1 0'
                      }}>
                        <div className={`text-xs ${outstanding > 0 ? 'text-danger' : 'text-success'}`} style={{ marginBottom: '2px' }}>Udhar Left (O/S)</div>
                        <div className={`font-bold text-sm ${outstanding > 0 ? 'text-danger' : 'text-success'}`}>₹{outstanding.toLocaleString('en-IN')}</div>
                      </div>
                    </div>

                    {/* Right: Actions */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }} onClick={(e) => e.stopPropagation()}>
                      <button 
                        className="btn btn-ghost btn-icon btn-sm" 
                        onClick={(e) => {
                          e.stopPropagation();
                          handleWhatsAppShare(customer, selectedMonths[customer.id] || 'ALL');
                        }} 
                        title="Share Balance on WhatsApp"
                        style={{ color: '#25D366' }}
                      >
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                          <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946C.06 5.348 5.397.01 12.008.01c3.202.001 6.212 1.246 8.477 3.514 2.266 2.268 3.507 5.28 3.505 8.484-.004 6.657-5.34 11.997-11.953 11.997-2.005-.001-3.973-.502-5.724-1.457L0 24zm6.59-4.846c1.6.95 3.188 1.449 4.825 1.451 5.436 0 9.86-4.42 9.864-9.858.002-2.634-1.013-5.11-2.861-6.961C16.63 1.936 14.156.92 11.53.921c-5.445 0-9.871 4.42-9.875 9.86-.001 1.716.452 3.39 1.312 4.869l-1.02 3.733 3.825-.996zM18.067 14.7c-.33-.165-1.956-.967-2.285-1.086-.329-.12-.57-.179-.81.18-.24.359-.93 1.168-1.138 1.407-.21.239-.419.27-.75.105-.329-.165-1.39-.512-2.648-1.633-.978-.872-1.637-1.95-1.83-2.28-.192-.33-.02-.509.145-.673.149-.148.33-.389.495-.584.165-.195.22-.329.33-.548.11-.219.055-.41-.027-.575-.083-.165-.81-1.952-1.11-2.674-.29-.701-.586-.607-.81-.617-.21-.01-.45-.011-.69-.011-.24 0-.63.09-.96.449-.33.359-1.258 1.229-1.258 2.996 0 1.767 1.287 3.473 1.467 3.712.18.24 2.534 3.869 6.138 5.426.857.371 1.526.593 2.05.759.86.273 1.643.235 2.261.143.689-.103 1.956-.8 2.235-1.573.279-.773.279-1.436.195-1.573-.083-.137-.31-.219-.64-.384z"/>
                        </svg>
                      </button>
                      {aiEnabled && (
                        <button 
                          className="btn btn-ghost btn-icon btn-sm" 
                          onClick={(e) => {
                            e.stopPropagation();
                            triggerAiReminder(customer);
                          }} 
                          title="Generate AI Payment Reminder"
                          style={{ color: '#c084fc' }}
                        >
                          <Sparkles size={14} />
                        </button>
                      )}
                      <button 
                        className="btn btn-secondary btn-sm" 
                        onClick={() => {
                          setForm({ ...emptyForm, customerId: customer.id })
                          setShowModal(true)
                        }}
                      >
                        <Plus size={14} /> Record Payment
                      </button>
                      <button 
                        className="btn btn-ghost btn-icon btn-sm"
                        onClick={() => toggleExpandCustomer(customer.id)}
                        style={{ transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.2s' }}
                      >
                        <ChevronDown size={18} />
                      </button>
                    </div>
                  </div>

                  {/* Expanded Ledger Panel */}
                  <AnimatePresence>
                    {isExpanded && (
                      <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.2 }}
                        style={{ borderTop: '1px solid var(--color-border)', background: 'var(--color-bg-secondary)' }}
                      >
                        <div style={{ padding: 'var(--space-4) var(--space-6)' }}>
                          {customerPaymentsLoading[customer.id] ? (
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-6) 0', gap: 'var(--space-2)' }}>
                              <div className="spinner" style={{ width: '20px', height: '20px', borderTopColor: 'var(--color-accent)' }}></div>
                              <span className="text-muted text-xs">Loading customer transactions...</span>
                            </div>
                          ) : (
                            <>
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
                                <div className="flex gap-2">
                                  <button 
                                    type="button"
                                    className={`btn btn-sm ${customerPanelTab[customer.id] === 'ledger' ? 'btn-primary' : 'btn-secondary'}`}
                                    onClick={() => setCustomerPanelTab(prev => ({ ...prev, [customer.id]: 'ledger' }))}
                                  >
                                    Ledger Statement
                                  </button>
                                  <button 
                                    type="button"
                                    className={`btn btn-sm ${(!customerPanelTab[customer.id] || customerPanelTab[customer.id] === 'udhar') ? 'btn-primary' : 'btn-secondary'}`}
                                    onClick={() => setCustomerPanelTab(prev => ({ ...prev, [customer.id]: 'udhar' }))}
                                  >
                                    Bill-wise Udhar History
                                  </button>
                                </div>
                                <h4 className="font-semibold text-sm text-slate-700 dark:text-slate-300">
                                  {(customerPanelTab[customer.id] === 'ledger') ? 'Customer Account Ledger Statement' : 'Customer Bill-wise Udhar Records'}
                                </h4>
                              </div>
                              
                              {(customerPanelTab[customer.id] === 'ledger') ? (
                                renderCustomerLedgerTable(customer)
                              ) : (
                                renderCustomerUdharTable(customer)
                              )}
                            </>
                          )}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              )
            })
          )}
        </div>
      </div>
    )
  }

  const handleSave = async (e) => {
    e.preventDefault()
    
    const enteredAmount = Number(form.amount || 0)
    const enteredWaive = form.waivedAmount ? Number(form.waivedAmount) : 0

    if (enteredAmount <= 0 && enteredWaive <= 0) {
      toast.error('Please enter Amount or Waive-off Amount.')
      return
    }

    if (enteredWaive > 200) {
      toast.error('Waive-off amount cannot exceed ₹200.')
      return
    }

    setSaving(true)
    try {
      const payload = {
        ...form,
        amount: enteredAmount,
        waivedAmount: enteredWaive > 0 ? enteredWaive : null,
        billId: form.billId || null
      }

      // Only check overpayment when a specific bill is selected
      if (payload.billId) {
        const previewRes = await api.post('/payments/preview', {
          customerId: payload.customerId,
          billId: payload.billId,
          amount: payload.amount
        })
        const preview = previewRes.data?.data
        if (preview) {
          // Overpayment detected — store the form and show the modal
          setPendingPaymentForm(payload)
          setOverpaymentPreview(preview)
          setSaving(false)
          return
        }
      }

      // Normal payment — save directly
      await api.post('/payments', payload)
      toast.success('Payment recorded!')
      setShowModal(false)
      setForm({ ...emptyForm })
      if (payload.customerId) {
        try {
          const res = await api.get(`/payments/customer/${payload.customerId}`)
          setCustomerPayments(prev => ({ ...prev, [payload.customerId]: res.data || [] }))
        } catch {}
      }
      loadAll()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed to record payment') }
    finally { setSaving(false) }
  }

  const handleOverpaymentConfirm = async (adjustmentType, targetBillId) => {
    if (!pendingPaymentForm) return
    setSaving(true)
    try {
      const payload = {
        ...pendingPaymentForm,
        adjustmentType,
        targetBillId: targetBillId || null,
        confirmedByUser: true
      }
      const res = await api.post('/payments', payload)
      const savedPayment = res.data?.data || res.data

      // Build success summary toast
      const appliedAmt = savedPayment?.appliedAmount || pendingPaymentForm.amount
      const excessAmt = savedPayment?.excessAmount || 0
      const note = savedPayment?.adjustmentNote || ''
      const billNo = overpaymentPreview?.sourceBillNumber || ''
      toast.success(
        `✅ Payment of ₹${Number(pendingPaymentForm.amount).toLocaleString('en-IN')} recorded. ` +
        `₹${Number(appliedAmt).toLocaleString('en-IN')} applied to ${billNo}. ` +
        (excessAmt > 0 ? `Excess ₹${Number(excessAmt).toLocaleString('en-IN')} adjusted to other bills.` : '')
      )
      setOverpaymentPreview(null)
      setPendingPaymentForm(null)
      setShowModal(false)
      setForm({ ...emptyForm })
      if (payload.customerId) {
        try {
          const pRes = await api.get(`/payments/customer/${payload.customerId}`)
          setCustomerPayments(prev => ({ ...prev, [payload.customerId]: pRes.data || [] }))
        } catch {}
      }
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to record payment')
    } finally { setSaving(false) }
  }

  const handleOverpaymentCancel = () => {
    setOverpaymentPreview(null)
    setPendingPaymentForm(null)
  }

  const deletePayment = async (id) => {
    if (!window.confirm('Are you sure you want to permanently delete this payment? Customer balance and linked bill will be reversed!')) return
    try {
      const paymentToDelete = payments.find(p => p.id === id) || todayPayments.find(p => p.id === id)
      await api.delete(`/payments/${id}`)
      toast.success('Payment deleted and balance reversed successfully')
      if (paymentToDelete?.customerId) {
        const res = await api.get(`/payments/customer/${paymentToDelete.customerId}`)
        setCustomerPayments(prev => ({ ...prev, [paymentToDelete.customerId]: res.data || [] }))
      }
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Delete failed')
    }
  }

  const openEditPayment = (p) => {
    setEditingPayment(p)
    setEditNotes(p.notes || '')
    setEditPaymentMode(p.paymentMode || 'CASH')
  }

  const handleUpdatePaymentSubmit = async (e) => {
    e.preventDefault()
    setUpdating(true)
    try {
      await api.put(`/payments/${editingPayment.id}`, {
        paymentMode: editPaymentMode,
        notes: editNotes
      })
      toast.success('Payment details updated successfully')
      if (editingPayment.customerId) {
        const res = await api.get(`/payments/customer/${editingPayment.customerId}`)
        setCustomerPayments(prev => ({ ...prev, [editingPayment.customerId]: res.data || [] }))
      }
      setEditingPayment(null)
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Update failed')
    } finally { setUpdating(false) }
  }

  const todayTotal = todayPayments.reduce((sum, p) => sum + Number(p.amount || 0), 0)
  const totalCollections = payments.reduce((sum, p) => sum + Number(p.amount || 0), 0)
  const totalUdhar = customers.reduce((sum, c) => sum + Number(c.totalPending || 0), 0)

  // Generate virtual opening bills for customers who have opening balances
  const virtualOpeningBills = customers.map(customer => {
    const openingBalance = Number(customer.openingBalance || 0);
    if (openingBalance <= 0) return null;

    const custBills = pendingBills.filter(b => b.customerId === customer.id);
    const pendingBillsTotal = custBills.reduce((sum, b) => sum + Number(b.pendingAmount || 0), 0);
    const totalPending = Number(customer.totalPending || 0);
    const unpaidOpeningBalance = Math.max(0, totalPending - pendingBillsTotal);

    const pendingAmt = Math.min(openingBalance, unpaidOpeningBalance);
    const paidAmt = openingBalance - pendingAmt;
    const isPaid = (pendingAmt <= 0);

    return {
      id: `opening-${customer.id}`,
      billNumber: 'Opening Balance',
      customerName: customer.name,
      customerPhone: customer.phone,
      grandTotal: openingBalance,
      paidAmount: paidAmt,
      pendingAmount: pendingAmt,
      paymentMode: 'CREDIT',
      status: isPaid ? 'PAID' : 'CONFIRMED',
      createdAt: customer.createdAt || null
    };
  }).filter(Boolean);

  // Filter active virtual opening bills (unpaid only) for Outstanding tab
  const activeVirtualOpeningBills = virtualOpeningBills.filter(b => b.pendingAmount > 0);

  const allUdharEntries = [...activeVirtualOpeningBills, ...pendingBills].map(b => {
    if (b.id.startsWith?.('opening-')) {
      return {
        ...b,
        initialUdhar: b.grandTotal,
        paidAfterwards: b.paidAmount,
      };
    }
    const billPayments = payments.filter(p => p.billId === b.id);
    const sumPaidAfterwards = billPayments.reduce((sum, p) => sum + Number(p.appliedAmount || p.amount || 0), 0);
    const downPayment = Math.max(0, Number(b.paidAmount || 0) - sumPaidAfterwards);
    const initialUdhar = Number(b.grandTotal || 0) - downPayment;
    return {
      ...b,
      initialUdhar,
      paidAfterwards: sumPaidAfterwards,
    };
  }).sort((a, b) => {
    const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
    const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
    return dateB - dateA;
  });

  const displayList = activeTab === 'today'
    ? todayPayments
    : activeTab === 'all'
      ? payments
      : allUdharEntries;

  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const paymentColumns = [
    { header: 'Customer', accessor: 'customerName', render: (row) => (
      <span className="font-medium">
        {row.customerShopName || row.shopName || row.customerName || '—'}
      </span>
    ) },
    { header: 'Amount Paid', accessor: 'amount', render: (row) => <span className="font-semibold text-success">₹{Number(row.amount || 0).toLocaleString('en-IN')}</span> },
    { header: 'Mode', accessor: 'paymentMode', render: (row) => {
      const colors = { CASH: 'badge-success', UPI: 'badge-info' }
      return <span className={`badge ${colors[row.paymentMode] || 'badge-neutral'}`}>{row.paymentMode}</span>
    }},
    { header: 'Bill #', accessor: 'billNumber', render: (row) => (
      <div>
        {row.billNumber ? (
          <div>
            <span className="font-medium">{row.billNumber}</span>
            {row.billGrandTotal !== undefined && row.billGrandTotal !== null && (
              <div className="text-xs text-muted" style={{ marginTop: 2 }}>
                Total: ₹{Number(row.billGrandTotal).toLocaleString('en-IN')} | Pending: ₹{Number(row.billPendingAmount).toLocaleString('en-IN')}
              </div>
            )}
          </div>
        ) : (
          <span className="text-muted">General</span>
        )}
      </div>
    )},
    { header: 'Notes', accessor: 'notes', render: (row) => row.notes || <span className="text-muted">—</span> },
    ...((isAdmin || isManager) ? [{
      header: 'Collected By',
      accessor: 'collectedBy',
      render: (row) => <span className="font-medium text-secondary" style={{ color: 'var(--color-text-secondary)' }}>{row.collectedBy || 'System'}</span>
    }] : []),
    { header: 'Date & Time', accessor: 'paidAt', render: (row) => row.paidAt ? new Date(row.paidAt).toLocaleString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true }) : '—' },
  ].filter(col => {
    if (isMobile) {
      return !['notes', 'collectedBy', 'paidAt'].includes(col.accessor)
    }
    return true
  })

  const udharColumns = [
    { header: 'Customer', accessor: 'customerName', render: (row) => (
      <span className="font-medium">
        {row.customerShopName || row.shopName || row.customerName || '—'}
      </span>
    ) },
    { header: 'Bill #', accessor: 'billNumber', render: (row) => <span className="font-medium">{row.billNumber || '—'}</span> },
    { header: 'Original Amount', accessor: 'grandTotal', render: (row) => <span className="font-semibold text-slate-500 dark:text-slate-400">₹{Number(row.grandTotal || 0).toLocaleString('en-IN')}</span> },
    { header: 'Udhar Taken', accessor: 'initialUdhar', render: (row) => <span className="font-bold text-slate-800 dark:text-slate-200">₹{Number(row.initialUdhar || 0).toLocaleString('en-IN')}</span> },
    { header: 'Total Paid', accessor: 'paidAfterwards', render: (row) => <span className="font-semibold text-success">₹{Number(row.paidAfterwards || 0).toLocaleString('en-IN')}</span> },
    { header: 'Outstanding Balance', accessor: 'pendingAmount', render: (row) => <span className="font-bold text-danger">₹{Number(row.pendingAmount || 0).toLocaleString('en-IN')}</span> },
    { header: 'Status', accessor: 'status', render: (row) => {
      const colors = { CONFIRMED: 'badge-neutral', PARTIAL: 'badge-warning', PAID: 'badge-success' }
      const labels = { CONFIRMED: 'UNPAID', PARTIAL: 'PARTIAL', PAID: 'PAID' }
      return <span className={`badge ${colors[row.status] || 'badge-neutral'}`}>{labels[row.status] || row.status}</span>
    }},
    { header: 'Date & Time', accessor: 'createdAt', render: (row) => row.createdAt ? new Date(row.createdAt).toLocaleString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true }) : '—' },
  ].filter(col => {
    if (isMobile) {
      return !['grandTotal', 'initialUdhar', 'paidAfterwards', 'createdAt'].includes(col.accessor)
    }
    return true
  })

  const columns = activeTab === 'udhar' ? udharColumns : paymentColumns

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Khata / Payments</h2>
          <p className="page-subtitle">Track payment collections</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={() => setShowModal(true)} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Record Payment
          </motion.button>
        </div>
      </div>

      {/* KPI */}
      <div className="grid-4" style={{ marginBottom: 'var(--space-6)' }}>
        <StatCard icon={<IndianRupee size={24} />} label="Today's Collection" value={todayTotal} prefix="₹" color="var(--color-success)" delay={0} />
        <StatCard icon={<IndianRupee size={24} />} label="Total Collections" value={totalCollections} prefix="₹" color="var(--color-info)" delay={1} />
        <StatCard icon={<IndianRupee size={24} />} label="Total Outstanding Udhar" value={totalUdhar} prefix="₹" color="var(--color-danger)" delay={2} />
        <StatCard icon={<IndianRupee size={24} />} label="Total Entries" value={payments.length} color="var(--color-accent)" delay={3} />
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'ledgers' ? 'active' : ''}`} onClick={() => setActiveTab('ledgers')}>Customer Ledgers</button>
        <button className={`tab ${activeTab === 'today' ? 'active' : ''}`} onClick={() => setActiveTab('today')}>Today ({todayPayments.length})</button>
        <button className={`tab ${activeTab === 'all' ? 'active' : ''}`} onClick={() => setActiveTab('all')}>All Payments ({payments.length})</button>
        <button className={`tab ${activeTab === 'udhar' ? 'active' : ''}`} onClick={() => setActiveTab('udhar')}>Outstanding Udhar ({allUdharEntries.length})</button>
      </div>

      {activeTab === 'ledgers' ? (
        renderCustomerLedgersSection()
      ) : (
        <DataTable
          columns={columns}
          data={displayList}
          loading={loading}
          searchPlaceholder={activeTab === 'udhar' ? "Search outstanding bills..." : "Search payments..."}
          emptyMessage={activeTab === 'udhar' ? "No outstanding bills found" : "No payments found"}
          actions={activeTab === 'udhar' ? null : (row) => (
            <>
              {(isAdmin || isManager) && (
                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEditPayment(row)} title="Edit Payment"><Edit2 size={15} /></button>
              )}
              {isAdmin && (
                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => deletePayment(row.id)} title="Delete Payment & Reverse Balance" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
              )}
            </>
          )}
        />
      )}

      {/* Record Payment Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Record Payment">
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Customer *</label>
            <SearchSelect
              options={customers.filter(c => c.active !== false)}
              value={form.customerId}
              onChange={val => {
                updateField('customerId', val)
                updateField('billId', '')
              }}
              labelKey="name" valueKey="id"
              placeholder="Select customer..."
              renderOption={(c) => (
                <div className="flex justify-between">
                  <span>{c.name} {c.shopName && <span className="text-muted text-xs">— {c.shopName}</span>}</span>
                  {Number(c.totalPending || 0) > 0 && <span className="text-danger text-xs">₹{Number(c.totalPending).toLocaleString('en-IN')}</span>}
                </div>
              )}
            />
          </div>
          {form.customerId && (
            <div className="form-group">
              <label className="form-label">Link to Outstanding Bill (Optional)</label>
              <select
                className="form-select"
                value={form.billId}
                onChange={e => updateField('billId', e.target.value)}
              >
                <option value="">General Payment / Auto FIFO Allocation</option>
                {(() => {
                  const activeCustomer = customers.find(c => c.id === form.customerId)
                  const customerPendingBills = activeCustomer ? pendingBills.filter(b => b.customerId === activeCustomer.id) : []
                  return customerPendingBills.map(b => (
                    <option key={b.id} value={b.id}>
                      {b.billNumber} ({b.createdAt ? new Date(b.createdAt).toLocaleDateString('en-IN') : '—'}) — Pending: ₹{Number(b.pendingAmount || 0).toLocaleString('en-IN')}
                    </option>
                  ))
                })()}
              </select>
            </div>
          )}
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Amount ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.amount} onChange={e => updateField('amount', e.target.value)} placeholder="0.00" />
            </div>
            <div className="form-group">
              <label className="form-label">Payment Mode *</label>
              <select className="form-select" value={form.paymentMode} onChange={e => updateField('paymentMode', e.target.value)}>
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
              </select>
            </div>
          </div>
          {(isAdmin || isManager) && (
            <div className="form-group">
              <label className="form-label">Waive-off Amount ₹ (Optional, Max ₹200)</label>
              <input 
                className="form-input" 
                type="number" 
                min="0" 
                max="200" 
                step="0.01" 
                value={form.waivedAmount || ''} 
                onChange={e => updateField('waivedAmount', e.target.value)} 
                placeholder="0.00" 
              />
            </div>
          )}
          <div className="form-group">
            <label className="form-label">Notes</label>
            <textarea className="form-textarea" value={form.notes} onChange={e => updateField('notes', e.target.value)} placeholder="Optional notes..." rows={2} />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-success" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Recording...' : 'Record Payment'}
            </motion.button>
          </div>
        </form>
      </Modal>

      {/* Overpayment Resolution Modal */}
      <OverpaymentModal
        isOpen={!!overpaymentPreview}
        preview={overpaymentPreview}
        onConfirm={handleOverpaymentConfirm}
        onCancel={handleOverpaymentCancel}
      />

      {/* Edit Payment Modal */}
      <Modal isOpen={!!editingPayment} onClose={() => setEditingPayment(null)} title="Edit Payment Details">
        {editingPayment && (
          <form onSubmit={handleUpdatePaymentSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Customer</label>
              <input className="form-input" value={editingPayment.customerName} disabled style={{ opacity: 0.7 }} />
            </div>
            <div className="form-group">
              <label className="form-label">Amount Paid</label>
              <input className="form-input" value={`₹${Number(editingPayment.amount || 0).toLocaleString('en-IN')}`} disabled style={{ opacity: 0.7 }} />
            </div>
             <div className="form-group">
              <label className="form-label">Payment Mode</label>
              <select 
                className="form-select" 
                value={editPaymentMode} 
                onChange={e => setEditPaymentMode(e.target.value)}
                disabled={editingPayment?.paymentMode === 'WAIVE_OFF'}
              >
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
                {editingPayment?.paymentMode === 'WAIVE_OFF' && (
                  <option value="WAIVE_OFF">Waive-off / Round-off</option>
                )}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Notes</label>
              <textarea className="form-textarea" value={editNotes} onChange={e => setEditNotes(e.target.value)} placeholder="Enter details..." rows={3} />
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingPayment(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={updating}>
                {updating ? 'Updating...' : 'Update Details'}
              </button>
            </div>
          </form>
        )}
      </Modal>

      {/* AI WhatsApp Reminder Modal */}
      <Modal isOpen={showAiReminderModal} onClose={() => setShowAiReminderModal(false)} title="AI WhatsApp Reminder">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {aiReminderLoading ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-8) 0', gap: 'var(--space-2)' }}>
              <div className="spinner" style={{ width: '30px', height: '30px', borderTopColor: 'var(--color-accent)' }}></div>
              <span className="text-muted text-xs">Generating reminder draft using Gemini AI...</span>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="form-group">
                <label className="form-label">Recipient Shop Name</label>
                <input className="form-input" value={aiReminderCustomer ? `${aiReminderCustomer.name} (${aiReminderCustomer.shopName || 'No Shop Name'})` : ''} disabled style={{ opacity: 0.7 }} />
              </div>
              <div className="form-group">
                <label className="form-label">Recipient Phone</label>
                <input className="form-input" value={aiReminderCustomer ? aiReminderCustomer.phone || '—' : ''} disabled style={{ opacity: 0.7 }} />
              </div>
              <div className="form-group">
                <label className="form-label" style={{ marginBottom: '6px' }}>Reminder Language Template</label>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="button"
                    className={`btn btn-sm ${aiReminderLanguage === 'HINGLISH' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => handleLanguageChange('HINGLISH')}
                    disabled={aiReminderLoading}
                    style={{ flex: 1, height: '32px', fontSize: '12px' }}
                  >
                    ✨ Hinglish Template
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${aiReminderLanguage === 'ENGLISH' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => handleLanguageChange('ENGLISH')}
                    disabled={aiReminderLoading}
                    style={{ flex: 1, height: '32px', fontSize: '12px' }}
                  >
                    🇬🇧 English Template
                  </button>
                </div>
              </div>
              <div className="form-group">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-1)' }}>
                  <label className="form-label">Generated Message Draft (Edit if needed)</label>
                  <span className="badge badge-accent" style={{ fontSize: '9px', textTransform: 'uppercase' }}>Gemini 2.5 Flash</span>
                </div>
                <textarea
                  className="form-textarea"
                  value={aiReminderText}
                  onChange={(e) => setAiReminderText(e.target.value)}
                  placeholder="Enter reminder message..."
                  rows={6}
                  style={{ fontSize: 'var(--font-size-sm)' }}
                />
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowAiReminderModal(false)}>Cancel</button>
                <button 
                  type="button" 
                  className="btn btn-primary" 
                  onClick={sendAiReminder}
                  disabled={sendingAiReminder || !aiReminderText.trim()}
                  style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}
                >
                  {sendingAiReminder ? (
                    'Sending...'
                  ) : (
                    <>
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                        <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946C.06 5.348 5.397.01 12.008.01c3.202.001 6.212 1.246 8.477 3.514 2.266 2.268 3.507 5.28 3.505 8.484-.004 6.657-5.34 11.997-11.953 11.997-2.005-.001-3.973-.502-5.724-1.457L0 24zm6.59-4.846c1.6.95 3.188 1.449 4.825 1.451 5.436 0 9.86-4.42 9.864-9.858.002-2.634-1.013-5.11-2.861-6.961C16.63 1.936 14.156.92 11.53.921c-5.445 0-9.871 4.42-9.875 9.86-.001 1.716.452 3.39 1.312 4.869l-1.02 3.733 3.825-.996zM18.067 14.7c-.33-.165-1.956-.967-2.285-1.086-.329-.12-.57-.179-.81.18-.24.359-.93 1.168-1.138 1.407-.21.239-.419.27-.75.105-.329-.165-1.39-.512-2.648-1.633-.978-.872-1.637-1.95-1.83-2.28-.192-.33-.02-.509.145-.673.149-.148.33-.389.495-.584.165-.195.22-.329.33-.548.11-.219.055-.41-.027-.575-.083-.165-.81-1.952-1.11-2.674-.29-.701-.586-.607-.81-.617-.21-.01-.45-.011-.69-.011-.24 0-.63.09-.96.449-.33.359-1.258 1.229-1.258 2.996 0 1.767 1.287 3.473 1.467 3.712.18.24 2.534 3.869 6.138 5.426.857.371 1.526.593 2.05.759.86.273 1.643.235 2.261.143.689-.103 1.956-.8 2.235-1.573.279-.773.279-1.436.195-1.573-.083-.137-.31-.219-.64-.384z"/>
                      </svg>
                      Send WhatsApp
                    </>
                  )}
                </button>
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}
