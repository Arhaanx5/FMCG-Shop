import { useState, useEffect, useRef } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  IndianRupee, ShoppingCart, TrendingUp, TrendingDown,
  AlertTriangle, Package, Users, Truck, Sparkles, Brain, Lightbulb, BookOpen, Send, RefreshCw, Cloud, Warehouse, Receipt
} from 'lucide-react'
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import api from '../services/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import Modal from '../components/Modal'
import { usePaymentSocketContext } from '../context/PaymentSocketContext'

function formatRelativeTime(dateStr) {
  if (!dateStr) return 'Just now'
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now - d
    const diffSec = Math.floor(diffMs / 1000)
    const diffMin = Math.floor(diffSec / 60)
    const diffHr = Math.floor(diffMin / 60)

    if (diffSec < 60 || diffMs < 0) return 'Just now'
    if (diffMin < 60) return `${diffMin}m ago`
    if (diffHr < 24) return `${diffHr}h ago`
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
  } catch (e) {
    return 'Just now'
  }
}

const PIE_COLORS = ['#f59e0b', '#3b82f6', '#10b981', '#ef4444', '#8b5cf6', '#ec4899']
const MODERN_PIE_COLORS = ['#8b5cf6', '#a78bfa', '#ec4899', '#f97316', '#f59e0b', '#10b981']
const CYBER_PIE_COLORS = ['#00d2ff', '#3b82f6', '#60a5fa', '#0a80df', '#38bdf8', '#1e3a8a']
const NEON_PIE_COLORS = ['#00ffcc', '#d946ef', '#10b981', '#f43f5e', '#a855f7', '#06b6d4']

function Sparkline({ data = [], color = 'var(--color-accent)', width = 60, height = 22 }) {
  if (!data || data.length === 0) return null
  const points = data.map(val => Number(val || 0))
  const max = Math.max(...points, 1)
  const min = Math.min(...points, 0)
  const range = max - min
  
  const svgPoints = points.map((val, idx) => {
    const x = (idx / (points.length - 1)) * width
    const y = height - ((val - min) / (range || 1)) * height
    return `${x},${y}`
  }).join(' ')

  return (
    <svg width={width} height={height} className="overflow-visible">
      <polyline
        fill="none"
        stroke={color}
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        points={svgPoints}
      />
    </svg>
  )
}

