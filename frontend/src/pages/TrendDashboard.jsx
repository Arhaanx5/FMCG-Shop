import React, { useState, useEffect } from 'react'
import api from '../services/api'
import {
  TrendingUp, Calendar, RefreshCw, AlertCircle, Info,
  TrendingDown, Minus, DollarSign, Activity, Package, Users,
  BookOpen, Truck, Receipt
} from 'lucide-react'
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend, LineChart, Line
} from 'recharts'

export default function TrendDashboard() {
  const [monthsLimit, setMonthsLimit] = useState(6)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const fetchTrendData = async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await api.get(`/dashboard/ai/health-report/trend?months=${monthsLimit}`)
      if (response.data && response.data.success) {
        setData(response.data.data)
      } else {
        setError(response.data?.message || 'Failed to fetch trend data.')
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to connect to backend server. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTrendData()
  }, [monthsLimit])

  const formatCurrency = (val) => {
    if (val === null || val === undefined) return '₹0.00'
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2
    }).format(val)
  }

  const getScoreColor = (score) => {
    if (score === null || score === undefined) return 'text-slate-400 border-slate-300'
    if (score >= 80) return 'text-emerald-600 dark:text-emerald-400 border-emerald-500'
    if (score >= 55) return 'text-amber-600 dark:text-amber-400 border-amber-500'
    return 'text-rose-600 dark:text-rose-400 border-rose-500'
  }

  // Custom tooltips to render cleanly on null/missing values
  const CustomScoreTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      const scoreVal = payload[0].value
      return (
        <div className="p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded shadow-md text-xs">
          <p className="font-semibold text-slate-800 dark:text-slate-200 mb-1">{label}</p>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: payload[0].color || '#3b82f6' }}></span>
            <span className="text-slate-600 dark:text-slate-400">{payload[0].name}:</span>
            <span className="font-bold text-slate-900 dark:text-slate-100">
              {scoreVal === null || scoreVal === undefined ? 'No Data' : `${scoreVal} / 100`}
            </span>
          </div>
        </div>
      )
    }
    return null
  }

  const CustomFinancialTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div className="p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded shadow-md text-xs">
          <p className="font-semibold text-slate-800 dark:text-slate-200 mb-2">{label}</p>
          {payload.map((item, idx) => (
            <div key={idx} className="flex items-center justify-between gap-4 mb-1">
              <div className="flex items-center gap-1.5 text-slate-600 dark:text-slate-400">
                <span className="w-2 h-2 rounded-full" style={{ backgroundColor: item.color }}></span>
                <span>{item.name}:</span>
              </div>
              <span className="font-bold text-slate-900 dark:text-slate-100">
                {formatCurrency(item.value)}
              </span>
            </div>
          ))}
        </div>
      )
    }
    return null
  }

  if (loading && !data) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] gap-3">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-600"></div>
        <span className="text-slate-500 text-sm">Loading health trends...</span>
      </div>
    )
  }

  if (error && !data) {
    return (
      <div className="p-6 max-w-lg mx-auto bg-rose-50 dark:bg-rose-950/20 border border-rose-200 rounded-lg text-center mt-12">
        <AlertCircle className="mx-auto text-rose-500 mb-3" size={36} />
        <h3 className="text-rose-800 dark:text-rose-400 font-semibold mb-2">Trend Fetch Failed</h3>
        <p className="text-rose-600 dark:text-rose-500 text-xs mb-4">{error}</p>
        <button onClick={fetchTrendData} className="px-4 py-2 text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 rounded transition">
          Retry
        </button>
      </div>
    )
  }

  const trendData = data?.trends || []
  const currentScore = data?.currentMonthScore
  const scoreDelta = data?.scoreDelta
  const explanation = data?.deltaExplanation

  return (
    <div className="p-6 max-w-[1600px] mx-auto flex flex-col gap-6">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 dark:border-slate-800 pb-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
            <TrendingUp className="text-blue-500" /> Business Health Trends
          </h2>
          <p className="text-slate-500 text-xs mt-1">Monitor historical performance scores and financial metrics</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={monthsLimit}
            onChange={(e) => setMonthsLimit(Number(e.target.value))}
            className="px-3 py-1.5 text-xs rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-200 shadow-sm focus:outline-none"
          >
            <option value={3}>Last 3 Months</option>
            <option value={6}>Last 6 Months</option>
            <option value={12}>Last 12 Months</option>
          </select>
          <button
            onClick={fetchTrendData}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-50 hover:bg-blue-100 dark:bg-blue-950/20 text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-800 rounded text-xs font-bold transition disabled:opacity-50"
          >
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
        </div>
      </div>

      {/* MoM Delta Insight Banner */}
      {explanation && (
        <div className="flex items-start gap-3 p-4 bg-blue-50 dark:bg-blue-950/20 border border-blue-200 dark:border-blue-800 rounded-lg">
          <Info className="text-blue-500 shrink-0 mt-0.5" size={18} />
          <div>
            <h4 className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1">Month-over-Month Delta Insight</h4>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-200 leading-relaxed">{explanation}</p>
          </div>
        </div>
      )}

      {trendData.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 border border-dashed border-slate-350 dark:border-slate-800 rounded-lg text-slate-400 bg-slate-50 dark:bg-slate-900/40">
          <Calendar size={48} className="mb-2 text-slate-300" />
          <p className="text-sm">Trend data generate karne ke liye purane months ke health reports generate kijiye.</p>
        </div>
      ) : (
        <>
          {/* Main Charts Row */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Overall Health Trend Chart */}
            <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex flex-col gap-4">
              <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                <Activity className="text-blue-500" size={16} /> Overall Health Score Trend
              </h3>
              <div className="h-[320px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={trendData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <defs>
                      <linearGradient id="colorScore" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.25}/>
                        <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-800" />
                    <XAxis dataKey="monthName" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                    <YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                    <Tooltip content={<CustomScoreTooltip />} />
                    <Area type="monotone" dataKey="overallScore" stroke="#3b82f6" strokeWidth={2.5} fillOpacity={1} fill="url(#colorScore)" connectNulls={false} name="Overall Score" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Financial Trends Overlay (Revenue, Expenses, Net Profit) */}
            <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex flex-col gap-4">
              <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                <DollarSign className="text-emerald-500" size={16} /> Revenue vs Expenses vs Net Profit
              </h3>
              <div className="h-[320px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={trendData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-800" />
                    <XAxis dataKey="monthName" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                    <YAxis tickLine={false} axisLine={false} tickFormatter={(tick) => `₹${(tick / 1000).toFixed(0)}k`} tick={{ fontSize: 11, fill: 'gray' }} />
                    <Tooltip content={<CustomFinancialTooltip />} />
                    <Legend verticalAlign="top" height={36} wrapperStyle={{ fontSize: 12 }} />
                    <Line type="monotone" dataKey="revenue" stroke="#10b981" strokeWidth={2} activeDot={{ r: 6 }} name="Revenue" />
                    <Line type="monotone" dataKey="totalExpenses" stroke="#f43f5e" strokeWidth={2} name="Total Expenses" />
                    <Line type="monotone" dataKey="netProfit" stroke="#6366f1" strokeWidth={2.5} name="Net Profit" />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Sub-scores Sparkline Grid */}
          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300">Category Score Trends</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {[
                { name: 'Profitability', key: 'profitabilityScore', color: '#10b981', icon: DollarSign },
                { name: 'Cash Flow', key: 'cashFlowScore', color: '#3b82f6', icon: Activity },
                { name: 'Inventory Efficiency', key: 'inventoryScore', color: '#f59e0b', icon: Package },
                { name: 'Customer Engagement', key: 'customerScore', color: '#8b5cf6', icon: Users },
                { name: 'Receivables Control', key: 'receivablesScore', color: '#f43f5e', icon: BookOpen },
                { name: 'Suppliers Management', key: 'suppliersScore', color: '#06b6d4', icon: Truck }
              ].map((sub, idx) => {
                const Icon = sub.icon
                return (
                  <div key={idx} className="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex flex-col gap-3">
                    <div className="flex items-center justify-between gap-2 border-b border-slate-100 dark:border-slate-800 pb-2">
                      <span className="text-xs font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                        <Icon size={14} className="text-slate-400 dark:text-slate-500" /> {sub.name}
                      </span>
                    </div>
                    <div className="h-[100px] w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={trendData} margin={{ top: 5, right: 5, left: -40, bottom: 5 }}>
                          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-850" />
                          <XAxis dataKey="monthName" hide />
                          <YAxis domain={[0, 100]} hide />
                          <Tooltip content={<CustomScoreTooltip />} />
                          <Area type="monotone" dataKey={sub.key} stroke={sub.color} fill={sub.color} strokeWidth={2} fillOpacity={0.1} connectNulls={false} name={sub.name} />
                        </AreaChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
