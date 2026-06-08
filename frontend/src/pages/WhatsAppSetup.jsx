import { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  QrCode, RefreshCw, CheckCircle2, AlertCircle, MessageSquare,
  Send, Users, LogOut, Loader2, Sparkles, HelpCircle, FileText,
  Type, StopCircle, ChevronRight
} from 'lucide-react'
import api from '../services/api'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'
import { getCustomerLedger, generateLedgerHtml } from '../utils/ledger'

export default function WhatsAppSetup() {
  const toast = useToast()
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Connection states
  const [status, setStatus] = useState('INITIALIZING')
  const [qrCode, setQrCodeState] = useState(null)
  const qrCodeRef = useRef(null)
  const setQrCode = (val) => {
    qrCodeRef.current = val
    setQrCodeState(val)
  }
  const [qrLoading, setQrLoadingState] = useState(false)
  const qrLoadingRef = useRef(false)
  const setQrLoading = (val) => {
    qrLoadingRef.current = val
    setQrLoadingState(val)
  }
  const [statusLoading, setStatusLoading] = useState(true)

  // Customer queue states
  const [customers, setCustomers] = useState([])
  const [selectedIds, setSelectedIds] = useState([])
  const [customersLoading, setCustomersLoading] = useState(false)

  // Broadcast mode: 'TEXT' or 'PDF'
  const [broadcastMode, setBroadcastMode] = useState('PDF')

  // Confirm dialog states
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false)
  const [showBroadcastConfirm, setShowBroadcastConfirm] = useState(false)

  // Client-side broadcast progress
  const [progress, setProgress] = useState({
    isSending: false,
    total: 0,
    processed: 0,
    success: 0,
    failed: 0,
    currentCustomer: '',
    logs: []   // { name, status: 'success' | 'failed', reason? }
  })

  // Stop signal ref — set to true to break loop
  const stopSignalRef = useRef(false)

  // Polling references
  const statusPollRef = useRef(null)

  // ─── 1. Fetch Connection Status ───────────────────────────────────────────
  const checkStatus = useCallback(async (isInitial = false) => {
    if (isInitial) setStatusLoading(true)
    try {
      const res = await api.get('/customers/whatsapp/status')
      const currentStatus = res.data.data?.status || 'DISCONNECTED'
      setStatus(currentStatus)

      if (currentStatus === 'CONNECTED') {
        setQrCode(null)
        if (statusPollRef.current) {
          clearInterval(statusPollRef.current)
          statusPollRef.current = null
        }
      } else if (currentStatus === 'DISCONNECTED' && !qrCodeRef.current && !qrLoadingRef.current) {
        fetchQrCode()
      }
    } catch (err) {
      console.error('Failed to fetch WhatsApp status:', err)
      setStatus('DISCONNECTED')
    } finally {
      if (isInitial) setStatusLoading(false)
    }
  }, [])

  // ─── 2. Fetch QR Code ─────────────────────────────────────────────────────
  const fetchQrCode = async () => {
    setQrLoading(true)
    try {
      const res = await api.get('/customers/whatsapp/qr')
      setQrCode(res.data.data?.qr || null)
    } catch (err) {
      console.error('Failed to fetch QR code:', err)
    } finally {
      setQrLoading(false)
    }
  }

  // ─── 3. Logout ────────────────────────────────────────────────────────────
  const handleLogout = async () => {
    setShowLogoutConfirm(false)
    setStatusLoading(true)
    try {
      await api.post('/customers/whatsapp/logout')
      setStatus('DISCONNECTED')
      setQrCode(null)
      fetchQrCode()
      toast.success('Device disconnected successfully.')
    } catch (err) {
      console.error('Failed to log out device:', err)
      toast.error('Failed to disconnect device.')
    } finally {
      setStatusLoading(false)
    }
  }

  // ─── 4. Fetch Outstanding Customers ───────────────────────────────────────
  const loadCustomers = useCallback(async () => {
    setCustomersLoading(true)
    try {
      const res = await api.get('/customers?page=0&size=1000')
      const allCustomers = res.data.data?.content || []
      const outstanding = allCustomers.filter(c => Number(c.totalPending || 0) > 0)
      setCustomers(outstanding)
      setSelectedIds(outstanding.map(c => c.id))
    } catch (err) {
      console.error('Failed to load outstanding customers:', err)
    } finally {
      setCustomersLoading(false)
    }
  }, [])

  // ─── 5. Setup ─────────────────────────────────────────────────────────────
  useEffect(() => {
    checkStatus(true)
    loadCustomers()
    statusPollRef.current = setInterval(() => checkStatus(false), 5000)
    return () => {
      if (statusPollRef.current) clearInterval(statusPollRef.current)
    }
  }, [checkStatus, loadCustomers])

  // ─── 6. Selection helpers ─────────────────────────────────────────────────
  const handleToggleSelectAll = () => {
    if (selectedIds.length === customers.length) setSelectedIds([])
    else setSelectedIds(customers.map(c => c.id))
  }

  const handleToggleSelect = (id) => {
    setSelectedIds(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])
  }

  // ─── 7. Generate PDF Blob for one customer ────────────────────────────────
  const generatePdfForCustomer = async (customer, allBills, allPayments) => {
    const html2pdf = (await import('html2pdf.js')).default

    const ledger = getCustomerLedger(customer, allBills, allPayments)
    const totalUdhar = ledger.reduce((s, e) => s + e.debit, 0)
    const totalPaid  = ledger.reduce((s, e) => s + e.credit, 0)
    const outstanding = totalUdhar - totalPaid

    const htmlContent = generateLedgerHtml(customer, ledger, totalUdhar, totalPaid, outstanding)

    const container = document.createElement('div')
    container.style.cssText = 'position:fixed;left:-9999px;top:0;width:800px;'
    container.innerHTML = htmlContent
    document.body.appendChild(container)

    const blob = await html2pdf()
      .set({
        margin: [8, 8, 8, 8],
        filename: `ledger_${customer.name}.pdf`,
        image: { type: 'jpeg', quality: 0.92 },
        html2canvas: { scale: 2, useCORS: true, logging: false },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
      })
      .from(container)
      .outputPdf('blob')

    document.body.removeChild(container)
    return blob
  }

  // ─── 8. Convert Blob to Base64 ────────────────────────────────────────────
  const blobToBase64 = (blob) => new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onloadend = () => resolve(reader.result.split(',')[1])
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })

  // ─── 9. Build text reminder message ──────────────────────────────────────
  const buildTextMessage = (customer) => {
    const outstanding = Number(customer.totalPending || 0)
    return `*LARI TRADERS*\nOutstanding Balance Reminder\n\nDear *${customer.name}*${customer.shopName ? ` (${customer.shopName})` : ''},\n\nYou have an outstanding balance of *₹${outstanding.toLocaleString('en-IN', { minimumFractionDigits: 2 })}*.\n\nPlease clear your dues as soon as possible. Thank you!`
  }

  // ─── 10. Main Broadcast Loop ──────────────────────────────────────────────
  const runBroadcast = useCallback(async () => {
    setShowBroadcastConfirm(false)
    stopSignalRef.current = false

    const selectedCustomers = customers.filter(c => selectedIds.includes(c.id))
    if (selectedCustomers.length === 0) return

    // For PDF mode — fetch all bills & payments once upfront
    let allBills = []
    let allPayments = []
    if (broadcastMode === 'PDF') {
      try {
        toast.info('Loading data for PDF generation...')
        const [bRes, pRes] = await Promise.all([
          api.get('/bills?size=5000'),
          api.get('/payments')
        ])
        allBills    = bRes.data?.data || bRes.data || []
        allPayments = pRes.data || []
      } catch (err) {
        toast.error('Could not fetch bill/payment data. Aborting.')
        console.error(err)
        return
      }
    }

    setProgress({
      isSending: true,
      total: selectedCustomers.length,
      processed: 0,
      success: 0,
      failed: 0,
      currentCustomer: selectedCustomers[0]?.name || '',
      logs: []
    })

    let success = 0
    let failed  = 0
    const logs  = []

    for (let i = 0; i < selectedCustomers.length; i++) {
      if (stopSignalRef.current) {
        toast.warning('Broadcast stopped by user.')
        break
      }

      const customer = selectedCustomers[i]

      setProgress(prev => ({
        ...prev,
        currentCustomer: customer.name,
        processed: i
      }))

      try {
        const phone = (customer.phone || '').trim().replace(/\D/g, '')
        const phoneWithCountry = phone.length === 10 ? '91' + phone : phone

        if (!phoneWithCountry || phoneWithCountry.length < 10) {
          throw new Error('Invalid phone number')
        }

        if (broadcastMode === 'PDF') {
          // — PDF mode —
          const pdfBlob = await generatePdfForCustomer(customer, allBills, allPayments)
          const base64  = await blobToBase64(pdfBlob)

          await api.post('/customers/whatsapp/send-media', {
            phone: phoneWithCountry,
            media: base64,
            filename: `Ledger_${customer.name.replace(/\s+/g, '_')}.pdf`,
            caption: `Dear ${customer.name}, please find your account ledger statement attached. Kindly clear your outstanding balance. Thank you! — Lari Traders`
          })
        } else {
          // — Text mode —
          const message = buildTextMessage(customer)
          await api.post('/customers/whatsapp/send-text', {
            phone: phoneWithCountry,
            message
          })
        }

        success++
        logs.push({ name: customer.name, status: 'success' })
        toast.success(`✅ Sent to ${customer.name}`)
      } catch (err) {
        failed++
        const reason = err?.response?.data?.message || err.message || 'Unknown error'
        logs.push({ name: customer.name, status: 'failed', reason })
        console.error(`Failed for ${customer.name}:`, err)
      }

      setProgress(prev => ({
        ...prev,
        processed: i + 1,
        success,
        failed,
        logs: [...logs]
      }))

      // Small delay between messages to avoid WhatsApp spam detection
      if (i < selectedCustomers.length - 1 && !stopSignalRef.current) {
        await new Promise(r => setTimeout(r, 1500))
      }
    }

    setProgress(prev => ({
      ...prev,
      isSending: false,
      currentCustomer: 'Completed'
    }))

    toast.success(`Broadcast done! ✅ ${success} sent, ❌ ${failed} failed.`)
    loadCustomers()
  }, [customers, selectedIds, broadcastMode, loadCustomers, toast])

  const handleStartBroadcastClick = () => {
    if (selectedIds.length === 0) {
      toast.warning('Please select at least one customer.')
      return
    }
    setShowBroadcastConfirm(true)
  }

  const handleStopBroadcast = () => {
    stopSignalRef.current = true
  }

  // ─── RENDER ───────────────────────────────────────────────────────────────
  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">WhatsApp Bulk Reminders</h2>
          <p className="page-subtitle">Link your WhatsApp and send outstanding balance statements to all customers.</p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '1fr 2fr', gap: 'var(--space-6)', alignItems: 'start' }}>

        {/* ── Left: Device Setup ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          <div className="card" style={{ padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <h3 className="font-bold text-sm text-slate-800 dark:text-slate-200 uppercase tracking-wider flex items-center gap-2">
              <Sparkles size={16} className="text-violet-500" /> Device Setup
            </h3>

            {statusLoading ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '200px', gap: 'var(--space-3)' }}>
                <Loader2 className="animate-spin text-violet-500" size={32} />
                <span className="text-xs text-muted">Checking connection status...</span>
              </div>
            ) : status === 'CONNECTED' ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 'var(--space-4)', padding: 'var(--space-2) 0' }}>
                <motion.div initial={{ scale: 0.8 }} animate={{ scale: 1 }} className="text-success">
                  <CheckCircle2 size={64} />
                </motion.div>
                <div>
                  <h4 className="font-bold text-base text-slate-800 dark:text-slate-100">WhatsApp is Linked</h4>
                  <p className="text-xs text-muted" style={{ marginTop: '2px' }}>
                    Your device is connected. Reminders will be sent directly from your account.
                  </p>
                </div>
                <button
                  className="btn btn-secondary w-full"
                  onClick={() => setShowLogoutConfirm(true)}
                  style={{ border: '1px solid var(--color-danger)', color: 'var(--color-danger)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)' }}
                >
                  <LogOut size={16} /> Disconnect Device
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <div className="badge badge-danger" style={{ alignSelf: 'flex-start', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <AlertCircle size={12} /> Disconnected
                </div>
                <p className="text-xs text-muted">
                  Scan this QR code from your phone's WhatsApp (Settings → Linked Devices) to authenticate.
                </p>

                <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', background: 'var(--color-surface-2)', borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', minHeight: isMobile ? '180px' : '220px' }}>
                  {qrLoading ? (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
                      <Loader2 className="animate-spin text-violet-500" size={24} />
                      <span className="text-xs text-muted">Generating fresh QR...</span>
                    </div>
                  ) : qrCode ? (
                    <motion.img initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                      src={qrCode} alt="WhatsApp Setup QR"
                      style={{ maxWidth: '100%', height: 'auto', width: isMobile ? '160px' : '200px', display: 'block' }}
                    />
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', textAlign: 'center', padding: '0 var(--space-4)' }}>
                      <QrCode size={48} className="text-slate-400" />
                      <span className="text-xs text-muted">Waiting for QR Code...</span>
                      <button className="btn btn-ghost btn-sm" onClick={fetchQrCode} style={{ display: 'flex', alignItems: 'center', gap: '4px', marginTop: 'var(--space-2)' }}>
                        <RefreshCw size={12} /> Retry
                      </button>
                    </div>
                  )}
                </div>

                <div style={{ padding: 'var(--space-3)', background: 'rgba(99, 102, 241, 0.05)', borderRadius: 'var(--radius-sm)', borderLeft: '3px solid var(--color-info)', display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-start' }}>
                  <HelpCircle size={16} className="text-info" style={{ flexShrink: 0, marginTop: '2px' }} />
                  <div className="text-[11px] text-slate-600 dark:text-slate-400" style={{ lineHeight: '1.4' }}>
                    <strong>Note:</strong> Make sure the WhatsApp background service is running (<code>npm start</code> inside <code>whatsapp-service</code>).
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* ── Broadcast Progress Panel ── */}
          <AnimatePresence>
            {progress.isSending && (
              <motion.div
                initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 15 }}
                className="card"
                style={{ padding: 'var(--space-6)', border: '1px solid var(--color-info)', background: 'rgba(56, 189, 248, 0.03)' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <h3 className="font-bold text-sm text-slate-800 dark:text-slate-200 uppercase tracking-wider flex items-center gap-2">
                    <Loader2 className="animate-spin text-info" size={16} /> Broadcast Live
                  </h3>
                  <button
                    onClick={handleStopBroadcast}
                    className="btn btn-sm"
                    style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--color-danger)', border: '1px solid var(--color-danger)', background: 'rgba(239,68,68,0.05)', fontSize: '11px', padding: '4px 10px', borderRadius: 'var(--radius-sm)' }}
                  >
                    <StopCircle size={13} /> Stop
                  </button>
                </div>

                <div style={{ marginTop: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', fontWeight: 600 }}>
                      <span className="text-slate-600 dark:text-slate-400">Sending: <span className="text-info font-bold">{progress.currentCustomer}</span></span>
                      <span className="text-slate-800 dark:text-slate-100">{progress.processed} / {progress.total}</span>
                    </div>
                    <div style={{ width: '100%', height: '8px', background: 'var(--color-border)', borderRadius: '4px', overflow: 'hidden', marginTop: 'var(--space-2)' }}>
                      <div style={{ width: `${progress.total > 0 ? (progress.processed / progress.total) * 100 : 0}%`, height: '100%', background: 'var(--color-info)', borderRadius: '4px', transition: 'width 0.4s ease' }} />
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)', textAlign: 'center' }}>
                    <div style={{ padding: 'var(--space-2)', background: 'rgba(34,197,94,0.08)', borderRadius: 'var(--radius-sm)' }}>
                      <div className="text-xs text-success font-bold">{progress.success}</div>
                      <div className="text-[10px] text-muted uppercase font-semibold">Sent ✅</div>
                    </div>
                    <div style={{ padding: 'var(--space-2)', background: 'rgba(239,68,68,0.08)', borderRadius: 'var(--radius-sm)' }}>
                      <div className="text-xs text-danger font-bold">{progress.failed}</div>
                      <div className="text-[10px] text-muted uppercase font-semibold">Failed ❌</div>
                    </div>
                  </div>

                  {/* Recent log entries */}
                  {progress.logs.length > 0 && (
                    <div style={{ maxHeight: '120px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      {[...progress.logs].reverse().slice(0, 8).map((log, idx) => (
                        <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px' }}>
                          <span>{log.status === 'success' ? '✅' : '❌'}</span>
                          <span className="text-slate-700 dark:text-slate-300 font-medium">{log.name}</span>
                          {log.reason && <span className="text-muted text-[10px]">— {log.reason}</span>}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* ── Completed Summary (after broadcast) ── */}
          <AnimatePresence>
            {!progress.isSending && progress.total > 0 && (
              <motion.div
                initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                className="card"
                style={{ padding: 'var(--space-5)', border: '1px solid var(--color-success)', background: 'rgba(34,197,94,0.03)' }}
              >
                <h3 className="font-bold text-sm text-slate-800 dark:text-slate-200 uppercase tracking-wider flex items-center gap-2">
                  <CheckCircle2 size={16} className="text-success" /> Broadcast Completed
                </h3>
                <div style={{ marginTop: 'var(--space-3)', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)', textAlign: 'center' }}>
                  <div style={{ padding: 'var(--space-2)', background: 'rgba(34,197,94,0.08)', borderRadius: 'var(--radius-sm)' }}>
                    <div className="text-sm text-success font-bold">{progress.success}</div>
                    <div className="text-[10px] text-muted uppercase font-semibold">Sent ✅</div>
                  </div>
                  <div style={{ padding: 'var(--space-2)', background: 'rgba(239,68,68,0.08)', borderRadius: 'var(--radius-sm)' }}>
                    <div className="text-sm text-danger font-bold">{progress.failed}</div>
                    <div className="text-[10px] text-muted uppercase font-semibold">Failed ❌</div>
                  </div>
                </div>
                {progress.logs.some(l => l.status === 'failed') && (
                  <div style={{ marginTop: 'var(--space-3)', display: 'flex', flexDirection: 'column', gap: '3px' }}>
                    <p className="text-[10px] text-muted font-semibold uppercase">Failed customers:</p>
                    {progress.logs.filter(l => l.status === 'failed').map((log, idx) => (
                      <div key={idx} style={{ fontSize: '11px', color: 'var(--color-danger)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <ChevronRight size={10} /> {log.name} {log.reason ? `— ${log.reason}` : ''}
                      </div>
                    ))}
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* ── Right: Customer Queue ── */}
        <div className="card" style={{ padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 className="font-bold text-sm text-slate-800 dark:text-slate-200 uppercase tracking-wider flex items-center gap-2">
              <Users size={16} className="text-slate-500" /> Pending Reminders Queue
            </h3>
            <span className="text-xs text-muted">{customers.length} customers with outstanding balance</span>
          </div>

          {/* ── Broadcast Mode Toggle ── */}
          {status === 'CONNECTED' && !customersLoading && customers.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-3)', background: 'var(--color-surface-2)', borderRadius: 'var(--radius-md)' }}>
              <span className="text-xs font-semibold text-slate-600 dark:text-slate-400">Send Mode:</span>
              <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                <button
                  onClick={() => setBroadcastMode('PDF')}
                  disabled={progress.isSending}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 14px',
                    borderRadius: 'var(--radius-sm)', fontSize: '12px', fontWeight: 600, cursor: 'pointer', border: 'none', transition: 'all 0.2s',
                    background: broadcastMode === 'PDF' ? 'var(--color-accent)' : 'transparent',
                    color: broadcastMode === 'PDF' ? '#fff' : 'var(--color-text-muted)',
                    outline: broadcastMode !== 'PDF' ? '1px solid var(--color-border)' : 'none'
                  }}
                >
                  <FileText size={13} /> PDF Ledger
                </button>
                <button
                  onClick={() => setBroadcastMode('TEXT')}
                  disabled={progress.isSending}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 14px',
                    borderRadius: 'var(--radius-sm)', fontSize: '12px', fontWeight: 600, cursor: 'pointer', border: 'none', transition: 'all 0.2s',
                    background: broadcastMode === 'TEXT' ? 'var(--color-accent)' : 'transparent',
                    color: broadcastMode === 'TEXT' ? '#fff' : 'var(--color-text-muted)',
                    outline: broadcastMode !== 'TEXT' ? '1px solid var(--color-border)' : 'none'
                  }}
                >
                  <Type size={13} /> Text Reminder
                </button>
              </div>
              <span className="text-[10px] text-muted" style={{ marginLeft: 'auto' }}>
                {broadcastMode === 'PDF' ? '📄 Full PDF ledger statement attached' : '💬 Short text reminder message'}
              </span>
            </div>
          )}

          {status !== 'CONNECTED' ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-10) var(--space-4)', gap: 'var(--space-3)', border: '1px dashed var(--color-border)', borderRadius: 'var(--radius-md)', textAlign: 'center' }}>
              <MessageSquare size={40} className="text-slate-300" />
              <div>
                <h4 className="font-semibold text-sm text-slate-700 dark:text-slate-300">WhatsApp Connection Required</h4>
                <p className="text-xs text-muted" style={{ maxWidth: '300px', marginTop: '2px' }}>
                  Please scan the QR code and connect your device to access bulk reminder options.
                </p>
              </div>
            </div>
          ) : customersLoading ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '240px', gap: 'var(--space-3)' }}>
              <Loader2 className="animate-spin text-slate-400" size={32} />
              <span className="text-xs text-muted">Scanning for outstanding balances...</span>
            </div>
          ) : customers.length === 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-10) var(--space-4)', gap: 'var(--space-3)', border: '1px dashed var(--color-border)', borderRadius: 'var(--radius-md)', textAlign: 'center' }}>
              <CheckCircle2 size={40} className="text-success" />
              <div>
                <h4 className="font-semibold text-sm text-slate-700 dark:text-slate-300">All Balances Clear!</h4>
                <p className="text-xs text-muted" style={{ marginTop: '2px' }}>No customers currently have outstanding balances. Great job!</p>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-theme shadow-sm" style={{ maxHeight: '380px', overflowY: 'auto' }}>
                <table className="w-full border-collapse text-left text-sm text-slate-500 dark:text-slate-400" style={{ tableLayout: 'fixed', width: '100%' }}>
                  <thead className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
                    <tr>
                      <th className="px-4 py-3" style={{ width: '40px' }}>
                        <input
                          type="checkbox"
                          checked={selectedIds.length === customers.length && customers.length > 0}
                          onChange={handleToggleSelectAll}
                          disabled={progress.isSending}
                          style={{ cursor: 'pointer', width: '16px', height: '16px' }}
                        />
                      </th>
                      <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider" style={{ width: '45%' }}>Customer / Shop</th>
                      <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider" style={{ width: '25%' }}>Phone</th>
                      <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider" style={{ width: '30%' }}>O/S Balance</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-850">
                    {customers.map((row) => (
                      <tr key={row.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 text-slate-900 dark:text-slate-100 transition-colors duration-150">
                        <td className="px-4 py-3 align-middle">
                          <input
                            type="checkbox"
                            checked={selectedIds.includes(row.id)}
                            onChange={() => handleToggleSelect(row.id)}
                            disabled={progress.isSending}
                            style={{ cursor: 'pointer', width: '16px', height: '16px' }}
                          />
                        </td>
                        <td className="px-4 py-3 align-middle" style={{ wordBreak: 'break-word', whiteSpace: 'normal' }}>
                          <div className="font-semibold text-xs text-slate-800 dark:text-slate-200">{row.name}</div>
                          {row.shopName && <div className="text-[10px] text-muted">{row.shopName}</div>}
                        </td>
                        <td className="px-4 py-3 align-middle text-xs" style={{ wordBreak: 'break-word', whiteSpace: 'normal' }}>
                          {row.phone || <span className="text-muted italic">—</span>}
                        </td>
                        <td className="px-4 py-3 align-middle font-bold text-danger text-xs">
                          ₹{Number(row.totalPending || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Footer actions */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--color-border)', paddingTop: 'var(--space-4)' }}>
                <span className="text-xs text-muted">
                  <strong>{selectedIds.length}</strong> of {customers.length} selected
                  {broadcastMode === 'PDF' && <span className="text-info" style={{ marginLeft: '6px' }}>· PDF mode</span>}
                  {broadcastMode === 'TEXT' && <span className="text-muted" style={{ marginLeft: '6px' }}>· Text mode</span>}
                </span>

                {progress.isSending ? (
                  <button
                    className="btn"
                    onClick={handleStopBroadcast}
                    style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', color: 'var(--color-danger)', border: '1px solid var(--color-danger)', background: 'rgba(239,68,68,0.05)' }}
                  >
                    <StopCircle size={15} /> Stop Broadcast
                  </button>
                ) : (
                  <button
                    className="btn btn-primary"
                    onClick={handleStartBroadcastClick}
                    disabled={selectedIds.length === 0}
                    style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', background: 'var(--color-accent)' }}
                  >
                    {broadcastMode === 'PDF' ? <FileText size={15} /> : <Send size={15} />}
                    {broadcastMode === 'PDF' ? `Send PDF Statements (${selectedIds.length})` : `Send Text Reminders (${selectedIds.length})`}
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Dialogs ── */}
      <ConfirmDialog
        isOpen={showLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
        onConfirm={handleLogout}
        title="Disconnect WhatsApp"
        message="Are you sure you want to disconnect your WhatsApp device?"
        confirmLabel="Disconnect"
        danger={true}
      />

      <ConfirmDialog
        isOpen={showBroadcastConfirm}
        onClose={() => setShowBroadcastConfirm(false)}
        onConfirm={runBroadcast}
        title={broadcastMode === 'PDF' ? 'Send PDF Ledger Statements' : 'Send Text Reminders'}
        message={
          broadcastMode === 'PDF'
            ? `${selectedIds.length} customers ko PDF ledger statement WhatsApp pe bheji jayegi. PDF frontend pe generate hogi. Confirm karein?`
            : `${selectedIds.length} customers ko text reminder message bheji jayegi. Confirm karein?`
        }
        confirmLabel={broadcastMode === 'PDF' ? 'Send PDFs' : 'Send Messages'}
        danger={false}
      />
    </div>
  )
}