function LiveActivityWidget({ recentBills = [], lowStockList = [], pendingDeliveries = [], livePayments = [], liveDeliveries = [] }) {
  const activities = []

  // 1. Add real-time payments first
  livePayments.forEach((payment) => {
    const amt = Number(payment.amount || 0).toLocaleString('en-IN')
    const shop = payment.customerShopName ? ` (${payment.customerShopName})` : ''
    const isWaived = payment.paymentMode === 'WAIVE_OFF'
    activities.push({
      id: `payment-${payment.id || Math.random()}`,
      type: 'payment',
      title: isWaived ? `₹${amt} Waived Off` : `₹${amt} Received`,
      subtitle: `${payment.customerName}${shop}`,
      time: formatRelativeTime(payment.paidAt),
      color: isWaived ? 'var(--color-text-muted)' : 'var(--color-cash)',
      icon: <IndianRupee size={12} />,
    })
  })

  // 1b. Add real-time completed/updated deliveries
  liveDeliveries.forEach((delivery) => {
    const billNum = delivery.bill?.billNumber || delivery.billNumber || 'N/A'
    const status = delivery.status
    const isSelf = delivery.type === 'SELF_PICKUP'
    const boyName = delivery.deliveryBoy?.name || delivery.deliveryBoyName || (isSelf ? 'Self Pickup' : 'Delivery Boy')
    const customerName = delivery.bill?.customer?.name || delivery.bill?.customerName || 'Customer'
    const shopStr = delivery.bill?.customer?.shopName || delivery.bill?.customerShopName ? ` (${delivery.bill?.customer?.shopName || delivery.bill?.customerShopName})` : ''

    let actionText = 'Delivery Completed'
    let color = 'var(--color-success)'

    if (status === 'FAILED') {
      actionText = 'Delivery Failed'
      color = 'var(--color-damage)'
    } else if (status === 'COD_DEFAULTED') {
      actionText = 'Delivery Default (Udhar)'
      color = 'var(--color-pending)'
    } else if (status === 'COD_PARTIAL') {
      actionText = 'Partial Cash Collected'
      color = 'var(--color-upi)'
    } else if (status === 'OUT') {
      actionText = 'Dispatched Out'
      color = 'var(--color-new-credit)'
    }

    activities.push({
      id: `delivery-live-${delivery.id || Math.random()}-${status}`,
      type: 'delivery-live',
      title: `${actionText} (Bill #${billNum})`,
      subtitle: `${customerName}${shopStr} by ${boyName}`,
      time: formatRelativeTime(delivery.deliveredAt || delivery.dispatchedAt),
      color: color,
      icon: <Truck size={12} />,
    })
  })

  // 2. Add invoices
  recentBills.slice(0, 3).forEach((bill, idx) => {
    const displayNum = bill.billNumber || (bill.id ? bill.id.slice(0, 8) : (idx + 101));
    activities.push({
      id: `bill-${bill.id || idx}`,
      type: 'bill',
      title: `Invoice #${displayNum}`,
      subtitle: `${bill.customerName || 'Retailer'} • ₹${Number(bill.grandTotal || 0).toLocaleString('en-IN')}`,
      time: formatRelativeTime(bill.createdAt),
      color: 'var(--color-bills)',
      icon: <ShoppingCart size={12} />,
    })
  })

  // 3. Add stock alerts
  lowStockList.slice(0, 2).forEach((item, idx) => {
    activities.push({
      id: `stock-${item.productId || idx}`,
      type: 'stock',
      title: `Low stock: ${item.productName}`,
      subtitle: `${item.currentStock} left (min: ${item.threshold})`,
      time: idx === 0 ? '2h ago' : '5h ago',
      color: 'var(--color-pending)',
      icon: <Package size={12} />,
    })
  })

  // 4. Add pending deliveries
  pendingDeliveries.slice(0, 2).forEach((item, idx) => {
    activities.push({
      id: `delivery-${item.id || idx}`,
      type: 'delivery',
      title: `Dispatch Scheduled`,
      subtitle: `Bill: ${item.billNumber || 'N/A'}`,
      time: idx === 0 ? '3h ago' : '6h ago',
      color: 'var(--color-new-credit)',
      icon: <Truck size={12} />,
    })
  })

  return (
    <div className="card h-full flex flex-col justify-between" style={{ padding: 'var(--space-4)' }}>
      <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-3)', paddingBottom: 'var(--space-2)' }}>
        <span className="card-title text-xs tracking-wider uppercase font-semibold">Live Log Activity</span>
        <span className="pulse-dot" style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', display: 'inline-block' }} />
      </div>
      <div className="flex flex-col gap-2 mt-1">
        {activities.length === 0 ? (
          <p className="text-muted text-xs text-center py-4">No recent activity detected.</p>
        ) : (
          activities.slice(0, 5).map((act) => (
            <div key={act.id} className="flex gap-2 items-center text-xs py-1.5 border-b border-slate-100 dark:border-slate-800/40 last:border-0 last:pb-0 truncate">
              <span 
                className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                style={{ background: act.color, boxShadow: `0 0 8px ${act.color}80` }}
              />
              <span className="font-semibold text-slate-700 dark:text-slate-200 truncate flex-1">
                {act.title} <span className="font-normal text-slate-550 dark:text-slate-400">({act.subtitle})</span>
              </span>
              <span className="text-[10px] text-slate-400 dark:text-slate-505 flex-shrink-0">
                {act.time}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

function AiCopilotWidget({ year, month }) {
  const [activeTab, setActiveTab] = useState('insights') // 'insights' | 'chat'
  const [insights, setInsights] = useState('')
  const [insightsLoading, setInsightsLoading] = useState(false)
  const [insightsError, setInsightsError] = useState(false)
  const [chatInput, setChatInput] = useState('')
  const [chatMessages, setChatMessages] = useState([
    { role: 'assistant', content: 'Hello! I am your FMCG Business Copilot. Ask me anything, e.g., "what is our low stock status?" or "analyze this month revenue".' }
  ])
  const [chatLoading, setChatLoading] = useState(false)
  const messagesEndRef = useRef(null)
  const toast = useToast()

  const fetchInsights = async (force = false) => {
    setInsightsLoading(true)
    setInsightsError(false)
    try {
      const res = await api.get(`/dashboard/ai/insights?year=${year}&month=${month}${force ? '&force=true' : ''}`)
      if (res.data?.data?.insights) {
        setInsights(res.data.data.insights)
      } else {
        setInsights('No insights generated.')
      }
    } catch (err) {
      console.error(err)
      setInsightsError(true)
      toast.error('Failed to load AI Insights')
    } finally {
      setInsightsLoading(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'insights' && !insights) {
      fetchInsights(false)
    }
  }, [year, month, activeTab])

  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [chatMessages])

  const handleSendMessage = async (e) => {
    if (e) e.preventDefault()
    if (!chatInput.trim()) return
    const userMsg = chatInput.trim()
    setChatInput('')
    setChatMessages(prev => [...prev, { role: 'user', content: userMsg }])
    setChatLoading(true)

    try {
      const res = await api.post(`/dashboard/ai/chat?year=${year}&month=${month}`, { message: userMsg })
      if (res.data?.data?.reply) {
        setChatMessages(prev => [...prev, { role: 'assistant', content: res.data.data.reply }])
      } else {
        setChatMessages(prev => [...prev, { role: 'assistant', content: 'Sorry, I could not generate a reply.' }])
      }
    } catch (err) {
      console.error(err)
      toast.error('Failed to send message')
      setChatMessages(prev => [...prev, { role: 'assistant', content: 'FMCG AI Engine is temporarily busy. Please try again.' }])
    } finally {
      setChatLoading(false)
    }
  }

  const handleChipClick = (msg) => {
    setChatInput(msg)
  }

  const renderInsightsMarkdown = (text) => {
    if (!text) return null
    const lines = text.split('\n')
    return (
      <div className="flex flex-col gap-2">
        {lines.map((line, idx) => {
          if (line.startsWith('###')) {
            return <h4 key={idx} className="font-semibold text-sm mt-3 text-purple-400">{line.replace('###', '').trim()}</h4>
          }
          if (line.trim().startsWith('-') || line.trim().startsWith('*')) {
            const cleanLine = line.replace(/^[\s-*]+/, '').trim()
            const parts = cleanLine.split('**')
            return (
              <li key={idx} className="ml-4 list-disc text-xs text-slate-350 dark:text-slate-300">
                {parts.map((p, i) => i % 2 === 1 ? <strong key={i} className="text-slate-800 dark:text-white font-bold">{p}</strong> : p)}
              </li>
            )
          }
          if (line.trim()) {
            const parts = line.split('**')
            return (
              <p key={idx} className="text-xs text-slate-350 dark:text-slate-300">
                {parts.map((p, i) => i % 2 === 1 ? <strong key={i} className="text-slate-800 dark:text-white font-bold">{p}</strong> : p)}
              </p>
            )
          }
          return <div key={idx} className="h-1" />
        })}
      </div>
    )
  }

  return (
    <div className="card ai-insight-card relative overflow-hidden flex flex-col" style={{ minHeight: '380px', padding: 'var(--space-4)' }}>
      <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-3)', paddingBottom: 'var(--space-2)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <Brain size={20} className="text-violet-500" />
          <span className="card-title text-sm">Lari AI Copilot</span>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <button
            onClick={() => setActiveTab('insights')}
            className={`btn btn-sm ${activeTab === 'insights' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '4px 10px', fontSize: '10px', height: 'auto', borderRadius: 'var(--radius-sm)' }}
          >
            Insights
          </button>
          <button
            onClick={() => setActiveTab('chat')}
            className={`btn btn-sm ${activeTab === 'chat' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '4px 10px', fontSize: '10px', height: 'auto', borderRadius: 'var(--radius-sm)' }}
          >
            Chat Advisor
          </button>
        </div>
      </div>

      {activeTab === 'insights' ? (
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, gap: 'var(--space-3)' }}>
          <div className="flex justify-between items-center text-xs text-slate-500 dark:text-slate-400">
            <span>Monthly business intelligence suggestions</span>
            <button 
              onClick={() => fetchInsights(true)} 
              disabled={insightsLoading}
              className="btn btn-sm btn-ghost btn-icon" 
              style={{ width: '24px', height: '24px' }}
            >
              <RefreshCw size={12} className={insightsLoading ? 'animate-spin' : ''} />
            </button>
          </div>
          
          <div style={{ maxHeight: '280px', overflowY: 'auto', flex: 1 }} className="pr-1 text-xs">
            {insightsLoading ? (
              <div className="flex flex-col gap-2.5 py-4 animate-pulse">
                <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-3/4"></div>
                <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-5/6"></div>
                <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-2/3"></div>
                <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-1/2"></div>
              </div>
            ) : insightsError ? (
              <div className="flex flex-col items-center justify-center p-6 text-center gap-3">
                <AlertTriangle className="text-amber-500 animate-bounce" size={28} />
                <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">AI Copilot is busy or offline</span>
                <span className="text-[10px] text-slate-550 dark:text-slate-400">Failed to connect to FMCG AI Engine</span>
                <button
                  onClick={() => fetchInsights(true)}
                  className="btn btn-sm btn-primary mt-2"
                >
                  Retry Connection
                </button>
              </div>
            ) : insights ? (
              renderInsightsMarkdown(insights)
            ) : (
              <p className="text-slate-500 dark:text-slate-400 text-center py-8">Click refresh to load insights.</p>
            )}
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', height: '300px', justifyContent: 'space-between' }}>
          <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }} className="pr-1 text-xs mb-2">
            {chatMessages.map((msg, i) => (
              <div 
                key={i} 
                className={`py-1.5 px-3 rounded-theme-md max-w-[85%] ${
                  msg.role === 'user'
                    ? 'bg-violet-500 text-white self-end'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-850 dark:text-slate-200 self-start'
                }`}
              >
                {msg.content}
              </div>
            ))}
            {chatLoading && (
              <div className="bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 py-1.5 px-3 rounded-theme-md self-start">
                <span className="animate-pulse">Thinking...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-1)', marginBottom: 'var(--space-1)' }}>
              {['Low stock plan?', 'Expiry clearing?', 'Collection tips?'].map((q, idx) => (
                <button 
                  key={idx} 
                  onClick={() => handleChipClick(q)}
                  className="btn btn-sm btn-secondary" 
                  style={{ padding: '2px 8px', fontSize: '9px', height: 'auto', borderRadius: 'var(--radius-sm)' }}
                >
                  {q}
                </button>
              ))}
            </div>

            <form onSubmit={handleSendMessage} style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <input
                type="text"
                placeholder="Ask business advisor..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                className="form-input"
                style={{ height: '32px', fontSize: 'var(--font-size-xs)', padding: '0 var(--space-3)', flex: 1 }}
              />
              <button 
                type="submit" 
                className="btn btn-sm btn-primary btn-icon" 
                style={{ width: '32px', height: '32px' }}
                disabled={chatLoading}
              >
                <Send size={12} />
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

function KPIStatCard({ icon, label, value, prefix = '', suffix = '', change, sparklineData = [], colorVar = 'var(--color-accent)', subtitle, onClick }) {
  const isClickable = !!onClick
  
  const formattedValue = typeof value === 'number' 
    ? value.toLocaleString('en-IN', { maximumFractionDigits: 2 }) 
    : value

  let changeBadge = null
  if (change !== undefined && change !== null) {
    const isPositive = change >= 0
    const absChange = Math.abs(change).toFixed(1)
    changeBadge = (
      <span className={`inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-bold ${
        isPositive 
          ? 'bg-emerald-500/10 text-emerald-500 dark:text-emerald-400' 
          : 'bg-rose-500/10 text-rose-500 dark:text-rose-400'
      }`}>
        {isPositive ? '↑' : '↓'} {absChange}%
      </span>
    )
  }

  return (
    <motion.div
      onClick={onClick}
      whileHover={isClickable ? { y: -3, scale: 1.01 } : { y: -1 }}
      className={`card relative overflow-hidden flex flex-col justify-between p-4 min-h-[110px] transition-all duration-200 ${
        isClickable ? 'cursor-pointer hover:border-accent' : ''
      }`}
      style={{
        border: '1px solid var(--color-border)',
        background: 'var(--color-surface)',
        borderRadius: 'var(--radius-lg)'
      }}
    >
      <div className="flex justify-between items-start gap-2">
        <div className="flex gap-2.5 items-start">
          <div 
            className="w-9 h-9 rounded-full flex items-center justify-center text-white flex-shrink-0"
            style={{ 
              background: `linear-gradient(135deg, ${colorVar} 0%, rgba(0,0,0,0.15) 100%)`,
              boxShadow: `0 4px 10px -2px ${colorVar}60`
            }}
          >
            {icon}
          </div>
          <div className="flex flex-col min-w-0">
            <span className="text-[10px] text-slate-500 dark:text-slate-400 font-semibold tracking-wide uppercase truncate">
              {label}
            </span>
            <span className="text-base font-bold text-slate-800 dark:text-slate-100 tracking-tight mt-0.5">
              {prefix}{formattedValue}{suffix}
            </span>
          </div>
        </div>

        <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
          {changeBadge}
          {sparklineData && sparklineData.length > 0 && (
            <div className="mt-1">
              <Sparkline data={sparklineData} color={colorVar} width={50} height={18} />
            </div>
          )}
        </div>
      </div>

      {subtitle && (
        <div className="text-[10px] text-slate-500 dark:text-slate-400 font-medium mt-2 pt-1 border-t border-slate-100 dark:border-slate-800/50 truncate">
          {subtitle}
        </div>
      )}
    </motion.div>
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [uiTheme] = useOutletContext()
  const { user, aiEnabled, isAdmin, isManager } = useAuth()
  const { livePayments, liveDeliveries } = usePaymentSocketContext()
  const [today, setToday] = useState(null)
  const [monthly, setMonthly] = useState(null)
  const [yearly, setYearly] = useState(null)
  const [viewMode, setViewMode] = useState('day') // 'day' | 'month' | 'year'
  const [loading, setLoading] = useState(true)
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1)
  const [alertScope, setAlertScope] = useState('urgent')
  const [chartTab, setChartTab] = useState('expenses') // 'expenses' | 'customers' | 'stock'
  const toast = useToast()
  const [backupLoading, setBackupLoading] = useState(false)
  const [showOverdueModal, setShowOverdueModal] = useState(false)
  const [recentBills, setRecentBills] = useState([])

  useEffect(() => {
    loadDashboard()
  }, [selectedYear, selectedMonth])

  const loadDashboard = async () => {
    setLoading(true)
    try {
      const res = await api.get(`/dashboard/summary?year=${selectedYear}&month=${selectedMonth}`)
      const summary = res.data.data
      setToday(summary.today)
      setMonthly(summary.monthly)
      setYearly(summary.yearly)
      setRecentBills(summary.recentBills || [])
      
      const overdueList = summary.today?.overdueUdharAlerts || []
      const hasShown = sessionStorage.getItem('overdue_udhar_popup_shown')
      if (overdueList.length > 0 && !hasShown) {
        setShowOverdueModal(true)
        sessionStorage.setItem('overdue_udhar_popup_shown', 'true')
      }
    } catch (err) {
      toast.error('Failed to load dashboard data')
    } finally {
      setLoading(false)
    }
  }

  const handleBackupToDrive = async () => {
    setBackupLoading(true)
    try {
      const res = await api.post('/backup/run')
      if (res.data?.success || res.data?.data?.status === 'SUCCESS') {
        toast.success('Database backup uploaded to Google Drive successfully!')
      } else {
        toast.error('Backup failed: ' + (res.data?.message || 'Unknown error'))
      }
    } catch (err) {
      console.error(err)
      toast.error('Backup failed: ' + (err.response?.data?.message || err.message))
    } finally {
      setBackupLoading(false)
    }
  }

  if (loading || !today || !monthly || !yearly) {
    return (
      <div className="page-container">
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 400 }}>
          <div className="spinner spinner-lg" />
          <p className="text-muted mt-4">Loading dashboard...</p>
        </div>
      </div>
    )
  }

  const calculateChangePct = (curr, prev) => {
    if (prev === undefined || prev === null || Number(prev) === 0) return null
    return ((Number(curr) - Number(prev)) / Number(prev)) * 100
  }

  const getSparklineData = (cardId) => {
    const trend = today?.sevenDayTrend || []
    if (!trend || trend.length === 0) return Array(7).fill(0)
    
    switch (cardId) {
      case 'revenue':
        return trend.map(p => Number(p.revenue || 0))
      case 'bills':
        return trend.map(p => Number(p.bills || 0))
      case 'collection':
        return trend.map(p => Number(p.collection || 0))
      case 'newUdhar':
        return trend.map(p => Number(p.newUdhar || 0))
      case 'cash': {
        const total = Number(today?.todayCollected || 1)
        const cash = Number(today?.todayCollectedCash || 0)
        return trend.map(p => Number(p.collection || 0) * (cash / (total || 1)))
      }
      case 'upi': {
        const total = Number(today?.todayCollected || 1)
        const upi = Number(today?.todayCollectedUpi || 0)
        return trend.map(p => Number(p.collection || 0) * (upi / (total || 1)))
      }
      case 'udharRecovery': {
        const total = Number(today?.todayCollected || 1)
        const udhar = Number(today?.todayCollectedUdhar || 0)
        return trend.map(p => Number(p.collection || 0) * (udhar / (total || 1)))
      }
      case 'expenses': {
        const totalExp = Number(viewMode === 'day' ? today?.todayExpenses : viewMode === 'month' ? monthly?.totalExpenses : yearly?.totalExpenses) || 0
        return Array(7).fill(totalExp / 7)
      }
      case 'netProfit':
        return trend.map(p => Number(p.revenue || 0) * 0.15)
      default:
        return trend.map(p => Number(p.revenue || 0) * 0.05)
    }
  }

  const getActiveCustomersCount = () => {
    if (viewMode === 'year') {
      return yearly?.activeCustomersYear || today?.activeCustomersYear || 0
    } else if (viewMode === 'month') {
      return monthly?.activeCustomersMonth || today?.activeCustomersMonth || 0
    } else {
      return today?.activeCustomersToday || 0
    }
  }

  const lowStockList = today.lowStockAlerts || []
  
  const urgentExpiring = today.expiringBatches?.filter(b => {
    if (!b.expiryDate) return false
    const daysLeft = Math.ceil((new Date(b.expiryDate) - new Date()) / (1000 * 60 * 60 * 24))
    return daysLeft <= 7
  }) || []
  const allExpiring = today.expiringBatches || []
  const expiringList = alertScope === 'urgent' ? urgentExpiring : allExpiring
  
  const urgentInactive = today.inactiveCustomers?.filter(c => {
    if (!c.lastOrderDate) return true
    const daysInactive = Math.ceil((new Date() - new Date(c.lastOrderDate)) / (1000 * 60 * 60 * 24))
    return daysInactive >= 30
  }) || []
  const allInactive = today.inactiveCustomers || []
  const inactiveList = alertScope === 'urgent' ? urgentInactive : allInactive
  
  const pendingDeliveriesList = today.pendingDeliveries || []

  // estimated inventory value at risk
  const expiringValue = allExpiring.reduce((acc, curr) => acc + (curr.stockCount || 0) * 120, 0)

  // 7-day Line Chart data
  const lineChartData = today.sevenDayTrend || []

  // Operational Expenses category Breakdown (filtering out STOCK_PURCHASE and OPENING_STOCK)
  const activeExpenseSource = viewMode === 'year' ? yearly : monthly
  const expenseChartData = activeExpenseSource?.expensesByCategory
    ? Object.entries(activeExpenseSource.expensesByCategory)
        .filter(([name]) => name !== 'STOCK_PURCHASE' && name !== 'OPENING_STOCK')
        .map(([name, value]) => ({ name: name.replace(/_/g, ' '), value: Number(value) }))
    : []

  const activeSegmentCustomers = getActiveCustomersCount()
  const inactiveSegmentCustomers = today?.inactiveCustomersCount || 0
  const npaSegmentCustomers = today?.npaCustomersCount || 0

  const customerHealthData = [
    { name: 'Active', value: activeSegmentCustomers },
    { name: 'Inactive', value: inactiveSegmentCustomers },
    { name: 'NPA', value: npaSegmentCustomers }
  ]

  const stockHealthData = [
    { name: 'Low Stock', value: lowStockList.length },
    { name: 'Expiring 30d', value: allExpiring.length },
    { name: 'Deliveries Pending', value: pendingDeliveriesList.length }
  ]

  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

  const customTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div style={{
          background: 'var(--color-surface-2)', padding: 'var(--space-3) var(--space-4)',
          borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
          fontSize: 'var(--font-size-sm)',
        }}>
          <p style={{ color: 'var(--color-text)', fontWeight: 'var(--font-weight-semibold)' }}>{label}</p>
          {payload.map((p, i) => (
            <p key={i} style={{ color: p.color }}>{p.name}: ₹{Number(p.value).toLocaleString('en-IN')}</p>
          ))}
        </div>
      )
    }
    return null
  }

  // 16 Metrics Layout Rows
  const kpis = {
    // Row 1
    revenue: {
      icon: <IndianRupee size={18} />,
      label: viewMode === 'day' ? "Revenue Today" : viewMode === 'month' ? "Revenue Month" : "Revenue Year",
      value: viewMode === 'day' ? today?.todayRevenue : viewMode === 'month' ? monthly?.totalRevenue : yearly?.totalRevenue,
      prefix: '₹',
      colorVar: 'var(--color-revenue)',
      change: viewMode === 'day' 
        ? calculateChangePct(today?.todayRevenue, today?.yesterdayRevenue) 
        : viewMode === 'month' 
        ? calculateChangePct(monthly?.totalRevenue, monthly?.lastMonthRevenue) 
        : calculateChangePct(yearly?.totalRevenue, yearly?.lastYearRevenue),
      sparkline: getSparklineData('revenue')
    },
    bills: {
      icon: <ShoppingCart size={18} />,
      label: viewMode === 'day' ? "Bills Today" : viewMode === 'month' ? "Bills Month" : "Bills Year",
      value: viewMode === 'day' ? today?.todayBills : viewMode === 'month' ? monthly?.totalBills : yearly?.totalBills,
      colorVar: 'var(--color-bills)',
      change: viewMode === 'day' ? calculateChangePct(today?.todayBills, today?.yesterdayBills) : null,
      sparkline: getSparklineData('bills')
    },
    collection: {
      icon: <TrendingUp size={18} />,
      label: viewMode === 'day' ? "Collection Today" : viewMode === 'month' ? "Collection Month" : "Collection Year",
      value: viewMode === 'day' ? today?.todayCollected : viewMode === 'month' ? monthly?.totalCollected : yearly?.totalCollected,
      prefix: '₹',
      colorVar: 'var(--color-collection)',
      change: viewMode === 'day' 
        ? calculateChangePct(today?.todayCollected, today?.yesterdayCollection) 
        : viewMode === 'month' 
        ? calculateChangePct(monthly?.totalCollected, monthly?.lastMonthCollection) 
        : calculateChangePct(yearly?.totalCollected, yearly?.lastYearCollection),
      sparkline: getSparklineData('collection')
    },
    pending: {
      icon: <TrendingDown size={18} />,
      label: "Pending (Udhar)",
      value: today?.totalOutstandingUdhar || 0,
      prefix: '₹',
      colorVar: 'var(--color-pending)',
      change: null,
      sparkline: getSparklineData('pending')
    },
    
    // Row 2
    cash: {
      icon: <IndianRupee size={18} />,
      label: "Cash Collection",
      value: viewMode === 'day' ? today?.todayCollectedCash : viewMode === 'month' ? monthly?.totalCollectedCash : yearly?.totalCollectedCash,
      prefix: '₹',
      colorVar: 'var(--color-cash)',
      change: viewMode === 'day' ? calculateChangePct(today?.todayCollectedCash, today?.yesterdayCash) : null,
      sparkline: getSparklineData('cash')
    },
    upi: {
      icon: <TrendingUp size={18} />,
      label: "UPI Collection",
      value: viewMode === 'day' ? today?.todayCollectedUpi : viewMode === 'month' ? monthly?.totalCollectedUpi : yearly?.totalCollectedUpi,
      prefix: '₹',
      colorVar: 'var(--color-upi)',
      change: viewMode === 'day' ? calculateChangePct(today?.todayCollectedUpi, today?.yesterdayUPI) : null,
      sparkline: getSparklineData('upi')
    },
    udharRecovery: {
      icon: <BookOpen size={18} />,
      label: "Udhar Recovery",
      value: viewMode === 'day' ? today?.todayCollectedUdhar : viewMode === 'month' ? monthly?.totalCollectedUdhar : yearly?.totalCollectedUdhar,
      prefix: '₹',
      colorVar: 'var(--color-udhar)',
      change: viewMode === 'day' ? calculateChangePct(today?.todayCollectedUdhar, today?.yesterdayUdharRecovery) : null,
      sparkline: getSparklineData('udharRecovery')
    },
    newUdhar: {
      icon: <TrendingDown size={18} />,
      label: "New Udhar Given",
      value: viewMode === 'day' ? today?.todayNewUdhar : viewMode === 'month' ? monthly?.totalNewUdhar : yearly?.totalNewUdhar,
      prefix: '₹',
      colorVar: 'var(--color-new-credit)',
      change: viewMode === 'day' 
        ? calculateChangePct(today?.todayNewUdhar, today?.yesterdayNewUdhar) 
        : viewMode === 'month' 
        ? calculateChangePct(monthly?.totalNewUdhar, monthly?.lastMonthNewUdhar) 
        : null,
      sparkline: getSparklineData('newUdhar')
    },

    // Row 3
    inventory: {
      icon: <Warehouse size={18} />,
      label: "Inventory Value",
      value: today?.totalInventoryValue || 0,
      prefix: '₹',
      colorVar: 'var(--color-inventory)',
      change: null,
      sparkline: getSparklineData('inventory')
    },
    codPending: {
      icon: <Truck size={18} />,
      label: "COD Pending Amount",
      value: today?.codPendingAmount || 0,
      prefix: '₹',
      colorVar: 'var(--color-cod)',
      change: null,
      subtitle: `${today?.codPendingBillsCount || 0} bills pending`,
      sparkline: getSparklineData('cod')
    },
    expenses: {
      icon: <Receipt size={18} />,
      label: viewMode === 'day' ? "Expenses Today" : viewMode === 'month' ? "Expenses Month" : "Expenses Year",
      value: viewMode === 'day' ? today?.todayExpenses : viewMode === 'month' ? monthly?.totalExpenses : yearly?.totalExpenses,
      prefix: '₹',
      colorVar: 'var(--color-expenses)',
      change: viewMode === 'month' ? calculateChangePct(monthly?.totalExpenses, monthly?.lastMonthExpenses) : null,
      sparkline: getSparklineData('expenses')
    },
    lowStock: {
      icon: <Package size={18} />,
      label: "Low Stock Products",
      value: today?.lowStockCount || 0,
      colorVar: 'var(--color-lowstock)',
      change: null,
      sparkline: getSparklineData('lowStock'),
      onClick: () => navigate('/products?filter=low_stock'),
      subtitle: 'Click to filter stock'
    },

    // Row 4
    netProfitPct: {
      icon: <Sparkles size={18} />,
      label: "Net Profit Margin",
      value: viewMode === 'day' ? today?.netProfitMarginPct : viewMode === 'month' ? monthly?.netProfitMarginPct : yearly?.netProfitMarginPct,
      suffix: '%',
      colorVar: 'var(--color-profit)',
      change: null,
      sparkline: getSparklineData('netProfit')
    },
    activeCustomers: {
      icon: <Users size={18} />,
      label: "Active Customers",
      value: getActiveCustomersCount(),
      colorVar: 'var(--color-health)',
      change: null,
      sparkline: getSparklineData('activeCustomers')
    },
    avgBillValue: {
      icon: <ShoppingCart size={18} />,
      label: "Avg Bill Value",
      value: viewMode === 'day' ? today?.avgBillValue : viewMode === 'month' ? monthly?.avgBillValue : yearly?.avgBillValue,
      prefix: '₹',
      colorVar: 'var(--color-health)',
      change: null,
      sparkline: getSparklineData('avgBillValue')
    },
    damageLoss: {
      icon: <AlertTriangle size={18} />,
      label: "Damage Loss MTD",
      value: today?.damageLossMTD || 0,
      prefix: '₹',
      colorVar: 'var(--color-damage)',
      change: null,
      sparkline: getSparklineData('damageLoss')
    },
    backupStatus: {
      icon: <Cloud size={18} />,
      label: "Drive Backup",
      value: today?.backupStale ? "Stale / Warning" : "Healthy",
      colorVar: today?.backupStale ? 'var(--color-danger)' : 'var(--color-success)',
      change: null,
      subtitle: today?.lastBackupTime ? `Last: ${formatRelativeTime(today.lastBackupTime)}` : 'Never backed up'
    }
  }

  const handleAlertClick = (type, value) => {
    if (Number(value) === 0) return
    switch (type) {
      case 'overdueUdhar':
        navigate('/receivables')
        break
      case 'creditLimit':
        navigate('/customers')
        break
      case 'lowStock':
        navigate('/products?filter=low_stock')
        break
      case 'expiring':
        navigate('/stock')
        break
      case 'inactive':
        navigate('/customers')
        break
      case 'pending':
        navigate('/deliveries')
        break
      default:
        break
    }
  }

  const PIE_COLORS_TABS = uiTheme === 'modern' ? MODERN_PIE_COLORS :
                           uiTheme === 'cyber' ? CYBER_PIE_COLORS :
                           uiTheme === 'neon' ? NEON_PIE_COLORS :
                           PIE_COLORS

  return (
    <div className="page-container">
      <style>{`
        @keyframes pulse {
          0% { transform: scale(0.95); opacity: 0.5; }
          50% { transform: scale(1.2); opacity: 1; }
          100% { transform: scale(0.95); opacity: 0.5; }
        }
        .pulse-dot {
          animation: pulse 2s infinite ease-in-out;
        }
        .tab-btn {
          padding: 6px 12px;
          font-size: 11px;
          border-radius: var(--radius-sm);
          font-weight: 600;
          transition: all 0.2s;
        }
      `}</style>

      {/* Header section */}
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div>
          <h2 className="page-title">Good {new Date().getHours() < 12 ? 'Morning' : new Date().getHours() < 17 ? 'Afternoon' : 'Evening'}, {user?.name || 'Mashkoor'} 👋</h2>
          <p className="page-subtitle">{new Date().toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })} • {new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}</p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
          <div className="time-range-toggle" style={{
            display: 'inline-flex',
            background: 'var(--color-surface-2)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-full)',
            padding: '3px',
            gap: '2px',
          }}>
            {['day', 'month', 'year'].map((mode) => (
              <button
                key={mode}
                onClick={() => setViewMode(mode)}
                className={`btn btn-sm ${viewMode === mode ? '' : 'btn-ghost'}`}
                style={{
                  borderRadius: 'var(--radius-full)',
                  padding: '6px 16px',
                  fontSize: 'var(--font-size-xs)',
                  fontWeight: 'var(--font-weight-semibold)',
                  height: 'auto',
                  textTransform: 'capitalize',
                  border: 'none',
                  background: viewMode === mode ? 'var(--color-accent)' : 'transparent',
                  color: viewMode === mode ? 'var(--color-text-inverse)' : 'var(--color-text-secondary)',
                  boxShadow: viewMode === mode ? 'var(--shadow-glow)' : 'none',
                }}
              >
                {mode === 'day' ? 'Daily' : mode === 'month' ? 'Monthly' : 'Yearly'}
              </button>
            ))}
          </div>

          <div className="page-actions" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            {isAdmin && (
              <button
                onClick={handleBackupToDrive}
                disabled={backupLoading}
                className="btn btn-secondary rounded-theme-md flex items-center gap-2"
                style={{ 
                  height: '38px', 
                  fontSize: 'var(--font-size-sm)', 
                  background: 'var(--color-surface-2)',
                  border: '1px solid var(--color-border)',
                  color: 'var(--color-text)'
                }}
              >
                {backupLoading ? <RefreshCw size={14} className="animate-spin" /> : <Cloud size={14} />}
                {backupLoading ? 'Backing up...' : 'Backup to Drive'}
              </button>
            )}
            {viewMode === 'day' && (
              <span style={{ 
                display: 'inline-flex', 
                alignItems: 'center', 
                gap: '6px', 
                fontSize: 'var(--font-size-xs)', 
                color: 'var(--color-success)', 
                background: 'var(--color-success-soft)', 
                padding: '6px 12px', 
                borderRadius: 'var(--radius-full)',
                fontWeight: 'var(--font-weight-medium)',
                border: '1px solid rgba(16, 185, 129, 0.2)'
              }}>
                <span className="pulse-dot" style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--color-success)', display: 'inline-block' }} />
                Live (Today)
              </span>
            )}
            
            {viewMode === 'month' && (
              <>
                <select
                  className="form-select rounded-theme-md"
                  value={selectedMonth}
                  onChange={(e) => setSelectedMonth(Number(e.target.value))}
                  style={{ width: '120px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', flexShrink: 0 }}
                >
                  {months.map((m, i) => (
                    <option key={i} value={i + 1}>{m}</option>
                  ))}
                </select>
                <select
                  className="form-select rounded-theme-md"
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(Number(e.target.value))}
                  style={{ width: '95px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', flexShrink: 0 }}
                >
                  {[2024, 2025, 2026].map((y) => (
                    <option key={y} value={y}>{y}</option>
                  ))}
                </select>
              </>
            )}

            {viewMode === 'year' && (
              <select
                className="form-select rounded-theme-md"
                value={selectedYear}
                onChange={(e) => setSelectedYear(Number(e.target.value))}
                style={{ width: '100px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', flexShrink: 0 }}
              >
                {[2024, 2025, 2026].map((y) => (
                  <option key={y} value={y}>{y}</option>
                ))}
              </select>
            )}
          </div>
        </div>
      </div>

      {/* KPI ROWS (Restructured 3-Row layout matching reference design) */}
      <div className="flex flex-col gap-6 mt-6">
        {/* Row 1: Today at a Glance */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2 px-1">
            <div className="w-1 h-3 rounded-full bg-[var(--color-revenue)]" />
            <span className="text-[10px] font-bold tracking-wider uppercase text-slate-450 dark:text-slate-400">
              Today at a Glance
            </span>
            <div className="h-[1px] bg-slate-200/60 dark:bg-slate-800/60 flex-1 ml-2" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <KPIStatCard {...kpis.revenue} />
            <KPIStatCard {...kpis.collection} />
            <KPIStatCard {...kpis.pending} />
            <KPIStatCard {...kpis.inventory} />
          </div>
        </div>

        {/* Row 2: Cash Flow & Health */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2 px-1">
            <div className="w-1 h-3 rounded-full bg-[var(--color-collection)]" />
            <span className="text-[10px] font-bold tracking-wider uppercase text-slate-450 dark:text-slate-400">
              Cash Flow & Health
            </span>
            <div className="h-[1px] bg-slate-200/60 dark:bg-slate-800/60 flex-1 ml-2" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <KPIStatCard {...kpis.udharRecovery} />
            <KPIStatCard {...kpis.newUdhar} />
            <KPIStatCard {...kpis.codPending} />
            <KPIStatCard {...kpis.netProfitPct} />
          </div>
        </div>

        {/* Row 3: Business Efficiency */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2 px-1">
            <div className="w-1 h-3 rounded-full bg-[var(--color-health)]" />
            <span className="text-[10px] font-bold tracking-wider uppercase text-slate-450 dark:text-slate-400">
              Business Efficiency
            </span>
            <div className="h-[1px] bg-slate-200/60 dark:bg-slate-800/60 flex-1 ml-2" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <KPIStatCard {...kpis.activeCustomers} />
            <KPIStatCard {...kpis.avgBillValue} />
            <KPIStatCard {...kpis.damageLoss} />
            <KPIStatCard {...kpis.backupStatus} />
          </div>
        </div>
      </div>

      {/* CHARTS CONTAINER (60/40 Split Layout Grid) */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 mt-8">
        {/* Left Side: 7-Day Trend multi-line Chart (60% width span 3) */}
        <div className="lg:col-span-3 card flex flex-col justify-between" style={{ minHeight: '340px' }}>
          <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-3)' }}>
            <span className="card-title text-sm">7-Day Business Sales & Collections Trend</span>
            <span className="text-[10px] text-slate-400 font-semibold uppercase">Chronological Spark trend</span>
          </div>
          {lineChartData.length > 0 ? (
            <div className="w-full flex-1">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={lineChartData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                  <XAxis dataKey="dayName" tick={{ fill: 'var(--color-text-secondary)', fontSize: 10 }} />
                  <YAxis tick={{ fill: 'var(--color-text-secondary)', fontSize: 10 }} />
                  <Tooltip content={customTooltip} />
                  <Line type="monotone" dataKey="revenue" name="Revenue" stroke="var(--color-revenue)" strokeWidth={2.5} dot={{ r: 3 }} activeDot={{ r: 5 }} />
                  <Line type="monotone" dataKey="collection" name="Collections" stroke="var(--color-collection)" strokeWidth={2} dot={{ r: 2 }} />
                  <Line type="monotone" dataKey="newUdhar" name="New Udhar" stroke="var(--color-new-credit)" strokeWidth={1.5} strokeDasharray="4 4" dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-xs text-slate-500">No trend data available</p>
            </div>
          )}
        </div>

        {/* Right Side: Smart Breakdown Widget Tab (40% width span 2) */}
        <div className="lg:col-span-2 card flex flex-col" style={{ minHeight: '340px' }}>
          <div className="card-header flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2" style={{ marginBottom: 'var(--space-2)' }}>
            <span className="card-title text-sm">Breakdowns & Analytics</span>
            <div className="flex bg-slate-100 dark:bg-slate-800 rounded p-0.5 border border-slate-200 dark:border-slate-700">
              <button 
                onClick={() => setChartTab('expenses')}
                className={`tab-btn ${chartTab === 'expenses' ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm' : 'text-slate-500 dark:text-slate-400'}`}
              >
                Expenses
              </button>
              <button 
                onClick={() => setChartTab('customers')}
                className={`tab-btn ${chartTab === 'customers' ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm' : 'text-slate-500 dark:text-slate-400'}`}
              >
                Customers
              </button>
              <button 
                onClick={() => setChartTab('stock')}
                className={`tab-btn ${chartTab === 'stock' ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm' : 'text-slate-500 dark:text-slate-400'}`}
              >
                Operations
              </button>
            </div>
          </div>

          <div className="flex-1 flex flex-col justify-center">
            {chartTab === 'expenses' && (
              expenseChartData.length > 0 ? (
                <div className="flex flex-col items-center justify-between h-full pt-2">
                  <div className="w-full flex justify-center">
                    <ResponsiveContainer width="100%" height={160}>
                      <PieChart>
                        <Pie
                          data={expenseChartData}
                          innerRadius={45}
                          outerRadius={70}
                          paddingAngle={3}
                          dataKey="value"
                        >
                          {expenseChartData.map((_, idx) => (
                            <Cell key={idx} fill={PIE_COLORS_TABS[idx % PIE_COLORS_TABS.length]} />
                          ))}
                        </Pie>
                        <Tooltip formatter={(val) => `₹${Number(val).toLocaleString('en-IN')}`} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  {/* Legend scrollable list */}
                  <div className="w-full flex flex-wrap gap-x-3 gap-y-1.5 justify-center max-h-[75px] overflow-y-auto mt-2 px-1">
                    {expenseChartData.map((item, idx) => (
                      <div key={item.name} className="flex items-center gap-1 text-[10px] text-slate-600 dark:text-slate-400">
                        <span className="w-2.5 h-2.5 rounded-sm flex-shrink-0" style={{ background: PIE_COLORS_TABS[idx % PIE_COLORS_TABS.length] }} />
                        <span className="truncate max-w-[80px] capitalize">{item.name}</span>
                        <span className="font-semibold text-slate-800 dark:text-slate-200">₹{item.value}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-center py-10">
                  <p className="text-xs text-slate-500">No operational expenses recorded for this period</p>
                </div>
              )
            )}

            {chartTab === 'customers' && (
              <div className="flex flex-col h-full justify-between pt-2">
                <div className="w-full flex justify-center">
                  <ResponsiveContainer width="100%" height={150}>
                    <PieChart>
                      <Pie
                        data={customerHealthData}
                        innerRadius={45}
                        outerRadius={65}
                        paddingAngle={3}
                        dataKey="value"
                      >
                        <Cell fill="var(--color-revenue)" />
                        <Cell fill="var(--color-upi)" />
                        <Cell fill="var(--color-pending)" />
                      </Pie>
                      <Tooltip />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex flex-col gap-2 px-2 mt-2">
                  <div className="flex items-center justify-between text-xs border-b border-slate-100 dark:border-slate-800/40 pb-1.5">
                    <span className="flex items-center gap-2 text-slate-600 dark:text-slate-400">
                      <span className="w-2.5 h-2.5 rounded-full bg-[var(--color-revenue)]" />
                      Active (This Period)
                    </span>
                    <span className="font-bold text-slate-800 dark:text-slate-100">{activeSegmentCustomers}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs border-b border-slate-100 dark:border-slate-800/40 pb-1.5">
                    <span className="flex items-center gap-2 text-slate-600 dark:text-slate-400">
                      <span className="w-2.5 h-2.5 rounded-full bg-[var(--color-upi)]" />
                      Inactive (15d+)
                    </span>
                    <span className="font-bold text-slate-800 dark:text-slate-100">{inactiveSegmentCustomers}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 text-slate-600 dark:text-slate-400">
                      <span className="w-2.5 h-2.5 rounded-full bg-[var(--color-pending)]" />
                      NPA / Overdue Accounts
                    </span>
                    <span className="font-bold text-rose-500">{npaSegmentCustomers}</span>
                  </div>
                </div>
              </div>
            )}

            {chartTab === 'stock' && (
              <div className="flex flex-col h-full justify-between py-2 px-2">
                <span className="text-[11px] text-slate-450 dark:text-slate-400 italic mb-2">Shop Operations alerts summary:</span>
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between p-2 bg-slate-50 dark:bg-slate-800/40 rounded border border-slate-100 dark:border-slate-800/60">
                    <div className="flex items-center gap-2.5">
                      <Package size={16} className="text-amber-500" />
                      <span className="text-xs font-semibold text-slate-700 dark:text-slate-200">Low Stock Lines</span>
                    </div>
                    <span className="text-xs font-bold px-2 py-0.5 bg-amber-500/10 text-amber-500 rounded">{today?.lowStockCount || 0} items</span>
                  </div>
                  
                  <div className="flex items-center justify-between p-2 bg-slate-50 dark:bg-slate-800/40 rounded border border-slate-100 dark:border-slate-800/60">
                    <div className="flex items-center gap-2.5">
                      <AlertTriangle size={16} className="text-rose-500" />
                      <span className="text-xs font-semibold text-slate-700 dark:text-slate-200">Expiring Batches (30d)</span>
                    </div>
                    <span className="text-xs font-bold px-2 py-0.5 bg-rose-500/10 text-rose-500 rounded">{today?.expiringBatchesCount || 0} batches</span>
                  </div>

                  <div className="flex items-center justify-between p-2 bg-slate-50 dark:bg-slate-800/40 rounded border border-slate-100 dark:border-slate-800/60">
                    <div className="flex items-center gap-2.5">
                      <Cloud size={16} className={today?.backupStale ? 'text-rose-500' : 'text-emerald-500'} />
                      <span className="text-xs font-semibold text-slate-700 dark:text-slate-200">Drive Cloud Backup</span>
                    </div>
                    <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded ${
                      today?.backupStale ? 'bg-rose-500/10 text-rose-500' : 'bg-emerald-500/10 text-emerald-500'
                    }`}>
                      {today?.backupStale ? 'Stale / Warning' : 'Healthy'}
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* LOWER WIDGET AREA (3-Column Layout Grid) */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-8">
        {/* Widget 1: Alerts Panel (Interactive Click redirects) */}
        <div className="lg:col-span-1 card flex flex-col justify-between" style={{ padding: 'var(--space-4)' }}>
          <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-3)', paddingBottom: 'var(--space-2)' }}>
            <span className="card-title text-xs tracking-wider uppercase font-semibold">Interactive Alert Center</span>
            <div className="flex bg-slate-100 dark:bg-slate-800 rounded p-0.5 border border-slate-200 dark:border-slate-700">
              <button 
                onClick={() => setAlertScope('urgent')}
                className={`tab-btn ${alertScope === 'urgent' ? 'bg-white dark:bg-slate-700 shadow-sm text-slate-800 dark:text-white' : 'text-slate-500'}`}
              >
                Urgent
              </button>
              <button 
                onClick={() => setAlertScope('expanded')}
                className={`tab-btn ${alertScope === 'expanded' ? 'bg-white dark:bg-slate-700 shadow-sm text-slate-800 dark:text-white' : 'text-slate-500'}`}
              >
                Monthly
              </button>
            </div>
          </div>

          <div className="flex flex-col gap-2 mt-1">
            {[
              { type: 'overdueUdhar', label: 'Overdue Udhar (>7 Days)', value: today?.overdueUdharCount || 0, color: 'var(--color-pending)', icon: <AlertTriangle size={14} /> },
              { type: 'creditLimit', label: 'Credit Limit Violations', value: today?.creditLimitExceededCount || 0, color: 'var(--color-new-credit)', icon: <Users size={14} /> },
              { type: 'lowStock', label: 'Products Low in Stock', value: lowStockList.length, color: 'var(--color-lowstock)', icon: <Package size={14} /> },
              { type: 'expiring', label: alertScope === 'urgent' ? 'Expiring in 7 Days' : 'Expiring in 30 Days', value: expiringList.length, color: 'var(--color-damage)', icon: <AlertTriangle size={14} /> },
              { type: 'inactive', label: alertScope === 'urgent' ? 'Critical Inactive' : 'Dormant Accounts', value: inactiveList.length, color: 'var(--color-health)', icon: <Users size={14} /> },
              { type: 'pending', label: 'Pending Delivery Dispatches', value: pendingDeliveriesList.length, color: 'var(--color-cod)', icon: <Truck size={14} /> }
            ].map((alert) => {
              const isZero = alert.value === 0
              return (
                <div
                  key={alert.type}
                  onClick={() => handleAlertClick(alert.type, alert.value)}
                  className={`flex items-center justify-between py-2 px-3 rounded-theme-sm transition-all border border-slate-100 dark:border-slate-800/40 ${
                    isZero 
                      ? 'opacity-40 cursor-default bg-slate-50 dark:bg-slate-800/20' 
                      : 'cursor-pointer hover:border-slate-350 dark:hover:border-slate-700 bg-white dark:bg-slate-800/40 hover:bg-slate-50 dark:hover:bg-slate-750'
                  }`}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span style={{ color: isZero ? 'var(--color-text-muted)' : alert.color }}>{alert.icon}</span>
                    <span className="text-xs font-medium text-slate-700 dark:text-slate-300 truncate">{alert.label}</span>
                  </div>
                  <span className={`text-xs font-bold px-1.5 py-0.5 rounded ${
                    isZero ? 'bg-slate-100 dark:bg-slate-800 text-slate-400' : 'bg-slate-100 dark:bg-slate-800 text-slate-850 dark:text-slate-100'
                  }`}>
                    {alert.value}
                  </span>
                </div>
              )
            })}
          </div>
        </div>

        {/* Widget 2: Compact Activity Logger */}
        <div className="lg:col-span-1">
          <LiveActivityWidget
            recentBills={recentBills}
            lowStockList={lowStockList}
            pendingDeliveries={pendingDeliveriesList}
            livePayments={livePayments}
            liveDeliveries={liveDeliveries}
          />
        </div>

        {/* Widget 3: Lari AI Copilot */}
        <div className="lg:col-span-1">
          {aiEnabled && (isAdmin || isManager) ? (
            <AiCopilotWidget year={selectedYear} month={selectedMonth} />
          ) : (
            <div className="card ai-insight-card flex flex-col justify-center items-center p-6 text-center" style={{ minHeight: '380px' }}>
              <Brain size={36} className="text-violet-400 mb-3 animate-pulse" />
              <span className="card-title text-sm">AI Copilot Status Offline</span>
              <p style={{ color: 'var(--color-text-muted)', fontSize: '11px', marginTop: 'var(--space-2)' }}>
                {!aiEnabled ? "AI Copilot feature is currently disabled in system settings." : "AI Advisor is restricted to Administrator and Manager roles."}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Outstanding Overdue Popup Modal */}
      <Modal
        isOpen={showOverdueModal}
        onClose={() => setShowOverdueModal(false)}
        title="⚠️ Outstanding Overdue Udhar Alerts"
        wide
      >
        <div className="flex flex-col gap-4">
          <p className="text-xs text-slate-600 dark:text-slate-400">
            Following customers have pending bills that have exceeded **7 days** since creation. Please follow up on collections.
          </p>
          <div style={{ maxHeight: '320px', overflowY: 'auto' }} className="flex flex-col gap-2 pr-1">
            {(today?.overdueUdharAlerts || []).map((item, idx) => (
              <div
                key={idx}
                className="flex justify-between items-center p-3 bg-slate-50 dark:bg-slate-850 border border-slate-200 dark:border-slate-700/60 rounded-theme-md border-l-4 border-l-rose-500"
              >
                <div>
                  <div className="font-bold text-sm text-slate-800 dark:text-slate-100">
                    {item.customerName}
                  </div>
                  <div className="text-[10px] text-slate-500 dark:text-slate-400 mt-1">
                    Shop: {item.shopName || 'N/A'} • Oldest Bill: <span className="text-rose-500 font-semibold">{item.overdueDays} days ago</span>
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-base font-bold text-rose-500">
                    ₹{Number(item.totalOverdueAmount || 0).toLocaleString('en-IN')}
                  </div>
                  <div className="text-[9px] text-slate-400 dark:text-slate-500 mt-1">
                    Overdue Udhar
                  </div>
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-end mt-2">
            <button className="btn btn-primary btn-sm" onClick={() => setShowOverdueModal(false)}>
              Understood
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
