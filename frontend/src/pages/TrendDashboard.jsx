import React, { useState, useEffect } from 'react'
import api from '../services/api'
import {
  TrendingUp, Calendar, RefreshCw, AlertCircle, Info,
  TrendingDown, Minus, DollarSign, Activity, Package, Users,
  BookOpen, Truck, Receipt
} from 'lucide-react'
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend, LineChart, Line, BarChart, Bar,
  PieChart, Pie, Cell, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar
} from 'recharts'

export default function TrendDashboard() {
  const [monthsLimit, setMonthsLimit] = useState(6)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  // Interactive UI States
  const [healthChartType, setHealthChartType] = useState('area') // 'area' | 'bar'
  const [financialChartType, setFinancialChartType] = useState('line') // 'line' | 'bar'
  const [activeCategory, setActiveCategory] = useState(null) // key of sub-category or null
  const [visibleFinancials, setVisibleFinancials] = useState({
    revenue: true,
    totalExpenses: true,
    netProfit: true
  })
  const [selectedMonthIndex, setSelectedMonthIndex] = useState(0)

  const fetchTrendData = async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await api.get(`/dashboard/ai/health-report/trend?months=${monthsLimit}`)
      if (response.data && response.data.success) {
        setData(response.data.data)
        const trends = response.data.data.trends || []
        // Auto-fallback to Bar chart if there's only 1 month of data
        if (trends.length === 1) {
          setHealthChartType('bar')
          setFinancialChartType('bar')
        } else {
          setHealthChartType('area')
          setFinancialChartType('line')
        }
        if (trends.length > 0) {
          setSelectedMonthIndex(trends.length - 1)
        }
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

  const handleFinancialLegendClick = (props) => {
    if (!props) return
    const { dataKey } = props
    if (!dataKey) return
    setVisibleFinancials(prev => ({
      ...prev,
      [dataKey]: !prev[dataKey]
    }))
  }

  const renderLegendText = (value, entry) => {
    const id = entry?.id || value
    let key = ''
    if (id === 'revenue' || value === 'Revenue') key = 'revenue'
    else if (id === 'totalExpenses' || value === 'Total Expenses') key = 'totalExpenses'
    else if (id === 'netProfit' || value === 'Net Profit') key = 'netProfit'

    const isVisible = key ? visibleFinancials[key] : true
    return (
      <span className={`cursor-pointer transition-all duration-200 hover:text-slate-900 dark:hover:text-white select-none ${
        isVisible ? 'opacity-100 font-semibold' : 'opacity-35 line-through text-slate-400'
      }`}>
        {value}
      </span>
    )
  }

  // Custom tooltips to render cleanly on null/missing values
  const CustomScoreTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div className="p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded shadow-md text-xs flex flex-col gap-1.5">
          <p className="font-semibold text-slate-800 dark:text-slate-200 mb-1">{label}</p>
          {payload.map((item, idx) => (
            <div key={idx} className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: item?.color || '#3b82f6' }}></span>
              <span className="text-slate-600 dark:text-slate-400">{item?.name || 'Score'}:</span>
              <span className="font-bold text-slate-900 dark:text-slate-100">
                {item?.value === null || item?.value === undefined ? 'No Data' : `${item.value} / 100`}
              </span>
            </div>
          ))}
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
                <span className="w-2 h-2 rounded-full" style={{ backgroundColor: item?.color || '#3b82f6' }}></span>
                <span>{item?.name || 'Value'}:</span>
              </div>
              <span className="font-bold text-slate-900 dark:text-slate-100">
                {formatCurrency(item?.value)}
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

  const categories = [
    { name: 'Profitability', key: 'profitabilityScore', color: '#10b981', icon: DollarSign },
    { name: 'Cash Flow', key: 'cashFlowScore', color: '#3b82f6', icon: Activity },
    { name: 'Inventory Efficiency', key: 'inventoryScore', color: '#f59e0b', icon: Package },
    { name: 'Customer Engagement', key: 'customerScore', color: '#8b5cf6', icon: Users },
    { name: 'Receivables Control', key: 'receivablesScore', color: '#f43f5e', icon: BookOpen },
    { name: 'Suppliers Management', key: 'suppliersScore', color: '#06b6d4', icon: Truck }
  ]

  const activeCategoryDetail = categories.find(c => c.key === activeCategory)
  const selectedData = trendData[selectedMonthIndex] || null

  const radarData = selectedData ? [
    { subject: 'Profitability', value: selectedData.profitabilityScore || 0, fullMark: 100 },
    { subject: 'Cash Flow', value: selectedData.cashFlowScore || 0, fullMark: 100 },
    { subject: 'Inventory', value: selectedData.inventoryScore || 0, fullMark: 100 },
    { subject: 'Customer', value: selectedData.customerScore || 0, fullMark: 100 },
    { subject: 'Receivables', value: selectedData.receivablesScore || 0, fullMark: 100 },
    { subject: 'Suppliers', value: selectedData.suppliersScore || 0, fullMark: 100 }
  ] : []

  const expensesVal = selectedData?.totalExpenses ? Number(selectedData.totalExpenses) : 0
  const profitVal = selectedData?.netProfit ? Number(selectedData.netProfit) : 0
  const totalRevenue = selectedData ? Number(selectedData.revenue) : 0

  const pieData = []
  if (expensesVal > 0) {
    pieData.push({ name: 'Expenses', value: expensesVal, color: '#f43f5e' })
  }
  if (profitVal > 0) {
    pieData.push({ name: 'Net Profit', value: profitVal, color: '#6366f1' })
  } else if (profitVal < 0) {
    pieData.push({ name: 'Net Loss', value: Math.abs(profitVal), color: '#eab308' })
  }

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
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300 flex flex-col gap-0.5">
                  <span className="flex items-center gap-1.5">
                    <Activity className="text-blue-500" size={16} /> Overall Health Score Trend
                  </span>
                  {activeCategoryDetail && (
                    <span className="text-[11px] text-slate-500 font-normal">
                      Comparing with: <span className="font-semibold" style={{ color: activeCategoryDetail.color }}>{activeCategoryDetail.name}</span>
                    </span>
                  )}
                </h3>
                <div className="flex bg-slate-100 dark:bg-slate-800 p-0.5 rounded-lg border border-slate-200 dark:border-slate-700 animate-fade-in">
                  <button
                    onClick={() => setHealthChartType('area')}
                    className={`px-2.5 py-1 text-[10px] font-semibold rounded-md transition ${
                      healthChartType === 'area'
                        ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-sm'
                        : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                    }`}
                  >
                    Area/Line
                  </button>
                  <button
                    onClick={() => setHealthChartType('bar')}
                    className={`px-2.5 py-1 text-[10px] font-semibold rounded-md transition ${
                      healthChartType === 'bar'
                        ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-sm'
                        : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                    }`}
                  >
                    Bar
                  </button>
                </div>
              </div>

              {activeCategoryDetail && (
                <div className="flex items-center justify-between bg-slate-50 dark:bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-100 dark:border-slate-800 text-[11px] select-none animate-slide-down">
                  <span className="text-slate-600 dark:text-slate-400">
                    Comparing Overall Health with <strong style={{ color: activeCategoryDetail.color }}>{activeCategoryDetail.name}</strong>
                  </span>
                  <button
                    onClick={() => setActiveCategory(null)}
                    className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 font-bold transition"
                  >
                    ✕ Clear Compare
                  </button>
                </div>
              )}

              <div className="h-[320px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  {healthChartType === 'area' ? (
                    <AreaChart
                      data={trendData}
                      margin={{ top: 10, right: 10, left: -20, bottom: 0 }}
                      onClick={(nextState) => {
                        if (nextState && nextState.activeTooltipIndex !== undefined) {
                          setSelectedMonthIndex(nextState.activeTooltipIndex)
                        }
                      }}
                      className="cursor-pointer"
                    >
                      <defs>
                        <linearGradient id="colorScore" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.25}/>
                          <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.0}/>
                        </linearGradient>
                        {activeCategoryDetail && (
                          <linearGradient id={`colorSub_${activeCategory}`} x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor={activeCategoryDetail.color} stopOpacity={0.2}/>
                            <stop offset="95%" stopColor={activeCategoryDetail.color} stopOpacity={0.0}/>
                          </linearGradient>
                        )}
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-800" />
                      <XAxis dataKey="monthName" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                      <YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                      <Tooltip content={<CustomScoreTooltip />} />
                      <Area type="monotone" dataKey="overallScore" stroke="#3b82f6" strokeWidth={2.5} fillOpacity={1} fill="url(#colorScore)" connectNulls={false} name="Overall Score" dot={{ r: trendData.length === 1 ? 5 : 0 }} activeDot={{ r: 7 }} />
                      {activeCategoryDetail && (
                        <Area type="monotone" dataKey={activeCategory} stroke={activeCategoryDetail.color} strokeWidth={2} fillOpacity={1} fill={`url(#colorSub_${activeCategory})`} connectNulls={false} name={activeCategoryDetail.name} dot={{ r: trendData.length === 1 ? 5 : 0 }} activeDot={{ r: 7 }} />
                      )}
                    </AreaChart>
                  ) : (
                    <BarChart
                      data={trendData}
                      margin={{ top: 10, right: 10, left: -20, bottom: 0 }}
                      onClick={(nextState) => {
                        if (nextState && nextState.activeTooltipIndex !== undefined) {
                          setSelectedMonthIndex(nextState.activeTooltipIndex)
                        }
                      }}
                      className="cursor-pointer"
                    >
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-800" />
                      <XAxis dataKey="monthName" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                      <YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                      <Tooltip content={<CustomScoreTooltip />} />
                      <Bar dataKey="overallScore" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Overall Score" maxBarSize={40} />
                      {activeCategoryDetail && (
                        <Bar dataKey={activeCategory} fill={activeCategoryDetail.color} radius={[4, 4, 0, 0]} name={activeCategoryDetail.name} maxBarSize={40} />
                      )}
                    </BarChart>
                  )}
                </ResponsiveContainer>
              </div>
            </div>

            {/* Financial Trends Overlay (Revenue, Expenses, Net Profit) */}
            <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                  <DollarSign className="text-emerald-500" size={16} /> Revenue vs Expenses vs Net Profit
                </h3>
                <div className="flex bg-slate-100 dark:bg-slate-800 p-0.5 rounded-lg border border-slate-200 dark:border-slate-700 animate-fade-in">
                  <button
                    onClick={() => setFinancialChartType('line')}
                    className={`px-2.5 py-1 text-[10px] font-semibold rounded-md transition ${
                      financialChartType === 'line'
                        ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-sm'
                        : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                    }`}
                  >
                    Line
                  </button>
                  <button
                    onClick={() => setFinancialChartType('bar')}
                    className={`px-2.5 py-1 text-[10px] font-semibold rounded-md transition ${
                      financialChartType === 'bar'
                        ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-sm'
                        : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                    }`}
                  >
                    Bar
                  </button>
                </div>
              </div>
              <div className="h-[320px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  {financialChartType === 'line' ? (
                    <LineChart
                      data={trendData}
                      margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
                      onClick={(nextState) => {
                        if (nextState && nextState.activeTooltipIndex !== undefined) {
                          setSelectedMonthIndex(nextState.activeTooltipIndex)
                        }
                      }}
                      className="cursor-pointer"
                    >
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-800" />
                      <XAxis dataKey="monthName" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                      <YAxis tickLine={false} axisLine={false} tickFormatter={(tick) => `₹${(tick / 1000).toFixed(0)}k`} tick={{ fontSize: 11, fill: 'gray' }} />
                      <Tooltip content={<CustomFinancialTooltip />} />
                      <Legend onClick={handleFinancialLegendClick} formatter={renderLegendText} verticalAlign="top" height={36} wrapperStyle={{ fontSize: 12 }} />
                      <Line type="monotone" dataKey="revenue" stroke="#10b981" strokeWidth={2} dot={{ r: trendData.length === 1 ? 5 : 3 }} activeDot={{ r: 6 }} hide={!visibleFinancials.revenue} name="Revenue" />
                      <Line type="monotone" dataKey="totalExpenses" stroke="#f43f5e" strokeWidth={2} dot={{ r: trendData.length === 1 ? 5 : 3 }} activeDot={{ r: 6 }} hide={!visibleFinancials.totalExpenses} name="Total Expenses" />
                      <Line type="monotone" dataKey="netProfit" stroke="#6366f1" strokeWidth={2.5} dot={{ r: trendData.length === 1 ? 5 : 3 }} activeDot={{ r: 6 }} hide={!visibleFinancials.netProfit} name="Net Profit" />
                    </LineChart>
                  ) : (
                    <BarChart
                      data={trendData}
                      margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
                      onClick={(nextState) => {
                        if (nextState && nextState.activeTooltipIndex !== undefined) {
                          setSelectedMonthIndex(nextState.activeTooltipIndex)
                        }
                      }}
                      className="cursor-pointer"
                    >
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-800" />
                      <XAxis dataKey="monthName" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'gray' }} />
                      <YAxis tickLine={false} axisLine={false} tickFormatter={(tick) => `₹${(tick / 1000).toFixed(0)}k`} tick={{ fontSize: 11, fill: 'gray' }} />
                      <Tooltip content={<CustomFinancialTooltip />} />
                      <Legend onClick={handleFinancialLegendClick} formatter={renderLegendText} verticalAlign="top" height={36} wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="revenue" fill="#10b981" radius={[4, 4, 0, 0]} hide={!visibleFinancials.revenue} name="Revenue" maxBarSize={25} />
                      <Bar dataKey="totalExpenses" fill="#f43f5e" radius={[4, 4, 0, 0]} hide={!visibleFinancials.totalExpenses} name="Total Expenses" maxBarSize={25} />
                      <Bar dataKey="netProfit" fill="#6366f1" radius={[4, 4, 0, 0]} hide={!visibleFinancials.netProfit} name="Net Profit" maxBarSize={25} />
                    </BarChart>
                  )}
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Selected Month Details Section */}
          {selectedData && (
            <div className="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex flex-col gap-4 animate-fade-in">
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 dark:border-slate-800 pb-3">
                <div>
                  <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                    <Calendar className="text-blue-500" size={16} /> Selected Month Performance Profile
                  </h3>
                  <p className="text-slate-500 text-xs mt-0.5">
                    Detailed analysis for <span className="font-semibold text-blue-600 dark:text-blue-400">{selectedData.monthName} {selectedData.year}</span> (Click points in the trend charts above to change month)
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-500">Health Score:</span>
                  <span className={`px-2 py-0.5 rounded-full text-xs font-bold border ${getScoreColor(selectedData.overallScore)} bg-slate-50 dark:bg-slate-950`}>
                    {selectedData.overallScore ?? 'N/A'} / 100
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-2">
                {/* Radar Chart (Category Scores breakdown) */}
                <div className="flex flex-col items-center justify-center p-4 border border-slate-100 dark:border-slate-850 rounded-lg bg-slate-50/50 dark:bg-slate-950/20 h-[300px]">
                  <h4 className="text-xs font-bold text-slate-500 dark:text-slate-400 mb-2 self-start uppercase tracking-wider">Strength Profile (Category Scores)</h4>
                  <div className="w-full h-full max-h-[240px]">
                    <ResponsiveContainer width="100%" height="100%">
                      <RadarChart cx="50%" cy="50%" outerRadius="75%" data={radarData}>
                        <PolarGrid stroke="#e2e8f0" className="dark:stroke-slate-800" />
                        <PolarAngleAxis dataKey="subject" tick={{ fontSize: 10, fill: 'gray' }} />
                        <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fontSize: 9 }} />
                        <Radar name="Score" dataKey="value" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.3} />
                        <Tooltip formatter={(value) => [`${value} / 100`, 'Score']} />
                      </RadarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                {/* Donut Chart (Financial share breakdown) */}
                <div className="flex flex-col items-center justify-center p-4 border border-slate-100 dark:border-slate-850 rounded-lg bg-slate-50/50 dark:bg-slate-950/20 h-[300px] relative">
                  <h4 className="text-xs font-bold text-slate-500 dark:text-slate-400 mb-2 self-start uppercase tracking-wider">Revenue Allocation (Profit vs Expenses)</h4>
                  {pieData.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs">
                      <span>No financial data for this month.</span>
                    </div>
                  ) : (
                    <>
                      <div className="w-full h-full max-h-[220px]">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={pieData}
                              cx="50%"
                              cy="50%"
                              innerRadius={55}
                              outerRadius={75}
                              paddingAngle={3}
                              dataKey="value"
                            >
                              {pieData.map((entry, idx) => (
                                <Cell key={`cell-${idx}`} fill={entry.color} />
                              ))}
                            </Pie>
                            <Tooltip formatter={(value) => [formatCurrency(value), '']} />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                      <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none mt-6">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">Revenue</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-slate-100">{formatCurrency(totalRevenue)}</span>
                      </div>
                      <div className="flex justify-center gap-4 text-xs mt-1">
                        {(() => {
                          const totalPieValue = pieData.reduce((sum, p) => sum + p.value, 0)
                          return pieData.map((item, idx) => (
                            <div key={idx} className="flex items-center gap-1">
                              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }}></span>
                              <span className="text-slate-600 dark:text-slate-400 font-semibold">
                                {item.name} ({((item.value / (totalPieValue || 1)) * 100).toFixed(0)}%)
                              </span>
                            </div>
                          ))
                        })()}
                      </div>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Sub-scores Sparkline Grid */}
          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300">Category Score Trends</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {categories.map((sub, idx) => {
                const Icon = sub.icon
                const isActive = activeCategory === sub.key
                return (
                  <div
                    key={idx}
                    onClick={() => setActiveCategory(isActive ? null : sub.key)}
                    className={`p-4 bg-white dark:bg-slate-900 border rounded-xl shadow-sm flex flex-col gap-3 cursor-pointer transition-all duration-300 hover:scale-[1.02] hover:shadow-md select-none ${
                      isActive
                        ? 'border-transparent shadow-lg'
                        : 'border-slate-200 dark:border-slate-800 hover:border-slate-400 dark:hover:border-slate-600'
                    }`}
                    style={isActive ? { border: `2px solid ${sub.color}`, boxShadow: `0 4px 12px ${sub.color}20` } : {}}
                  >
                    <div className="flex items-center justify-between gap-2 border-b border-slate-100 dark:border-slate-800 pb-2">
                      <span className="text-xs font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                        <Icon size={14} className="text-slate-400 dark:text-slate-500" /> {sub.name}
                      </span>
                      {isActive && (
                        <span className="text-[9px] font-bold px-2 py-0.5 rounded text-white" style={{ backgroundColor: sub.color }}>
                          Comparing
                        </span>
                      )}
                    </div>
                    <div className="h-[100px] w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={trendData} margin={{ top: 5, right: 5, left: -40, bottom: 5 }}>
                          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" className="dark:stroke-slate-855" />
                          <XAxis dataKey="monthName" hide />
                          <YAxis domain={[0, 100]} hide />
                          <Tooltip content={<CustomScoreTooltip />} />
                          <Area type="monotone" dataKey={sub.key} stroke={sub.color} fill={sub.color} strokeWidth={2} fillOpacity={0.1} connectNulls={false} name={sub.name} dot={{ r: trendData.length === 1 ? 4 : 0 }} activeDot={{ r: 5 }} />
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
