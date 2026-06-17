import React, { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  TrendingUp, RefreshCw, AlertCircle, Info, Send, PhoneCall,
  MessageSquare, User, Landmark, HelpCircle, CheckCircle2,
  Loader2, ClipboardList, ShieldAlert, PhoneForwarded, X, Search, Filter
} from 'lucide-react'
import api from '../services/api'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'

export default function UdharCollection() {
  const toast = useToast()
  
  // View states
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [sortBy, setSortBy] = useState('daysOverdue')
  const [searchTerm, setSearchTerm] = useState('')
  const [whatsappConnected, setWhatsappConnected] = useState(false)
  const [scanningNpa, setScanningNpa] = useState(false)
  
  // Selection
  const [selectedIds, setSelectedIds] = useState([])
  
  // Modal state
  const [activeFollowUpCustomer, setActiveFollowUpCustomer] = useState(null) // customer object
  const [customNotes, setCustomNotes] = useState('')
  const [reminderPreview, setReminderPreview] = useState('')
  const [submittingAction, setSubmittingAction] = useState(false)
  const [showBulkConfirm, setShowBulkConfirm] = useState(false)
  const [showCooldownConfirm, setShowCooldownConfirm] = useState(false)
  const [cooldownConfirmMsg, setCooldownConfirmMsg] = useState('')
  const [cooldownCustomer, setCooldownCustomer] = useState(null)
  const [cooldownChannel, setCooldownChannel] = useState('WHATSAPP')

  // Bulk Progress Polling
  const [progress, setProgress] = useState({
    isSending: false,
    total: 0,
    processed: 0,
    success: 0,
    failed: 0,
    currentCustomer: ''
  })
  
  const progressPollRef = useRef(null)

  // Fetch pending list
  const fetchPendingData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.get(`/receivables/pending?sortBy=${sortBy}`)
      if (res.data && res.data.success) {
        setData(res.data.data)
      } else {
        setError(res.data?.message || 'Failed to load receivables.')
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to fetch receivables data.')
    } finally {
      setLoading(false)
    }
  }, [sortBy])

  const handleScanNpa = async () => {
    setScanningNpa(true)
    try {
      const res = await api.post('/customers/scan-npa')
      if (res.data && res.data.success) {
        toast.success('NPA Defaulters scan completed! 🔍')
        fetchPendingData()
      } else {
        toast.error(res.data?.message || 'Failed to complete NPA scan.')
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to trigger NPA scan.')
    } finally {
      setScanningNpa(false)
    }
  }

  // Check WhatsApp service connection status
  const checkWhatsAppStatus = async () => {
    try {
      const res = await api.get('/customers/whatsapp/status')
      if (res.data && res.data.success) {
        setWhatsappConnected(res.data.data.status === 'CONNECTED')
      }
    } catch (err) {
      console.warn('Failed to fetch WhatsApp connection status:', err)
      setWhatsappConnected(false)
    }
  }

  // Poll bulk progress if running
  const checkBulkProgress = async () => {
    try {
      const res = await api.get('/customers/whatsapp/bulk-progress')
      if (res.data && res.data.success) {
        const prog = res.data.data
        setProgress({
          isSending: prog.isSending || false,
          total: prog.total || 0,
          processed: prog.processed || 0,
          success: prog.success || 0,
          failed: prog.failed || 0,
          currentCustomer: prog.currentCustomer || ''
        })
        if (!prog.isSending && progressPollRef.current) {
          clearInterval(progressPollRef.current)
          progressPollRef.current = null
          toast.success('Bulk WhatsApp reminder queue finished! ✅')
          fetchPendingData()
        }
      }
    } catch (err) {
      console.error('Failed to poll bulk progress:', err)
    }
  }

  useEffect(() => {
    fetchPendingData()
    checkWhatsAppStatus()
  }, [sortBy, fetchPendingData])

  // Poll bulk sending status on mount & interval
  useEffect(() => {
    checkBulkProgress()
    const interval = setInterval(() => {
      if (progress.isSending) {
        checkBulkProgress()
      } else {
        checkWhatsAppStatus()
      }
    }, 4000)
    return () => clearInterval(interval)
  }, [progress.isSending])

  // Selection handlers
  const handleToggleSelectAll = () => {
    if (selectedIds.length === filteredData.length) {
      setSelectedIds([])
    } else {
      setSelectedIds(filteredData.map(c => c.customerId))
    }
  }

  const handleToggleSelect = (customerId) => {
    setSelectedIds(prev =>
      prev.includes(customerId)
        ? prev.filter(id => id !== customerId)
        : [...prev, customerId]
    )
  }

  // Open Follow-up modal
  const openFollowUpModal = async (customer) => {
    setActiveFollowUpCustomer(customer)
    setCustomNotes('')
    setReminderPreview('Generating reminder details...')
    
    try {
      const res = await api.get(`/customers/${customer.customerId}/reminder`)
      if (res.data && res.data.success) {
        setReminderPreview(res.data.data.message)
      } else {
        setReminderPreview('Failed to retrieve automated prompt template.')
      }
    } catch (err) {
      setReminderPreview(
        `${customer.customerName} Ji,\n\nLari Traders ki taraf se outstanding balance ₹${customer.pendingAmount.toLocaleString('en-IN')} pending hai. Kripya iska bhugtan karein.`
      )
    }
  }

  // Execute reminder action
  const executeReminder = async (channel, ignoreCooldown = false) => {
    if (!activeFollowUpCustomer && !cooldownCustomer) return
    const targetCustomer = activeFollowUpCustomer || cooldownCustomer
    setSubmittingAction(true)
    try {
      const res = await api.post(`/receivables/${targetCustomer.customerId}/send-reminder?channel=${channel}&ignoreCooldown=${ignoreCooldown}`, {
        notes: customNotes
      })

      if (res.data && res.data.success) {
        const responseData = res.data.data
        if (responseData && responseData.success === false && responseData.error === 'COOLDOWN') {
          setCooldownCustomer(targetCustomer)
          setCooldownChannel(channel)
          setCooldownConfirmMsg(responseData.message)
          setShowCooldownConfirm(true)
          setActiveFollowUpCustomer(null)
          return
        }

        if (responseData.sent) {
          toast.success(`Automated WhatsApp sent to ${targetCustomer.customerName}! ✅`)
        } else if (channel === 'WHATSAPP' && responseData.whatsappLink) {
          toast.warning('WhatsApp service offline. Fallback to manual link.')
          window.open(responseData.whatsappLink, '_blank')
        } else if (channel === 'MANUAL' && responseData.whatsappLink) {
          window.open(responseData.whatsappLink, '_blank')
          toast.success('Manual link loaded & log entry saved. 📲')
        } else {
          toast.success('Follow-up call note recorded successfully! 📝')
        }
        setShowCooldownConfirm(false)
        setCooldownCustomer(null)
        setActiveFollowUpCustomer(null)
        fetchPendingData()
      } else {
        toast.error(res.data?.message || 'Failed to submit follow-up log.')
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to send collection reminder.')
    } finally {
      setSubmittingAction(false)
    }
  }

  const handleBypassCooldown = () => {
    executeReminder(cooldownChannel, true)
  }

  // Send bulk reminders
  const handleBulkSend = async () => {
    setShowBulkConfirm(false)
    try {
      await api.post('/customers/whatsapp/send-bulk', selectedIds)
      toast.success('Background bulk sending queue started! 🚀')
      setProgress(prev => ({ ...prev, isSending: true }))
      setSelectedIds([])
      // Start polling progress immediately
      progressPollRef.current = setInterval(checkBulkProgress, 2000)
    } catch (err) {
      toast.error('Failed to dispatch background bulk reminders queue.')
    }
  }

  // Aging bracket colors helper
  const getOverdueBadge = (days) => {
    if (days >= 90) return 'bg-rose-100 dark:bg-rose-950/40 text-rose-800 dark:text-rose-400 border border-rose-250 dark:border-rose-900 font-bold'
    if (days >= 61) return 'bg-orange-100 dark:bg-orange-950/40 text-orange-800 dark:text-orange-400 border border-orange-250 dark:border-orange-900 font-semibold'
    if (days >= 31) return 'bg-amber-100 dark:bg-amber-950/40 text-amber-800 dark:text-amber-400 border border-amber-250 dark:border-amber-900'
    return 'bg-emerald-50 dark:bg-emerald-950/10 text-emerald-800 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900'
  }

  // Filter list by search term
  const filteredData = data.filter(c =>
    (c.customerName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
    (c.shopName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
    (c.phoneNumber || '').includes(searchTerm)
  )

  // Calculations for summary boxes
  const totalOutstanding = data.reduce((sum, item) => sum + (item.pendingAmount || 0), 0)
  const highlyOverdueCount = data.filter(c => c.daysOverdue >= 30).length
  const pendingFollowUpCount = data.filter(c => c.needsFollowUp).length

  return (
    <div className="p-6 max-w-[1600px] mx-auto flex flex-col gap-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
            <Landmark className="text-blue-500" /> Receivables & Collections (Udhar)
          </h2>
          <p className="text-slate-500 text-xs mt-1">
            Prioritize customer reminders and track pending balances directly linked with your Khata books.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleScanNpa}
            disabled={scanningNpa || loading}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-rose-50 hover:bg-rose-100 dark:bg-rose-950/20 text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-800 rounded text-xs font-bold transition disabled:opacity-50"
          >
            <ShieldAlert size={12} className={scanningNpa ? 'animate-spin' : ''} /> Scan NPA Defaulters
          </button>
          
          <button
            onClick={fetchPendingData}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-50 hover:bg-blue-100 dark:bg-blue-950/20 text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-800 rounded text-xs font-bold transition disabled:opacity-50"
          >
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} /> Refresh Queue
          </button>
        </div>
      </div>

      {/* Connection Notification */}
      {!whatsappConnected && !progress.isSending && (
        <div className="flex items-center gap-2 p-3 bg-amber-50 dark:bg-amber-950/20 border border-amber-250 dark:border-amber-900/60 rounded-lg text-amber-800 dark:text-amber-400 text-xs">
          <ShieldAlert size={16} />
          <span>
            <strong>WhatsApp Service Disconnected:</strong> Direct automatic text delivery is offline. Reminder actions will fallback to manual browser URL links.
          </span>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400 font-semibold uppercase tracking-wider">Total Outstanding Dues</span>
            <h3 className="text-2xl font-bold text-slate-900 dark:text-slate-100 mt-1">
              ₹{totalOutstanding.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </h3>
          </div>
          <div className="w-12 h-12 bg-rose-50 dark:bg-rose-950/20 text-rose-500 rounded-full flex items-center justify-center">
            <Landmark size={24} />
          </div>
        </div>

        <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400 font-semibold uppercase tracking-wider">Defaulters (30+ Days)</span>
            <h3 className="text-2xl font-bold text-rose-600 dark:text-rose-400 mt-1">
              {highlyOverdueCount} <span className="text-xs text-muted font-normal">shops</span>
            </h3>
          </div>
          <div className="w-12 h-12 bg-amber-50 dark:bg-amber-950/20 text-amber-500 rounded-full flex items-center justify-center">
            <AlertCircle size={24} />
          </div>
        </div>

        <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400 font-semibold uppercase tracking-wider">Follow-ups Due Today</span>
            <h3 className="text-2xl font-bold text-blue-600 dark:text-blue-400 mt-1">
              {pendingFollowUpCount} <span className="text-xs text-muted font-normal">due</span>
            </h3>
          </div>
          <div className="w-12 h-12 bg-blue-50 dark:bg-blue-950/20 text-blue-500 rounded-full flex items-center justify-center">
            <PhoneForwarded size={24} />
          </div>
        </div>
      </div>

      {/* Live Bulk sending progress banner */}
      {progress.isSending && (
        <div className="p-4 bg-blue-50 dark:bg-blue-950/20 border border-blue-200 dark:border-blue-800 rounded-lg flex flex-col gap-3">
          <div className="flex items-center justify-between text-xs font-bold">
            <span className="flex items-center gap-1 text-slate-700 dark:text-slate-300">
              <Loader2 size={14} className="animate-spin text-blue-500" /> Sending Bulk Reminders: {progress.currentCustomer}
            </span>
            <span className="text-slate-800 dark:text-slate-200">
              {progress.processed} / {progress.total} Completed
            </span>
          </div>
          <div className="w-full bg-slate-200 dark:bg-slate-800 h-2.5 rounded-full overflow-hidden">
            <div
              className="bg-blue-600 h-full rounded-full transition-all duration-300"
              style={{ width: `${progress.total > 0 ? (progress.processed / progress.total) * 100 : 0}%` }}
            />
          </div>
          <div className="flex items-center justify-between flex-wrap gap-2 text-xs font-semibold">
            <div className="flex items-center gap-4">
              <span className="text-emerald-600 dark:text-emerald-400">✅ {progress.success} Success</span>
              <span className="text-rose-600 dark:text-rose-400">❌ {progress.failed} Failed</span>
            </div>
            <span className="text-[10px] text-amber-600 dark:text-amber-400 font-medium">
              ⚠️ Safety Notice: WhatsApp account safety aur spam ban prevention ke liye har message me 2.5 seconds ka anti-spam delay apply ho raha hai.
            </span>
          </div>
        </div>
      )}

      {/* Priorities and filters section */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm p-4 flex flex-wrap items-center justify-between gap-4">
        {/* Search */}
        <div className="relative w-full md:max-w-xs">
          <Search size={16} className="absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search customer, phone or shop..."
            className="w-full pl-9 pr-4 py-1.5 text-xs rounded border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 text-slate-800 dark:text-slate-200 shadow-sm focus:outline-none focus:border-blue-500"
          />
        </div>

        {/* Action controllers */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5">
            <Filter size={13} className="text-slate-400" />
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              className="px-3 py-1.5 text-xs rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-850 dark:text-slate-200 shadow-sm focus:outline-none"
            >
              <option value="daysOverdue">Sort by Overdue Days</option>
              <option value="pendingAmount">Sort by Balance Outstanding</option>
              <option value="customerName">Sort by Customer Name</option>
            </select>
          </div>

          <button
            onClick={() => setShowBulkConfirm(true)}
            disabled={selectedIds.length === 0 || progress.isSending || !whatsappConnected}
            className="flex items-center gap-1 px-4 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded font-semibold transition"
          >
            <Send size={12} /> Send Bulk WhatsApp ({selectedIds.length})
          </button>
        </div>
      </div>

      {/* Queue Table */}
      {loading && data.length === 0 ? (
        <div className="flex flex-col items-center justify-center min-h-[300px] gap-3">
          <Loader2 className="animate-spin text-blue-500" size={32} />
          <span className="text-slate-500 text-xs">Scanning ledger balances...</span>
        </div>
      ) : filteredData.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 border border-dashed border-slate-300 dark:border-slate-800 rounded-lg text-slate-400 bg-slate-50 dark:bg-slate-900/30">
          <CheckCircle2 size={48} className="mb-2 text-emerald-400" />
          <p className="text-sm font-semibold">Koi pending dues nahi mile. Saari collections clear hain! 🎉</p>
        </div>
      ) : (
        <div className="overflow-x-auto border border-slate-200 dark:border-slate-850 bg-white dark:bg-slate-900 rounded-xl shadow-sm">
          <table className="w-full border-collapse text-left text-xs">
            <thead className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 sticky top-0">
              <tr>
                <th className="p-4 w-[50px]">
                  <input
                    type="checkbox"
                    checked={selectedIds.length === filteredData.length && filteredData.length > 0}
                    onChange={handleToggleSelectAll}
                    className="cursor-pointer w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                </th>
                <th className="p-4 font-semibold text-slate-700 dark:text-slate-300">Customer & Shop</th>
                <th className="p-4 font-semibold text-slate-700 dark:text-slate-300">Phone</th>
                <th className="p-4 font-semibold text-slate-700 dark:text-slate-300">Outstanding Balance</th>
                <th className="p-4 font-semibold text-slate-700 dark:text-slate-300">Days Overdue</th>
                <th className="p-4 font-semibold text-slate-700 dark:text-slate-300">Last Follow-up</th>
                <th className="p-4 font-semibold text-slate-700 dark:text-slate-300 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {filteredData.map((row) => (
                <tr
                  key={row.customerId}
                  className={`hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors ${
                    row.needsFollowUp ? 'bg-amber-50/20 dark:bg-amber-950/5' : ''
                  }`}
                >
                  <td className="p-4">
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(row.customerId)}
                      onChange={() => handleToggleSelect(row.customerId)}
                      className="cursor-pointer w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                    />
                  </td>
                  <td className="p-4">
                    <div className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1.5">
                      <User size={13} className="text-slate-400" />
                      {row.customerName}
                      {row.needsFollowUp && (
                        <span className="px-1.5 py-0.5 text-[9px] font-semibold bg-amber-100 dark:bg-amber-950 text-amber-800 dark:text-amber-400 rounded-full border border-amber-300 dark:border-amber-900 flex items-center gap-0.5 animate-pulse">
                          Needs Follow-up
                        </span>
                      )}
                      {row.isNpa && (
                        <span className="px-1.5 py-0.5 text-[9px] font-bold bg-rose-100 dark:bg-rose-950 text-rose-800 dark:text-rose-400 rounded-full border border-rose-300 dark:border-rose-900 animate-pulse">
                          NPA Defaulter
                        </span>
                      )}
                    </div>
                    {row.shopName && <div className="text-[10px] text-slate-500 dark:text-slate-400 ml-4.5 mt-0.5">{row.shopName}</div>}
                  </td>
                  <td className="p-4 text-slate-600 dark:text-slate-400">{row.phoneNumber || '—'}</td>
                  <td className="p-4 font-bold text-slate-800 dark:text-slate-100">
                    ₹{row.pendingAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </td>
                  <td className="p-4">
                    <span className={`px-2 py-0.5 rounded text-[10px] ${getOverdueBadge(row.daysOverdue)}`}>
                      {row.daysOverdue} Days
                    </span>
                  </td>
                  <td className="p-4 text-slate-500 dark:text-slate-400">
                    {row.lastReminderSentAt
                      ? new Date(row.lastReminderSentAt).toLocaleString('en-IN', {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit'
                        })
                      : <span className="text-slate-400 italic">Never contacted</span>}
                  </td>
                  <td className="p-4 text-right">
                    <button
                      onClick={() => openFollowUpModal(row)}
                      className="inline-flex items-center gap-1 px-3 py-1 bg-slate-50 hover:bg-slate-100 dark:bg-slate-800 dark:hover:bg-slate-700/60 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 rounded font-semibold transition"
                    >
                      <PhoneCall size={12} /> Contact / Log
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Contact & Follow-up Modal */}
      <AnimatePresence>
        {activeFollowUpCustomer && (
          <div className="fixed inset-0 bg-black/60 z-[300] flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-850 rounded-xl shadow-2xl max-w-lg w-full overflow-hidden flex flex-col"
            >
              {/* Modal Header */}
              <div className="flex items-center justify-between p-4 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/60">
                <div>
                  <h3 className="font-bold text-sm text-slate-800 dark:text-slate-200 uppercase tracking-wider">
                    Follow-up Action
                  </h3>
                  <p className="text-[10px] text-muted mt-0.5">
                    Customer: {activeFollowUpCustomer.customerName} ({activeFollowUpCustomer.shopName || 'No Shop'})
                  </p>
                </div>
                <button
                  onClick={() => setActiveFollowUpCustomer(null)}
                  className="p-1 hover:bg-slate-200 dark:hover:bg-slate-800 text-slate-500 rounded-full transition"
                >
                  <X size={16} />
                </button>
              </div>

              {/* Modal Body */}
              <div className="p-6 flex flex-col gap-4 overflow-y-auto max-h-[70vh]">
                {/* Balance context */}
                <div className="p-3 bg-rose-50 dark:bg-rose-950/20 border border-rose-200 dark:border-rose-900/40 rounded-lg flex justify-between items-center text-xs">
                  <span className="font-semibold text-rose-800 dark:text-rose-400 uppercase tracking-wider">Outstanding Dues</span>
                  <span className="font-bold text-rose-700 dark:text-rose-350 text-sm">
                    ₹{activeFollowUpCustomer.pendingAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </span>
                </div>

                {/* Template Preview */}
                <div className="flex flex-col gap-1.5">
                  <span className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">WhatsApp Reminder Text (Hinglish Template)</span>
                  <div className="p-3 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg text-slate-800 dark:text-slate-200 font-mono text-[11px] whitespace-pre-wrap leading-relaxed">
                    {reminderPreview}
                  </div>
                </div>

                {/* Call Notes input */}
                <div className="flex flex-col gap-1.5">
                  <span className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">Notes / Comments (Optional)</span>
                  <textarea
                    rows={3}
                    value={customNotes}
                    onChange={(e) => setCustomNotes(e.target.value)}
                    placeholder="Log comments, e.g. shop was closed, or owner promised payment by Thursday..."
                    className="w-full p-2.5 text-xs rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-800 dark:text-slate-200 shadow-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              </div>

              {/* Modal Footer */}
              <div className="p-4 bg-slate-50 dark:bg-slate-900/60 border-t border-slate-100 dark:border-slate-800 flex flex-wrap items-center justify-end gap-2">
                <button
                  onClick={() => executeReminder('CALL')}
                  disabled={submittingAction}
                  className="px-3.5 py-1.5 text-xs font-semibold bg-white hover:bg-slate-100 dark:bg-slate-800 dark:hover:bg-slate-750 text-slate-700 dark:text-slate-300 border border-slate-350 dark:border-slate-700 rounded transition disabled:opacity-50 flex items-center gap-1"
                >
                  <ClipboardList size={13} /> Log Call Note Only
                </button>

                <button
                  onClick={() => executeReminder('MANUAL')}
                  disabled={submittingAction}
                  className="px-3.5 py-1.5 text-xs font-semibold bg-white hover:bg-slate-100 dark:bg-slate-850 text-slate-700 dark:text-slate-300 border border-slate-350 dark:border-slate-750 rounded transition disabled:opacity-50 flex items-center gap-1"
                >
                  <Send size={13} /> Open Manual Link & Log
                </button>

                <button
                  onClick={() => executeReminder('WHATSAPP')}
                  disabled={submittingAction}
                  className="px-4 py-1.5 text-xs font-bold bg-blue-600 hover:bg-blue-750 disabled:bg-blue-650 text-white rounded shadow transition flex items-center gap-1"
                >
                  {submittingAction ? (
                    <Loader2 size={13} className="animate-spin" />
                  ) : (
                    <MessageSquare size={13} />
                  )}
                  {whatsappConnected ? 'Send Auto WhatsApp' : 'Direct Link Fallback'}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Confirm dialogues */}
      <ConfirmDialog
        isOpen={showBulkConfirm}
        onClose={() => setShowBulkConfirm(false)}
        onConfirm={handleBulkSend}
        title="Send Bulk Reminders"
        message={
          <span>
            {selectedIds.length} select kiya hua shop owners ko queued background WhatsApp notifications dispatch ki jayengi. Confirm karein?
            <br /><br />
            <strong className="text-amber-600 dark:text-amber-400">Safety Notice:</strong> WhatsApp account safety aur spam ban prevention ke liye har message me 2.5 seconds ka anti-spam delay apply ho raha hai.
          </span>
        }
        confirmLabel="Queue Reminders"
        danger={false}
      />

      <ConfirmDialog
        isOpen={showCooldownConfirm}
        onClose={() => {
          setShowCooldownConfirm(false)
          setCooldownCustomer(null)
        }}
        onConfirm={handleBypassCooldown}
        title="Spam-Guard Cooldown Warning"
        message={cooldownConfirmMsg}
        confirmLabel="Yes, Send Anyway"
        danger={true}
      />
    </div>
  )
}
