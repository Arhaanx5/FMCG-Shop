import { useState, useEffect, useRef } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  IndianRupee, ShoppingCart, TrendingUp, TrendingDown,
  AlertTriangle, Package, Users, Truck, Sparkles, Brain, Lightbulb, BookOpen, Send, RefreshCw
} from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import api from '../services/api'
import StatCard from '../components/StatCard'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const PIE_COLORS = ['#f59e0b', '#3b82f6', '#10b981', '#ef4444', '#8b5cf6', '#ec4899']
const MODERN_PIE_COLORS = ['#8b5cf6', '#a78bfa', '#ec4899', '#f97316', '#f59e0b', '#10b981']
const CYBER_PIE_COLORS = ['#00d2ff', '#3b82f6', '#60a5fa', '#0a80df', '#38bdf8', '#1e3a8a']
const NEON_PIE_COLORS = ['#00ffcc', '#d946ef', '#10b981', '#f43f5e', '#a855f7', '#06b6d4']

function LiveActivityWidget({ recentBills = [], lowStockList = [], pendingDeliveries = [] }) {
  const activities = []

  recentBills.slice(0, 3).forEach((bill, idx) => {
    const displayNum = bill.billNumber || (bill.id ? bill.id.slice(0, 8) : (idx + 101));
    activities.push({
      id: `bill-${bill.id || idx}`,
      type: 'bill',
      title: `Invoice #${displayNum} generated`,
      subtitle: `${bill.customerName || 'Retailer'} • ₹${Number(bill.grandTotal || 0).toLocaleString('en-IN')}`,
      time: idx === 0 ? 'Just now' : idx === 1 ? '15m ago' : '1h ago',
      color: '#8b5cf6',
      icon: <ShoppingCart size={14} />,
    })
  })

  lowStockList.slice(0, 2).forEach((item, idx) => {
    activities.push({
      id: `stock-${item.productId || idx}`,
      type: 'stock',
      title: `Low stock: ${item.productName}`,
      subtitle: `${item.currentStock} remaining (min: ${item.threshold})`,
      time: idx === 0 ? '2h ago' : '5h ago',
      color: '#ef4444',
      icon: <Package size={14} />,
    })
  })

  pendingDeliveries.slice(0, 2).forEach((item, idx) => {
    activities.push({
      id: `delivery-${item.id || idx}`,
      type: 'delivery',
      title: `Delivery Dispatch Scheduled`,
      subtitle: `Route: ${item.areaName || 'Main Route'}`,
      time: idx === 0 ? '3h ago' : '6h ago',
      color: '#f97316',
      icon: <Truck size={14} />,
    })
  })

  return (
    <div className="card card-lift">
      <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-4)', paddingBottom: 'var(--space-3)' }}>
        <span className="card-title">Live Activity Feed</span>
        <span className="pulse-dot" style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', display: 'inline-block' }} />
      </div>
      <div className="flex flex-col gap-4 mt-2">
        {activities.length === 0 ? (
          <p className="text-muted text-xs text-center py-4">No recent activity detected.</p>
        ) : (
          activities.slice(0, 5).map((act) => (
            <div key={act.id} className="flex gap-3 items-start text-xs border-b border-slate-100 dark:border-slate-800 pb-3 last:border-0 last:pb-0">
              <div 
                className="w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 text-white"
                style={{ background: act.color }}
              >
                {act.icon}
              </div>
              <div className="flex-1 min-w-0">
                <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text)' }}>
                  {act.title}
                </div>
                <div style={{ color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                  {act.subtitle}
                </div>
              </div>
              <div style={{ color: 'var(--color-text-muted)', fontSize: '10px', whiteSpace: 'nowrap' }}>
                {act.time}
              </div>
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
  const [chatInput, setChatInput] = useState('')
  const [chatMessages, setChatMessages] = useState([
    { role: 'assistant', content: 'Hello! Main aapka business copilot hoon. Poochein kuch bhi, jaise "low stock products kya hain?" ya "is month ka revenue analysis".' }
  ])
  const [chatLoading, setChatLoading] = useState(false)
  const messagesEndRef = useRef(null)
  const toast = useToast()

  const fetchInsights = async () => {
    setInsightsLoading(true)
    try {
      const res = await api.get(`/dashboard/ai/insights?year=${year}&month=${month}`)
      if (res.data?.data?.insights) {
        setInsights(res.data.data.insights)
      } else {
        setInsights('No insights generated.')
      }
    } catch (err) {
      console.error(err)
      toast.error('Failed to load AI Insights')
    } finally {
      setInsightsLoading(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'insights' && !insights) {
      fetchInsights()
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
      setChatMessages(prev => [...prev, { role: 'assistant', content: 'Error connecting to AI assistant.' }])
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
              <li key={idx} className="ml-4 list-disc text-xs text-slate-300">
                {parts.map((p, i) => i % 2 === 1 ? <strong key={i} className="text-white">{p}</strong> : p)}
              </li>
            )
          }
          if (line.trim()) {
            const parts = line.split('**')
            return (
              <p key={idx} className="text-xs text-slate-300">
                {parts.map((p, i) => i % 2 === 1 ? <strong key={i} className="text-white">{p}</strong> : p)}
              </p>
            )
          }
          return <div key={idx} className="h-1" />
        })}
      </div>
    )
  }

  return (
    <div className="card card-lift ai-insight-card relative overflow-hidden" style={{ minHeight: '380px', display: 'flex', flexDirection: 'column' }}>
      <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-3)', paddingBottom: 'var(--space-2)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <Brain size={20} style={{ color: '#c084fc' }} />
          <span className="card-title">Lari AI Copilot</span>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <button
            onClick={() => setActiveTab('insights')}
            className={`btn btn-sm ${activeTab === 'insights' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '4px 10px', fontSize: '10px', height: 'auto' }}
          >
            Insights
          </button>
          <button
            onClick={() => setActiveTab('chat')}
            className={`btn btn-sm ${activeTab === 'chat' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '4px 10px', fontSize: '10px', height: 'auto' }}
          >
            Chat Advisor
          </button>
        </div>
      </div>

      {activeTab === 'insights' ? (
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, gap: 'var(--space-3)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--font-size-xs)', color: 'rgba(248, 250, 252, 0.5)' }}>
            <span>Monthly business intelligence suggestions</span>
            <button 
              onClick={fetchInsights} 
              disabled={insightsLoading}
              className="btn btn-sm btn-ghost btn-icon" 
              style={{ width: '24px', height: '24px' }}
            >
              <RefreshCw size={12} className={insightsLoading ? 'animate-spin' : ''} />
            </button>
          </div>
          
          <div style={{ maxHeight: '280px', overflowY: 'auto', flex: 1 }} className="pr-1 text-xs">
            {insightsLoading ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', padding: 'var(--space-4) 0' }}>
                <div style={{ height: '16px', background: '#334155', borderRadius: 'var(--radius-sm)', width: '75%' }} className="animate-pulse"></div>
                <div style={{ height: '12px', background: '#334155', borderRadius: 'var(--radius-sm)', width: '85%' }} className="animate-pulse"></div>
                <div style={{ height: '12px', background: '#334155', borderRadius: 'var(--radius-sm)', width: '65%' }} className="animate-pulse"></div>
                <div style={{ height: '12px', background: '#334155', borderRadius: 'var(--radius-sm)', width: '90%' }} className="animate-pulse"></div>
              </div>
            ) : insights ? (
              renderInsightsMarkdown(insights)
            ) : (
              <p style={{ color: 'rgba(248, 250, 252, 0.5)', textAlign: 'center', padding: 'var(--space-8) 0' }}>Click refresh to load insights.</p>
            )}
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', height: '300px', justifyContent: 'space-between' }}>
          <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }} className="pr-1 text-xs mb-2">
            {chatMessages.map((msg, i) => (
              <div 
                key={i} 
                style={{
                  alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
                  background: msg.role === 'user' ? 'var(--color-accent, #8b5cf6)' : 'rgba(255,255,255,0.1)',
                  color: msg.role === 'user' ? '#ffffff' : '#f1f5f9',
                  padding: 'var(--space-2) var(--space-3)',
                  borderRadius: 'var(--radius-md)',
                  maxWidth: '85%'
                }}
              >
                {msg.content}
              </div>
            ))}
            {chatLoading && (
              <div style={{ alignSelf: 'flex-start', background: 'rgba(255,255,255,0.1)', color: '#f1f5f9', padding: 'var(--space-2) var(--space-3)', borderRadius: 'var(--radius-md)' }}>
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

export default function Dashboard() {
  const navigate = useNavigate()
  const [uiTheme] = useOutletContext()
  const { aiEnabled, isAdmin, isManager } = useAuth()
  const [today, setToday] = useState(null)
  const [monthly, setMonthly] = useState(null)
  const [yearly, setYearly] = useState(null)
  const [viewMode, setViewMode] = useState('day') // 'day' | 'month' | 'year'
  const [loading, setLoading] = useState(true)
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1)
  const [alertScope, setAlertScope] = useState('urgent')
  const toast = useToast()

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
    } catch (err) {
      toast.error('Failed to load dashboard data')
    } finally {
      setLoading(false)
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

  // Filter alerts based on active scope (Urgent vs Expanded/Month)
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

  // Estimated inventory value at risk for expiring batches (assuming typical cost of ₹120 per unit)
  const expiringValue = allExpiring.reduce((acc, curr) => acc + (curr.stockCount || 0) * 120, 0)

  // Prepare expense chart data
  const activeExpenseSource = viewMode === 'year' ? yearly : monthly
  const expenseChartData = activeExpenseSource?.expensesByCategory
    ? Object.entries(activeExpenseSource.expensesByCategory).map(([name, value]) => ({ name, value: Number(value) }))
    : []

  // Prepare top products chart data
  const activeProductSource = viewMode === 'year' ? yearly : monthly
  const topProductsData = activeProductSource?.topProductsByQty
    ? Object.entries(activeProductSource.topProductsByQty)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 6)
        .map(([name, qty]) => ({ name: name.length > 15 ? name.slice(0, 15) + '...' : name, qty }))
    : []

  // Revenue vs Expenses bar data
  const revenueExpenseData = viewMode === 'day' 
    ? [
        { name: 'Revenue', amount: Number(today?.todayRevenue || 0) },
        { name: 'Collected', amount: Number(today?.todayCollected || 0) },
        { name: 'Pending', amount: Number(today?.todayPending || 0) },
      ]
    : viewMode === 'month'
    ? [
        { name: 'Revenue', amount: Number(monthly?.totalRevenue || 0) },
        { name: 'Collected', amount: Number(monthly?.totalCollected || 0) },
        { name: 'Expenses', amount: Number(monthly?.totalExpenses || 0) },
        { name: 'Net Profit', amount: Number(monthly?.netProfit || 0) },
      ]
    : [
        { name: 'Revenue', amount: Number(yearly?.totalRevenue || 0) },
        { name: 'Collected', amount: Number(yearly?.totalCollected || 0) },
        { name: 'Expenses', amount: Number(yearly?.totalExpenses || 0) },
        { name: 'Net Profit', amount: Number(yearly?.netProfit || 0) },
      ];

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
            <p key={i} style={{ color: p.color }}>₹{Number(p.value).toLocaleString('en-IN')}</p>
          ))}
        </div>
      )
    }
    return null
  }

  const getKpiValues = () => {
    switch (viewMode) {
      case 'year':
        return {
          revenue: Number(yearly?.totalRevenue || 0),
          revenueLabel: "Yearly Revenue",
          bills: Number(yearly?.totalBills || 0),
          billsLabel: "Yearly Bills",
          collection: Number(yearly?.totalCollected || 0),
          collectionLabel: "Yearly Collection",
          collectionDesc: `Cash: ₹${Number(yearly?.totalCollectedCash || 0).toLocaleString('en-IN')} | UPI: ₹${Number(yearly?.totalCollectedUpi || 0).toLocaleString('en-IN')} | Udhar Recovery: ₹${Number(yearly?.totalCollectedUdhar || 0).toLocaleString('en-IN')}`,
          collectedCash: Number(yearly?.totalCollectedCash || 0),
          collectedUpi: Number(yearly?.totalCollectedUpi || 0),
          collectedUdhar: Number(yearly?.totalCollectedUdhar || 0),
          pending: Number(today?.todayPending || 0),
          pendingLabel: "Total Pending (Udhaar)",
        }
      case 'month':
        return {
          revenue: Number(monthly?.totalRevenue || 0),
          revenueLabel: "Monthly Revenue",
          bills: Number(monthly?.totalBills || 0),
          billsLabel: "Monthly Bills",
          collection: Number(monthly?.totalCollected || 0),
          collectionLabel: "Monthly Collection",
          collectionDesc: `Cash: ₹${Number(monthly?.totalCollectedCash || 0).toLocaleString('en-IN')} | UPI: ₹${Number(monthly?.totalCollectedUpi || 0).toLocaleString('en-IN')} | Udhar Recovery: ₹${Number(monthly?.totalCollectedUdhar || 0).toLocaleString('en-IN')}`,
          collectedCash: Number(monthly?.totalCollectedCash || 0),
          collectedUpi: Number(monthly?.totalCollectedUpi || 0),
          collectedUdhar: Number(monthly?.totalCollectedUdhar || 0),
          pending: Number(today?.todayPending || 0),
          pendingLabel: "Total Pending (Udhaar)",
        }
      case 'day':
      default:
        return {
          revenue: Number(today?.todayRevenue || 0),
          revenueLabel: "Today's Revenue",
          bills: Number(today?.todayBills || 0),
          billsLabel: "Today's Bills",
          collection: Number(today?.todayCollected || 0),
          collectionLabel: "Today's Collection",
          collectionDesc: `Cash: ₹${Number(today?.todayCollectedCash || 0).toLocaleString('en-IN')} | UPI: ₹${Number(today?.todayCollectedUpi || 0).toLocaleString('en-IN')} | Udhar Recovery: ₹${Number(today?.todayCollectedUdhar || 0).toLocaleString('en-IN')}`,
          collectedCash: Number(today?.todayCollectedCash || 0),
          collectedUpi: Number(today?.todayCollectedUpi || 0),
          collectedUdhar: Number(today?.todayCollectedUdhar || 0),
          pending: Number(today?.todayPending || 0),
          pendingLabel: "Total Pending (Udhaar)",
        }
    }
  }

  const kpis = getKpiValues()

  const getBottomCards = () => {
    switch (viewMode) {
      case 'year':
        return [
          { label: "Year Revenue", value: Number(yearly?.totalRevenue || 0), color: 'var(--color-text)' },
          { label: "Year Expenses", value: Number(yearly?.totalExpenses || 0), color: 'var(--color-danger)' },
          { label: "Net Profit", value: Number(yearly?.netProfit || 0), color: Number(yearly?.netProfit || 0) >= 0 ? 'var(--color-success)' : 'var(--color-danger)' },
          { label: "Damage Loss", value: Number(yearly?.totalDamageLoss || 0), color: 'var(--color-warning)' },
        ]
      case 'month':
        return [
          { label: "Month Revenue", value: Number(monthly?.totalRevenue || 0), color: 'var(--color-text)' },
          { label: "Month Expenses", value: Number(monthly?.totalExpenses || 0), color: 'var(--color-danger)' },
          { label: "Net Profit", value: Number(monthly?.netProfit || 0), color: Number(monthly?.netProfit || 0) >= 0 ? 'var(--color-success)' : 'var(--color-danger)' },
          { label: "Damage Loss", value: Number(monthly?.totalDamageLoss || 0), color: 'var(--color-warning)' },
        ]
      case 'day':
      default:
        return [
          { label: "Month Revenue (YTD)", value: Number(today?.monthRevenue || 0), color: 'var(--color-text)' },
          { label: "Month Expenses", value: Number(today?.monthExpenses || 0), color: 'var(--color-danger)' },
          { label: "Month Net Profit", value: Number(today?.monthNetProfit || 0), color: Number(today?.monthNetProfit || 0) >= 0 ? 'var(--color-success)' : 'var(--color-danger)' },
          { label: "Month Damage Loss", value: Number(monthly?.totalDamageLoss || 0), color: 'var(--color-warning)' },
        ]
    }
  }

  const bottomCards = getBottomCards()

  const MODERN_COLORS = ['#8b5cf6', '#d946ef', '#f97316', '#ef4444']
  const CLASSIC_COLORS = ['#f59e0b', '#3b82f6', '#10b981', '#ef4444']
  const CYBER_COLORS = ['#3b82f6', '#00d2ff', '#60a5fa', '#ef4444']
  const NEON_COLORS = ['#00ffcc', '#d946ef', '#10b981', '#f43f5e']

  const activeColors = uiTheme === 'modern' ? MODERN_COLORS :
                       uiTheme === 'cyber' ? CYBER_COLORS :
                       uiTheme === 'neon' ? NEON_COLORS :
                       CLASSIC_COLORS

  const activePieColors = uiTheme === 'modern' ? MODERN_PIE_COLORS :
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
      `}</style>

      {/* Header */}
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div>
          <h2 className="page-title">Good {new Date().getHours() < 12 ? 'Morning' : new Date().getHours() < 17 ? 'Afternoon' : 'Evening'} 👋</h2>
          <p className="page-subtitle">Here's what's happening with your business today.</p>
        </div>

        {/* View Switcher Controls */}
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
                  style={{ width: '130px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', flexShrink: 0 }}
                >
                  {months.map((m, i) => (
                    <option key={i} value={i + 1}>{m}</option>
                  ))}
                </select>
                <select
                  className="form-select rounded-theme-md"
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(Number(e.target.value))}
                  style={{ width: '105px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', flexShrink: 0 }}
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
                style={{ width: '110px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', flexShrink: 0 }}
              >
                {[2024, 2025, 2026].map((y) => (
                  <option key={y} value={y}>{y}</option>
                ))}
              </select>
            )}
          </div>
        </div>
      </div>

      {uiTheme !== 'classic' ? (
        /* MODERN MULTI-COLUMN SaaS LAYOUT */
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mt-6">
          {/* Main Workspace (Col Span 2) */}
          <div className="lg:col-span-2 flex flex-col gap-8">
            {/* KPI Metrics */}
            <div className="kpi-grid">
              <StatCard
                icon={<IndianRupee size={24} />}
                label={kpis.revenueLabel}
                value={kpis.revenue}
                prefix="₹"
                color="var(--color-accent)"
                delay={0}
              />
              <StatCard
                icon={<ShoppingCart size={24} />}
                label={kpis.billsLabel}
                value={kpis.bills}
                color="var(--color-info)"
                delay={1}
              />
              <StatCard
                icon={<TrendingUp size={24} />}
                label={kpis.collectionLabel}
                value={kpis.collection}
                prefix="₹"
                color="var(--color-success)"
                delay={2}
                description={kpis.collectionDesc}
              />
              <StatCard
                icon={<TrendingDown size={24} />}
                label={kpis.pendingLabel}
                value={kpis.pending}
                prefix="₹"
                color="var(--color-danger)"
                delay={3}
              />
            </div>

            {/* Breakdown Sub-KPI Metrics */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4" style={{ marginTop: '-16px' }}>
              <StatCard
                icon={<IndianRupee size={20} />}
                label="Cash Collection"
                value={kpis.collectedCash}
                prefix="₹"
                color="var(--color-success)"
                delay={4}
              />
              <StatCard
                icon={<TrendingUp size={20} />}
                label="UPI Collection"
                value={kpis.collectedUpi}
                prefix="₹"
                color="var(--color-info)"
                delay={5}
              />
              <StatCard
                icon={<BookOpen size={20} />}
                label="Udhar Recovery"
                value={kpis.collectedUdhar}
                prefix="₹"
                color="var(--color-warning)"
                delay={6}
              />
            </div>

            {/* Charts Section */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Revenue vs Expenses Bar */}
              <motion.div
                className="card card-lift"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.3 }}
              >
                <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-4)' }}>
                  <span className="card-title">
                    {viewMode === 'day' ? 'Daily Overview' : viewMode === 'month' ? 'Monthly Overview' : 'Yearly Overview'}
                  </span>
                </div>
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={revenueExpenseData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                    <XAxis dataKey="name" tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
                    <YAxis tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
                    <Tooltip content={customTooltip} />
                    <Bar dataKey="amount" radius={[6, 6, 0, 0]}>
                      {revenueExpenseData.map((entry, index) => (
                        <Cell
                          key={index}
                          fill={activeColors[index % activeColors.length]}
                        />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </motion.div>

              {/* Expense Breakdown Pie */}
              <motion.div
                className="card card-lift"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.4 }}
              >
                <div className="card-header" style={{ marginBottom: 'var(--space-4)' }}>
                  <span className="card-title">Expense Breakdown ({viewMode === 'year' ? 'Yearly' : 'Monthly'})</span>
                </div>
                {expenseChartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={250}>
                    <PieChart>
                      <Pie
                        data={expenseChartData}
                        innerRadius={60}
                        outerRadius={90}
                        paddingAngle={4}
                        dataKey="value"
                      >
                        {expenseChartData.map((_, idx) => (
                          <Cell key={idx} fill={activePieColors[idx % activePieColors.length]} />
                        ))}
                      </Pie>
                      <Tooltip
                        formatter={(value) => `₹${Number(value).toLocaleString('en-IN')}`}
                        contentStyle={{
                          background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
                          borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)',
                        }}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
                    <p className="text-muted">No expense data for this period</p>
                  </div>
                )}
                {/* Legend */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
                  {expenseChartData.map((item, idx) => (
                    <div key={item.name} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', fontSize: 'var(--font-size-xs)' }}>
                      <div style={{ width: 8, height: 8, borderRadius: 2, background: activePieColors[idx % activePieColors.length] }} />
                      <span className="text-secondary">{item.name}</span>
                    </div>
                  ))}
                </div>
              </motion.div>
            </div>

            {/* Top Products */}
            <motion.div
              className="card card-lift"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5 }}
            >
              <div className="card-header" style={{ marginBottom: 'var(--space-4)' }}>
                <span className="card-title">Top Products ({viewMode === 'year' ? 'Yearly' : 'Monthly'})</span>
              </div>
              {topProductsData.length > 0 ? (
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={topProductsData} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                    <XAxis type="number" tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
                    <YAxis dataKey="name" type="category" tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }} width={120} />
                    <Tooltip
                      contentStyle={{
                        background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)',
                      }}
                    />
                    <Bar
                      dataKey="qty"
                      fill={
                        uiTheme === 'modern' ? '#8b5cf6' :
                        uiTheme === 'cyber' ? '#00d2ff' :
                        uiTheme === 'neon' ? '#00ffcc' :
                        '#f59e0b'
                      }
                      radius={[0, 6, 6, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
                  <p className="text-muted">No product data for this period</p>
                </div>
              )}
            </motion.div>

            {/* Summary Cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {bottomCards.map((card, idx) => (
                <motion.div 
                  key={idx} 
                  className="stat-card card-lift" 
                  initial={{ opacity: 0, y: 20 }} 
                  animate={{ opacity: 1, y: 0 }} 
                  transition={{ delay: 0.6 + idx * 0.05 }}
                >
                  <div className="stat-card-content">
                    <div className="stat-card-label">{card.label}</div>
                    <div className="stat-card-value" style={{ fontSize: 'var(--font-size-lg)', color: card.color }}>
                      ₹{Number(card.value || 0).toLocaleString('en-IN')}
                    </div>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>

          {/* Sidebar widget space (Col Span 1) */}
          <div className="lg:col-span-1 flex flex-col gap-8">
            {/* Live Activity Feed */}
            <LiveActivityWidget
              recentBills={recentBills}
              lowStockList={lowStockList}
              pendingDeliveries={pendingDeliveriesList}
            />

            {/* Lari AI Copilot */}
            {aiEnabled && (isAdmin || isManager) ? (
              <AiCopilotWidget year={selectedYear} month={selectedMonth} />
            ) : (
              <div className="card card-lift ai-insight-card" style={{ minHeight: '150px', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', padding: 'var(--space-6)', textAlign: 'center' }}>
                <Brain size={32} style={{ color: '#c084fc', marginBottom: 'var(--space-2)' }} />
                <span className="card-title" style={{ fontSize: 'var(--font-size-md)' }}>AI Copilot Offline</span>
                <p style={{ color: 'var(--color-text-muted)', fontSize: '11px', marginTop: 'var(--space-2)' }}>
                  {!aiEnabled ? "AI Copilot feature is currently disabled in system settings." : "AI Advisor is restricted to Administrator and Manager roles."}
                </p>
              </div>
            )}

            {/* Alerts Panel */}
            <motion.div
              className="card card-lift"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.6 }}
              style={{ position: 'relative' }}
            >
              <div className="card-header flex justify-between items-center" style={{ marginBottom: 'var(--space-4)', paddingBottom: 'var(--space-3)' }}>
                <span className="card-title">Alerts & Notifications</span>
                <div style={{ display: 'flex', background: 'var(--color-surface-2)', borderRadius: 'var(--radius-sm)', padding: '2px', border: '1px solid var(--color-border)' }}>
                  <button 
                    onClick={() => setAlertScope('urgent')}
                    className={`btn btn-sm ${alertScope === 'urgent' ? 'btn-primary' : 'btn-ghost'}`}
                    style={{ padding: '4px 10px', fontSize: '10px', height: 'auto' }}
                  >
                    ⚡ Urgent
                  </button>
                  <button 
                    onClick={() => setAlertScope('expanded')}
                    className={`btn btn-sm ${alertScope === 'expanded' ? 'btn-primary' : 'btn-ghost'}`}
                    style={{ padding: '4px 10px', fontSize: '10px', height: 'auto' }}
                  >
                    📅 Monthly
                  </button>
                </div>
              </div>
              
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <AlertItem
                  icon={<Package size={20} />}
                  label="Low Stock Products"
                  value={lowStockList.length}
                  color="var(--color-danger)"
                  bg="var(--color-danger-soft)"
                  details={lowStockList}
                  type="lowStock"
                  scope={alertScope}
                />
                <AlertItem
                  icon={<AlertTriangle size={20} />}
                  label={alertScope === 'urgent' ? "Expiring in 7 Days" : "Expiring in 30 Days"}
                  value={expiringList.length}
                  color="var(--color-warning)"
                  bg="var(--color-warning-soft)"
                  details={expiringList}
                  type="expiring"
                  scope={alertScope}
                />
                <AlertItem
                  icon={<Users size={20} />}
                  label={alertScope === 'urgent' ? "Critical Inactive (30d+)" : "Warning Inactive (15d+)"}
                  value={inactiveList.length}
                  color="var(--color-info)"
                  bg="var(--color-info-soft)"
                  details={inactiveList}
                  type="inactive"
                  scope={alertScope}
                />
                <AlertItem
                  icon={<Truck size={20} />}
                  label="Pending Deliveries"
                  value={pendingDeliveriesList.length}
                  color="var(--color-accent)"
                  bg="var(--color-accent-soft)"
                  details={pendingDeliveriesList}
                  type="pending"
                  scope={alertScope}
                />
              </div>
              <div style={{ marginTop: 'var(--space-3)', fontSize: '10px', color: 'var(--color-text-muted)', textAlign: 'center', fontStyle: 'italic' }}>
                💡 Hover over any alert card to inspect immediate details!
              </div>
            </motion.div>
          </div>
        </div>
      ) : (
        /* CLASSIC LAYOUT (Original layout with grid rows) */
        <>
          {/* KPI Cards */}
          <div className="kpi-grid" style={{ marginBottom: 'var(--space-8)' }}>
            <StatCard
              icon={<IndianRupee size={24} />}
              label={kpis.revenueLabel}
              value={kpis.revenue}
              prefix="₹"
              color="var(--color-accent)"
              delay={0}
            />
            <StatCard
              icon={<ShoppingCart size={24} />}
              label={kpis.billsLabel}
              value={kpis.bills}
              color="var(--color-info)"
              delay={1}
            />
            <StatCard
              icon={<TrendingUp size={24} />}
              label={kpis.collectionLabel}
              value={kpis.collection}
              prefix="₹"
              color="var(--color-success)"
              delay={2}
              description={kpis.collectionDesc}
            />
            <StatCard
              icon={<TrendingDown size={24} />}
              label={kpis.pendingLabel}
              value={kpis.pending}
              prefix="₹"
              color="var(--color-danger)"
              delay={3}
            />
          </div>

          {/* Breakdown Sub-KPI Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4" style={{ marginTop: '-24px', marginBottom: 'var(--space-8)' }}>
            <StatCard
              icon={<IndianRupee size={20} />}
              label="Cash Collection"
              value={kpis.collectedCash}
              prefix="₹"
              color="var(--color-success)"
              delay={4}
            />
            <StatCard
              icon={<TrendingUp size={20} />}
              label="UPI Collection"
              value={kpis.collectedUpi}
              prefix="₹"
              color="var(--color-info)"
              delay={5}
            />
            <StatCard
              icon={<BookOpen size={20} />}
              label="Udhar Recovery"
              value={kpis.collectedUdhar}
              prefix="₹"
              color="var(--color-warning)"
              delay={6}
            />
          </div>

          {/* Charts Row */}
          <div className="grid-2" style={{ marginBottom: 'var(--space-8)' }}>
            {/* Revenue vs Expenses */}
            <motion.div
              className="card"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
            >
              <div className="card-header">
                <span className="card-title">
                  {viewMode === 'day' ? 'Daily Overview' : viewMode === 'month' ? 'Monthly Overview' : 'Yearly Overview'}
                </span>
              </div>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={revenueExpenseData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                  <XAxis dataKey="name" tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
                  <YAxis tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
                  <Tooltip content={customTooltip} />
                  <Bar dataKey="amount" radius={[6, 6, 0, 0]}>
                    {revenueExpenseData.map((entry, index) => (
                      <Cell
                        key={index}
                        fill={activeColors[index % activeColors.length]}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </motion.div>

            {/* Expense Breakdown */}
            <motion.div
              className="card"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4 }}
            >
              <div className="card-header">
                <span className="card-title">Expense Breakdown ({viewMode === 'year' ? 'Yearly' : 'Monthly'})</span>
              </div>
              {expenseChartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={280}>
                  <PieChart>
                    <Pie
                      data={expenseChartData}
                      innerRadius={60}
                      outerRadius={100}
                      paddingAngle={4}
                      dataKey="value"
                    >
                      {expenseChartData.map((_, idx) => (
                        <Cell key={idx} fill={activePieColors[idx % activePieColors.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(value) => `₹${Number(value).toLocaleString('en-IN')}`}
                      contentStyle={{
                        background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)',
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
                  <p className="text-muted">No expense data for this period</p>
                </div>
              )}
              {/* Legend */}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
                {expenseChartData.map((item, idx) => (
                  <div key={item.name} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', fontSize: 'var(--font-size-xs)' }}>
                    <div style={{ width: 10, height: 10, borderRadius: 2, background: activePieColors[idx % activePieColors.length] }} />
                    <span className="text-secondary">{item.name}</span>
                  </div>
                ))}
              </div>
            </motion.div>
          </div>

          {/* Top Products + Alerts Row */}
          <div className="grid-2" style={{ marginBottom: 'var(--space-8)' }}>
            {/* Top Products */}
            <motion.div
              className="card"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5 }}
            >
              <div className="card-header">
                <span className="card-title">Top Products ({viewMode === 'year' ? 'Yearly' : 'Monthly'})</span>
              </div>
              {topProductsData.length > 0 ? (
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={topProductsData} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                    <XAxis type="number" tick={{ fill: 'var(--color-text-secondary)', fontSize: 12 }} />
                    <YAxis dataKey="name" type="category" tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }} width={120} />
                    <Tooltip
                      contentStyle={{
                        background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-sm)',
                      }}
                    />
                    <Bar
                      dataKey="qty"
                      fill={
                        uiTheme === 'modern' ? '#8b5cf6' :
                        uiTheme === 'cyber' ? '#00d2ff' :
                        uiTheme === 'neon' ? '#00ffcc' :
                        '#f59e0b'
                      }
                      radius={[0, 6, 6, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state" style={{ padding: 'var(--space-10)' }}>
                  <p className="text-muted">No product data for this period</p>
                </div>
              )}
            </motion.div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
              {/* Alerts Panel */}
              <motion.div
                className="card"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.6 }}
                style={{ position: 'relative' }}
              >
                <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span className="card-title">Alerts & Notifications</span>
                  <div style={{ display: 'flex', background: 'var(--color-surface-2)', borderRadius: 'var(--radius-sm)', padding: '2px', border: '1px solid var(--color-border)' }}>
                    <button 
                      onClick={() => setAlertScope('urgent')}
                      className={`btn btn-sm ${alertScope === 'urgent' ? 'btn-primary' : 'btn-ghost'}`}
                      style={{ padding: '4px 10px', fontSize: '10px', height: 'auto' }}
                    >
                      ⚡ Urgent
                    </button>
                    <button 
                      onClick={() => setAlertScope('expanded')}
                      className={`btn btn-sm ${alertScope === 'expanded' ? 'btn-primary' : 'btn-ghost'}`}
                      style={{ padding: '4px 10px', fontSize: '10px', height: 'auto' }}
                    >
                      📅 Monthly
                    </button>
                  </div>
                </div>
                
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                  <AlertItem
                    icon={<Package size={20} />}
                    label="Low Stock Products"
                    value={lowStockList.length}
                    color="var(--color-danger)"
                    bg="var(--color-danger-soft)"
                    details={lowStockList}
                    type="lowStock"
                    scope={alertScope}
                  />
                  <AlertItem
                    icon={<AlertTriangle size={20} />}
                    label={alertScope === 'urgent' ? "Expiring in 7 Days" : "Expiring in 30 Days"}
                    value={expiringList.length}
                    color="var(--color-warning)"
                    bg="var(--color-warning-soft)"
                    details={expiringList}
                    type="expiring"
                    scope={alertScope}
                  />
                  <AlertItem
                    icon={<Users size={20} />}
                    label={alertScope === 'urgent' ? "Critical Inactive (30d+)" : "Warning Inactive (15d+)"}
                    value={inactiveList.length}
                    color="var(--color-info)"
                    bg="var(--color-info-soft)"
                    details={inactiveList}
                    type="inactive"
                    scope={alertScope}
                  />
                  <AlertItem
                    icon={<Truck size={20} />}
                    label="Pending Deliveries"
                    value={pendingDeliveriesList.length}
                    color="var(--color-accent)"
                    bg="var(--color-accent-soft)"
                    details={pendingDeliveriesList}
                    type="pending"
                    scope={alertScope}
                  />
                </div>
                <div style={{ marginTop: 'var(--space-3)', fontSize: '10px', color: 'var(--color-text-muted)', textAlign: 'center', fontStyle: 'italic' }}>
                  💡 Hover over any alert card to inspect immediate details!
                </div>
              </motion.div>

              {/* AI Copilot Insights Panel */}
              {aiEnabled && (isAdmin || isManager) ? (
                <AiCopilotWidget year={selectedYear} month={selectedMonth} />
              ) : (
                <div className="card ai-insight-card" style={{ minHeight: '150px', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', padding: 'var(--space-6)', textAlign: 'center' }}>
                  <Brain size={32} style={{ color: '#c084fc', marginBottom: 'var(--space-2)' }} />
                  <span className="card-title" style={{ fontSize: 'var(--font-size-md)' }}>AI Copilot Offline</span>
                  <p style={{ color: 'var(--color-text-muted)', fontSize: '11px', marginTop: 'var(--space-2)' }}>
                    {!aiEnabled ? "AI Copilot feature is currently disabled in system settings." : "AI Advisor is restricted to Administrator and Manager roles."}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Summary Cards */}
          <div className="grid-4">
            {bottomCards.map((card, idx) => (
              <motion.div 
                key={idx} 
                className="stat-card" 
                initial={{ opacity: 0, y: 20 }} 
                animate={{ opacity: 1, y: 0 }} 
                transition={{ delay: 0.7 + idx * 0.05 }}
              >
                <div className="stat-card-content">
                  <div className="stat-card-label">{card.label}</div>
                  <div className="stat-card-value" style={{ fontSize: 'var(--font-size-xl)', color: card.color }}>
                    ₹{Number(card.value || 0).toLocaleString('en-IN')}
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function AlertItem({ icon, label, value, color, bg, details, type, scope }) {
  const [hovered, setHovered] = useState(false)
  
  return (
    <div 
      onMouseEnter={() => {
        if (window.matchMedia('(hover: hover)').matches) {
          setHovered(true)
        }
      }}
      onMouseLeave={() => setHovered(false)}
      style={{
        position: 'relative',
        display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
        padding: 'var(--space-3) var(--space-4)',
        background: bg, borderRadius: 'var(--radius-md)',
        cursor: 'pointer',
        transition: 'transform var(--transition-fast), box-shadow var(--transition-fast)',
        border: '1px solid transparent',
      }}
      className="card-interactive"
    >
      <div style={{ color }}>{icon}</div>
      <span style={{ flex: 1, fontSize: 'var(--font-size-base)', color: 'var(--color-text)', fontWeight: 'var(--font-weight-medium)' }}>{label}</span>
      <span style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)', color }}>{value}</span>

      <AnimatePresence>
        {hovered && (
          <motion.div
            initial={{ opacity: 0, x: -15, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: -10, scale: 0.95 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            className="alert-popover"
            style={{
              border: `1px solid ${color === 'var(--color-danger)' ? 'rgba(239, 68, 68, 0.4)' : color === 'var(--color-warning)' ? 'rgba(245, 158, 11, 0.4)' : 'rgba(59, 130, 246, 0.4)'}`,
              boxShadow: `0 10px 30px rgba(0, 0, 0, 0.5), 0 0 15px ${color === 'var(--color-danger)' ? 'rgba(239, 68, 68, 0.15)' : color === 'var(--color-warning)' ? 'rgba(245, 158, 11, 0.15)' : 'rgba(59, 130, 246, 0.15)'}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-3)', borderBottom: '1px solid var(--color-border)', paddingBottom: 'var(--space-2)' }}>
              <div style={{ color }}>{icon}</div>
              <span style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--font-size-md)', color: 'var(--color-text)' }}>
                {label} ({value})
              </span>
            </div>

            <div style={{ maxHeight: '200px', overflowY: 'auto', paddingRight: '4px' }}>
              {details && details.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                  {details.map((item, idx) => (
                    <div 
                      key={idx} 
                      style={{ 
                        fontSize: 'var(--font-size-xs)',
                        padding: 'var(--space-2)',
                        background: 'rgba(10, 17, 40, 0.4)',
                        borderRadius: 'var(--radius-sm)',
                        borderLeft: `2px solid ${color}`,
                      }}
                    >
                      {renderDetailItem(type, item)}
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: 'var(--space-4) 0', color: 'var(--color-text-muted)', fontSize: 'var(--font-size-xs)' }}>
                  ✨ All clear! No items found.
                </div>
              )}
            </div>
            
            <div style={{ marginTop: 'var(--space-3)', fontSize: '9px', color: 'var(--color-text-muted)', textAlign: 'right', fontStyle: 'italic' }}>
              * Powered by FMCG AI Engine
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function renderDetailItem(type, item) {
  if (type === 'lowStock') {
    return (
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text)' }}>{item.productName}</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '10px' }}>{item.brand} • {item.category}</div>
        </div>
        <span className="badge badge-danger" style={{ fontSize: '10px' }}>
          {item.currentStock} / {item.threshold} {item.unit || ''}
        </span>
      </div>
    )
  }
  
  if (type === 'expiring') {
    const daysLeft = Math.ceil((new Date(item.expiryDate) - new Date()) / (1000 * 60 * 60 * 24));
    let badgeClass = "badge-danger";
    if (daysLeft > 14) badgeClass = "badge-neutral";
    else if (daysLeft > 7) badgeClass = "badge-warning";
    
    return (
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text)' }}>{item.productName}</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '10px' }}>Batch: {item.batchNo} • Qty: {item.stockCount}</div>
        </div>
        <span className={`badge ${badgeClass}`} style={{ fontSize: '10px' }}>
          {daysLeft <= 0 ? "Expired" : `${daysLeft}d left`}
        </span>
      </div>
    )
  }

  if (type === 'inactive') {
    let daysInactive = "N/A";
    if (item.lastOrderDate) {
      daysInactive = Math.ceil((new Date() - new Date(item.lastOrderDate)) / (1000 * 60 * 60 * 24)) + " days";
    }
    const isCritical = daysInactive !== "N/A" && parseInt(daysInactive) >= 30;

    return (
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text)' }}>{item.customerName}</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '10px' }}>{item.shopName} • {item.phone}</div>
        </div>
        <span className={`badge ${isCritical ? 'badge-danger' : 'badge-warning'}`} style={{ fontSize: '10px' }}>
          {daysInactive} idle
        </span>
      </div>
    )
  }

  if (type === 'pending') {
    return (
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text)' }}>{item.billNumber}</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '10px' }}>{item.customerName} • {item.shopName}</div>
        </div>
        <span className="badge badge-accent" style={{ fontSize: '10px', fontWeight: 'bold' }}>
          ₹{Number(item.amount || 0).toLocaleString('en-IN')}
        </span>
      </div>
    )
  }

  return null;
}
