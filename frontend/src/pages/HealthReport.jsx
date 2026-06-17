import React, { useState, useEffect } from 'react'
import api from '../services/api'
import {
  Activity, Calendar, Printer, RefreshCw, AlertCircle, CheckCircle2,
  TrendingUp, Wallet, Package, Users, ShieldAlert, BadgePercent,
  Truck, ArrowRight, Info
} from 'lucide-react'

export default function HealthReport() {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1 // 1-indexed

  const [year, setYear] = useState(currentYear)
  const [month, setMonth] = useState(currentMonth)
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [activeTab, setActiveTab] = useState('profitability')
  const [cooldown, setCooldown] = useState(false)

  const years = Array.from({ length: 5 }, (_, i) => currentYear - i)
  const months = [
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' }
  ]

  const fetchReport = async (force = false) => {
    setLoading(true)
    setError(null)
    if (force) {
      setCooldown(true)
      setTimeout(() => setCooldown(false), 5000)
    }
    try {
      const response = await api.get(`/dashboard/ai/health-report?year=${year}&month=${month}&force=${force}`)
      if (response.data && response.data.success) {
        setReport(response.data.data)
      } else {
        setError(response.data?.message || 'Report generation failed. Please try again.')
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to connect to backend server. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchReport(false)
  }, [year, month])

  const getScoreColor = (score) => {
    if (score === null || score === undefined) return 'text-slate-400 border-slate-350 bg-slate-50'
    if (score >= 80) return 'text-emerald-600 dark:text-emerald-400 border-emerald-500 bg-emerald-50 dark:bg-emerald-950/20'
    if (score >= 55) return 'text-amber-600 dark:text-amber-400 border-amber-500 bg-amber-50 dark:bg-amber-950/20'
    return 'text-rose-600 dark:text-rose-400 border-rose-500 bg-rose-50 dark:bg-rose-950/20'
  }

  const getScoreProgressColor = (score) => {
    if (score >= 80) return '#10b981' // emerald-500
    if (score >= 55) return '#f59e0b' // amber-500
    return '#f43f5e' // rose-500
  }

  const getStatusBadge = (status) => {
    switch (status) {
      case 'HEALTHY':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-300">HEALTHY</span>
      case 'STABLE':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-blue-100 dark:bg-blue-950/40 text-blue-700 dark:text-blue-400 border border-blue-300">STABLE</span>
      case 'DECLINING':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-300">DECLINING</span>
      case 'AT_RISK':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border border-rose-300">AT RISK</span>
      default:
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-400 border border-slate-300">UNKNOWN</span>
    }
  }

  const getUrgencyBadge = (urgency) => {
    switch (urgency) {
      case 'HIGH':
        return <span className="px-2.5 py-0.5 text-[10px] font-bold rounded bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border border-rose-350 uppercase">HIGH</span>
      case 'MEDIUM':
        return <span className="px-2.5 py-0.5 text-[10px] font-bold rounded bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-350 uppercase">MEDIUM</span>
      default:
        return <span className="px-2.5 py-0.5 text-[10px] font-bold rounded bg-slate-150 dark:bg-slate-800 text-slate-700 dark:text-slate-400 border border-slate-300 uppercase">LOW</span>
    }
  }

  const getCategoryIcon = (category) => {
    switch (category) {
      case 'profitability': return <TrendingUp className="w-5 h-5" />
      case 'cashFlow': return <Wallet className="w-5 h-5" />
      case 'inventory': return <Package className="w-5 h-5" />
      case 'customer': return <Users className="w-5 h-5" />
      case 'receivables': return <ShieldAlert className="w-5 h-5" />
      case 'suppliers': return <BadgePercent className="w-5 h-5" />
      case 'operational': return <Truck className="w-5 h-5" />
      default: return <Activity className="w-5 h-5" />
    }
  }

  const getTabLabel = (tab) => {
    switch (tab) {
      case 'profitability': return 'Profitability'
      case 'cashFlow': return 'Cash Flow'
      case 'inventory': return 'Inventory Efficiency'
      case 'customer': return 'Customer Health'
      case 'receivables': return 'Receivables Aging'
      case 'suppliers': return 'Suppliers / Cost'
      case 'operational': return 'Operational Health'
      default: return tab
    }
  }

  const renderCategoryCard = (key, data) => {
    if (!data) return null
    const score = data.score
    const isSelected = activeTab === key

    return (
      <button
        key={key}
        onClick={() => setActiveTab(key)}
        className={`flex items-center justify-between p-4 rounded-theme-md border text-left transition-all ${
          isSelected
            ? 'border-violet-500 dark:border-violet-400 bg-violet-50/40 dark:bg-violet-950/10 shadow-sm ring-1 ring-violet-500/20'
            : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-850 hover:border-slate-300 dark:hover:border-slate-700 hover:bg-slate-50/50'
        }`}
      >
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-theme-sm ${
            isSelected
              ? 'bg-violet-500/10 text-violet-500 dark:text-violet-400'
              : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400'
          }`}>
            {getCategoryIcon(key)}
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-850 dark:text-slate-200">
              {getTabLabel(key)}
            </div>
            <div className="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5">
              Rating: <span className="font-semibold">{data.rating || 'N/A'}</span>
            </div>
          </div>
        </div>
        
        {/* Sub-score indicator */}
        <div className={`w-8 h-8 rounded-full border flex items-center justify-center font-bold text-xs ${getScoreColor(score)}`}>
          {score !== null && score !== undefined ? score : '-'}
        </div>
      </button>
    )
  }

  return (
    <div className="p-4 md:p-8 max-w-7xl mx-auto space-y-6">
      {/* Configuration & Selection Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white dark:bg-slate-850 p-4 border border-slate-200 dark:border-slate-800 rounded-theme-md no-print">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 text-slate-500 dark:text-slate-400 text-sm font-semibold">
            <Calendar size={18} />
            <span>Select Period:</span>
          </div>
          
          <select
            value={month}
            onChange={(e) => setMonth(parseInt(e.target.value))}
            className="px-3 py-1.5 rounded-theme-sm text-sm border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 focus:outline-none focus:ring-1 focus:ring-violet-500"
          >
            {months.map((m) => (
              <option key={m.value} value={m.value}>{m.label}</option>
            ))}
          </select>

          <select
            value={year}
            onChange={(e) => setYear(parseInt(e.target.value))}
            className="px-3 py-1.5 rounded-theme-sm text-sm border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 focus:outline-none focus:ring-1 focus:ring-violet-500"
          >
            {years.map((y) => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => fetchReport(true)}
            disabled={loading || cooldown}
            className="flex items-center gap-2 px-4 py-2 text-sm font-semibold border border-slate-200 dark:border-slate-850 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-theme-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
            <span>{cooldown ? 'Wait 5s...' : 'Force Recalculate'}</span>
          </button>
          
          {report && report.status !== 'NO_DATA' && (
            <button
              onClick={() => window.print()}
              className="flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-violet-500 hover:bg-violet-600 text-white rounded-theme-sm transition-colors shadow-sm shadow-violet-500/10"
            >
              <Printer size={16} />
              <span>Export PDF / Print</span>
            </button>
          )}
        </div>
      </div>

      {/* Loading State */}
      {loading && (
        <div className="flex flex-col items-center justify-center p-12 bg-white dark:bg-slate-850 border border-slate-200 dark:border-slate-800 rounded-theme-md min-h-[400px]">
          <div className="spinner spinner-lg mb-4 text-violet-500" />
          <h3 className="text-base font-bold text-slate-800 dark:text-slate-100">Generating Health Report...</h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Analyzing transactions, cash flows, inventory expiries, and credit aging.</p>
        </div>
      )}

      {/* Error State */}
      {!loading && error && (
        <div className="flex flex-col items-center justify-center p-8 bg-rose-50 dark:bg-rose-950/10 border border-rose-200 dark:border-rose-900 rounded-theme-md min-h-[300px]">
          <AlertCircle className="w-12 h-12 text-rose-500 mb-3" />
          <h3 className="text-base font-bold text-rose-800 dark:text-rose-300">Report Generation Failed</h3>
          <p className="text-xs text-rose-600 dark:text-rose-400 mt-1 max-w-md text-center">{error}</p>
          <button
            onClick={() => fetchReport(true)}
            disabled={loading || cooldown}
            className="mt-4 flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-rose-500 hover:bg-rose-600 text-white rounded-theme-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            <span>{cooldown ? 'Wait 5s...' : 'Retry Insights'}</span>
          </button>
        </div>
      )}

      {/* Empty State - No Data */}
      {!loading && !error && report && report.status === 'NO_DATA' && (
        <div className="flex flex-col items-center justify-center p-12 bg-white dark:bg-slate-850 border border-slate-200 dark:border-slate-800 rounded-theme-md min-h-[400px]">
          <Activity className="w-16 h-16 text-slate-300 dark:text-slate-650 mb-3" />
          <h3 className="text-lg font-bold text-slate-850 dark:text-slate-200">No Business Activity Found</h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 max-w-sm text-center">
            Is month ({months.find(m => m.value === month)?.label} {year}) ke liye koi bills ya transactions available nahi hain. Health report compile karne ke liye at least ek transaction record chahiye.
          </p>
        </div>
      )}

      {/* CFO Insights Report Display */}
      {!loading && !error && report && report.status !== 'NO_DATA' && (
        <div className="space-y-6 print-container">
          
          {/* Print Only Title Section */}
          <div className="hidden print:block border-b pb-4 mb-4">
            <h1 className="text-2xl font-bold text-slate-900">LariTraders Business Health Report</h1>
            <p className="text-xs text-slate-500 mt-1">
              Report Period: {months.find(m => m.value === month)?.label} {year} | Generated At: {new Date().toLocaleDateString()}
            </p>
          </div>

          {/* Overall Score Summary Section */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 bg-white dark:bg-slate-850 border border-slate-200 dark:border-slate-800 rounded-theme-md p-6 shadow-sm relative overflow-hidden">
            {/* Top border decor accent */}
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-violet-500 via-indigo-500 to-emerald-500" />
            
            {/* Circle Progress Widget */}
            <div className="flex flex-col items-center justify-center border-r border-slate-100 dark:border-slate-800/60 pr-0 lg:pr-6 py-4">
              <div className="relative flex items-center justify-center">
                {/* SVG Circular Indicator */}
                <svg className="w-36 h-36 transform -rotate-90">
                  <circle
                    cx="72"
                    cy="72"
                    r="60"
                    stroke="var(--color-border)"
                    className="text-slate-100 dark:text-slate-800"
                    strokeWidth="10"
                    fill="transparent"
                  />
                  <circle
                    cx="72"
                    cy="72"
                    r="60"
                    stroke={getScoreProgressColor(report.overallScore)}
                    strokeWidth="10"
                    fill="transparent"
                    strokeDasharray={376.8}
                    strokeDashoffset={376.8 - (376.8 * report.overallScore) / 100}
                    strokeLinecap="round"
                    className="transition-all duration-1000 ease-out"
                  />
                </svg>
                <div className="absolute flex flex-col items-center">
                  <span className="text-3xl font-extrabold text-slate-850 dark:text-slate-100">
                    {report.overallScore}
                  </span>
                  <span className="text-[10px] text-slate-400 uppercase tracking-widest font-semibold mt-0.5">
                    HEALTH SCORE
                  </span>
                </div>
              </div>
              <div className="mt-4 flex items-center gap-2">
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">Status:</span>
                {getStatusBadge(report.status)}
              </div>
            </div>

            {/* CFO Narrative Summary */}
            <div className="col-span-1 lg:col-span-2 flex flex-col justify-center py-4 pl-0 lg:pl-6 space-y-3">
              <div className="flex items-center gap-2 text-violet-500 font-bold text-sm">
                <CheckCircle2 size={16} />
                <span>Executive Diagnosis</span>
              </div>
              <h2 className="text-lg font-bold text-slate-850 dark:text-slate-100">
                Lari Traders ka Monthly Performance Summary
              </h2>
              <p className="text-xs md:text-sm text-slate-600 dark:text-slate-350 leading-relaxed font-medium bg-slate-50/50 dark:bg-slate-900/40 p-4 border border-slate-100 dark:border-slate-800 rounded-theme-sm">
                {report.healthExplanation}
              </p>
            </div>
          </div>

          {/* Details & Interactive Analysis Layout */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Category Selector Cards */}
            <div className="flex flex-col gap-3 no-print">
              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1 px-1">
                Category Ratings ({Object.keys(report).filter(k => k.endsWith('Details')).length})
              </h3>
              
              {renderCategoryCard('profitability', report.profitabilityDetails)}
              {renderCategoryCard('cashFlow', report.cashFlowDetails)}
              {renderCategoryCard('inventory', report.inventoryDetails)}
              {renderCategoryCard('customer', report.customerDetails)}
              {renderCategoryCard('receivables', report.receivablesDetails)}
              {renderCategoryCard('suppliers', report.suppliersDetails)}
              {renderCategoryCard('operational', report.operationalDetails)}
            </div>

            {/* Selected Category Details Display */}
            <div className="lg:col-span-2 bg-white dark:bg-slate-850 border border-slate-200 dark:border-slate-800 rounded-theme-md p-6 space-y-6 print:w-full print:border-none print:shadow-none">
              
              {/* Tab Selector for Print (renders all details sequentially in print view) */}
              {Object.keys(report)
                .filter((key) => key.endsWith('Details'))
                .map((key) => {
                  const data = report[key]
                  if (!data) return null
                  const isVisible = activeTab === key

                  return (
                    <div
                      key={key}
                      className={`${
                        isVisible ? 'block' : 'hidden print:block'
                      } space-y-6 print:border-b print:pb-6 print:mb-6 print:page-break-inside-avoid`}
                    >
                      {/* Section Title */}
                      <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800/60 pb-3">
                        <div className="flex items-center gap-3">
                          <div className="p-2 bg-violet-500/10 text-violet-500 dark:text-violet-400 rounded-theme-sm">
                            {getCategoryIcon(key)}
                          </div>
                          <div>
                            <h3 className="text-base font-bold text-slate-850 dark:text-slate-100">
                              {getTabLabel(key)}
                            </h3>
                            <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5">
                              Diagnostic breakdown for Lari Traders catalog & transactions.
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className={`px-2.5 py-0.5 text-[10px] font-bold rounded border ${
                            data.rating === 'Good' ? 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300' :
                            data.rating === 'Average' ? 'bg-blue-100 dark:bg-blue-950/40 text-blue-700 dark:text-blue-400 border-blue-300' :
                            data.rating === 'Critical' ? 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300' :
                            'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-400 border-slate-350'
                          }`}>
                            {data.rating || 'N/A'}
                          </span>
                          <span className={`w-7 h-7 rounded-full border flex items-center justify-center font-bold text-xs ${getScoreColor(data.score)}`}>
                            {data.score !== null ? data.score : '-'}
                          </span>
                        </div>
                      </div>

                      {/* Diagnostic Summary */}
                      <div className="space-y-2">
                        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">DIAGNOSIS</h4>
                        <p className="text-xs md:text-sm text-slate-650 dark:text-slate-300 leading-relaxed font-medium bg-slate-50/50 dark:bg-slate-900/40 p-4 border border-slate-100 dark:border-slate-800 rounded-theme-sm">
                          {data.explanation}
                        </p>
                      </div>

                      {/* KPI Table Grid */}
                      {data.kpis && Object.keys(data.kpis).length > 0 && (
                        <div className="space-y-2">
                          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">KEY PERFORMANCE METRICS</h4>
                          <div className="overflow-hidden border border-slate-200 dark:border-slate-800 rounded-theme-sm">
                            <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-800 text-xs">
                              <thead className="bg-slate-50 dark:bg-slate-900/40">
                                <tr>
                                  <th scope="col" className="px-4 py-2 font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-left">Metric Name</th>
                                  <th scope="col" className="px-4 py-2 font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-right">Aggregated Value</th>
                                </tr>
                              </thead>
                              <tbody className="bg-white dark:bg-slate-850 divide-y divide-slate-150 dark:divide-slate-800 font-medium">
                                {Object.entries(data.kpis).map(([metric, val]) => (
                                  <tr key={metric} className="hover:bg-slate-50/30">
                                    <td className="px-4 py-2.5 text-slate-800 dark:text-slate-300">{metric}</td>
                                    <td className="px-4 py-2.5 text-right font-semibold text-slate-900 dark:text-slate-100">{val}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      )}

                      {/* Specific Observations list */}
                      {data.diagnoses && data.diagnoses.length > 0 && (
                        <div className="space-y-2">
                          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">DIAGNOSTIC BULLET INSIGHTS</h4>
                          <ul className="space-y-2.5 pl-0">
                            {data.diagnoses.map((diag, index) => (
                              <li key={index} className="flex gap-2 text-xs md:text-sm text-slate-605 dark:text-slate-350">
                                <span className="text-violet-500 font-extrabold flex-shrink-0">•</span>
                                <span>{diag}</span>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  )
                })}
            </div>
          </div>

          {/* Actionable CFO Checklist Section */}
          {report.actionChecklist && report.actionChecklist.length > 0 && (
            <div className="bg-white dark:bg-slate-850 border border-slate-200 dark:border-slate-800 rounded-theme-md p-6 space-y-4 print:page-break-before-always">
              <div className="flex items-center justify-between border-b border-slate-105 dark:border-slate-800/60 pb-3">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 text-emerald-500" />
                  <h3 className="text-base font-bold text-slate-850 dark:text-slate-100">
                    LariTraders Actionable Corrective Plan
                  </h3>
                </div>
                <span className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">
                  {report.actionChecklist.length} Items to Action
                </span>
              </div>

              {/* Checklist Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {report.actionChecklist.map((item, index) => (
                  <div
                    key={index}
                    className="flex flex-col p-4 border border-slate-200 dark:border-slate-800 rounded-theme-md bg-slate-50/30 dark:bg-slate-900/10 gap-3 hover:border-slate-300 dark:hover:border-slate-700 transition-colors print:page-break-inside-avoid"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex flex-col">
                        <h4 className="text-sm font-bold text-slate-850 dark:text-slate-100 leading-tight">
                          {item.task}
                        </h4>
                        <span className="text-[9px] font-semibold text-violet-500 uppercase tracking-widest mt-1">
                          SCOPE: {item.category}
                        </span>
                      </div>
                      {getUrgencyBadge(item.urgency)}
                    </div>

                    <div className="border-t border-dashed border-slate-200 dark:border-slate-800 pt-2 flex items-start gap-2">
                      <Info className="w-4 h-4 text-slate-400 mt-0.5 flex-shrink-0" />
                      <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed font-medium">
                        {item.instructions}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* CSS Styles for Print/PDF breaks & dynamic elements */}
      <style>{`
        .spinner {
          border: 3px stroke var(--color-border);
          border-top: 3px stroke var(--color-primary);
          border-radius: 50%;
          animation: spin 1s linear infinite;
        }
        .spinner-sm {
          width: 20px;
          height: 20px;
        }
        .spinner-lg {
          width: 44px;
          height: 44px;
        }
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }

        @media print {
          /* Hide sidebar, headers, configurations */
          body {
            background: #fff !important;
            color: #000 !important;
          }
          .app-sidebar,
          .app-header,
          .no-print,
          button,
          select {
            display: none !important;
          }
          .app-content {
            margin-left: 0 !important;
            padding: 0 !important;
            width: 100% !important;
          }
          .print-container {
            margin: 0 !important;
            padding: 0 !important;
            width: 100% !important;
          }
          .print-page-break-inside-avoid {
            page-break-inside: avoid !important;
          }
          table {
            page-break-inside: auto;
          }
          tr {
            page-break-inside: avoid;
            page-break-after: auto;
          }
          thead {
            display: table-header-group;
          }
          /* Ensure all sequential sections are displayed in full print */
          .hidden.print\\:block {
            display: block !important;
          }
        }
      `}</style>
    </div>
  )
}
